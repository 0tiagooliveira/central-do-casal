# Central do Casal Web · Mercado, catálogo e modo de compras

## Famílias de produtos e adição rápida

O catálogo web mostra grupos por família na ficha de cada item-base. Exemplo: **Açúcar** apresenta históricos de açúcar cristal, mascavo e refinado na mesma ficha, com **gráficos separados por SKU/variação**, para não comparar uma embalagem de 1 kg com outra de 5 kg como se fossem o mesmo item. O gráfico principal destaca uma série individual com registros reais. A mesma lógica de família por nome específico e evidências importadas é aplicada a outros produtos da base quando identificáveis.

As famílias de nome ou sugestão são **pré-visualizações**, não associações oficiais gravadas no Firebase; uma decisão explícita de separar ou associar a outra base sempre prevalece. Quando não há evidência segura, o gráfico não inventa histórico. Os vínculos exatos continuam confirmáveis manualmente. As séries originais, notas e IDs não são alterados.

**+ Adicionar** no Catálogo agora insere o item na lista **com um toque**, sem abrir um formulário. Se a unidade ou preço não têm suporte na base, o item entra com quantidade inicial de 1 e sinalização de revisão; o campo de preço não é preenchido com dados de embalagens diferentes. Depois use **Minha lista → +/− ou Editar** para ajustar e **No mercado** para registrar o preço real. A transação Firestore preserva os campos `unitReviewed`, `quantityReviewed`, `estimateKnown` e os IDs de origem, garantindo sincronização conservadora com Android.

## Preparar a lista usando a base de produtos

Na web, abra **Compras** e escolha uma das três áreas:

- **Catálogo**: veja **Minha base** (`catalogItem`) ou **Por loja** (`catalogSku`) conforme os registros compartilhados no Firestore. Busque nome, marca, categoria ou loja, filtre por categoria, avance em grupos de 40 produtos e toque em **Adicionar** para inserir na lista diretamente; ajuste unidade, quantidade e preço na lista caso estejam incompletos.
- **Minha lista**: planeje e ajuste quantidades pelos controles `+` e `−`, ou abra **Editar** para especificar valores exatos.
- **No mercado**: veja **Falta comprar** e **No carrinho** separadamente. Ajuste quantidade, preencha o preço real do produto e toque em **Colocar no carrinho**. Corrija preços com **Salvar preço** ou devolva itens à lista com **Retirar do carrinho**. Ao terminar, use **Concluir e salvar**.

Os minigráficos de cada card do catálogo são calculados a partir do histórico importado no `catalogSku.history` e dos recibos finalizados no aplicativo, associados à base por IDs e decisões explícitas (`catalogLinks`). Só se comparam preços por mesma unidade; preços inválidos ou sem associação confirmada são omitidos, não estimados. Se houver uma única observação comparável, o card informa que ainda não há tendência suficiente.

Os botões de quantidade variam `1 un/pacote`, `0,5 kg/L` ou `100 g/mL`; o editor permite qualquer quantidade positiva dentro dos limites. Quantidade planejada e efetivamente comprada são preservadas separadamente no Firestore. Cartas antigas que só existem localmente no Android não são recuperadas sem migração.

## Funcionalidades prontas na branch
A versão web disponibiliza login Firebase e as telas Hoje, Compras, Rotina, Planos e Nós. **Mercado e Açougue são totalmente editáveis**: criar/editar/excluir produtos, marcar comprado e registrar preço real, orçamento, duas listas, histórico e reposição de produtos antigos. As demais telas ainda estão em expansão e não devem ser tratadas como paridade total com o Android.

As operações nas listas usam a mesma coleção `households/{id}/baskets` e o mesmo esquema de dados do APK. Todas as mutações ocorrem em transações do Firestore com revisão esperada; caso outro dispositivo atualize antes, a gravação é recusada e o usuário revisa. O fechamento da compra gera um recibo de ID `generation` e cria nova revisão com os preços reais, preservando o histórico. Itens com mesmo título e unidades diferentes não são mesclados.

**Importante:** a web só sincroniza dados que já existem no Firestore. Registros exclusivos do armazenamento local do APK antigo precisam ser exportados/migrados com revisão humana. Não limpe ou desinstale o APK.

## Publicar no Firebase Hosting
Há um workflow `.github/workflows/deploy-web-firebase.yml` que tenta publicar automaticamente ao atualizar a branch `main`. Ele usa os secrets `GCP_WORKLOAD_IDENTITY_PROVIDER` e `GCP_SERVICE_ACCOUNT` existentes no repositório para autenticação Workload Identity Federation. A conta de serviço deve possuir também permissões para **Firebase Hosting** (não apenas Firestore Rules Admin).

Se faltar permissão, verifique a execução em GitHub → Actions → **Publicar Central do Casal Web no Firebase Hosting**. Para publicar manualmente:

```bash
npm ci
npx firebase-tools login
npx firebase-tools deploy --only hosting --project central-do-casal
```

A URL será mostrada na saída do deploy. No console Firebase, registre um aplicativo Web no projeto `central-do-casal`, confira `web/main.js` (configuração Firebase pública) e confirme que o login e os domínios autorizados funcionam. Não exponha credenciais privadas em JavaScript ou no GitHub.

Uma segunda opção é o workflow GitHub Pages `.github/workflows/web-pages.yml`. Se o repositório privado não tiver Pages habilitado pelo plano, use Firebase Hosting.

## Teste em dois dispositivos
1. Entre na web com a mesma conta Android. Se o Android usa modo local, conecte/exporte esses dados antes.
2. Confirme espaço `householdId` e listas.
3. Adicione um produto na web. Confira se aparece no Android, e vice-versa.
4. Marque um produto e altere orçamento. Verifique em ambos.
5. Finalize uma compra real apenas após todos os preços reais terem sido conferidos. Confira histórico no Android.
6. Verifique que duplicações não foram unificadas.
7. Se uma operação exibir erro de permissão, não altere regras para públicas; confirme usuário, espaço compartilhado, versão das regras e deploy.

Outras funções nativas como lembretes de Android, localização e SOS não são portadas para browser nesta entrega. A migração do armazenamento local continua necessária para compatibilidade total.
