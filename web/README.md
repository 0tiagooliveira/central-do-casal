# Central do Casal Web · Mercado, catálogo e modo de compras

### Escolher quantidade antes de adicionar

Em cada card de produto e de apresentação física (frasco, caixa, unidade), aparece **Quantidade a comprar**, com botões `−`/`+` e campo numérico para digitar valores como `1`, `2` ou `1,5`. O valor escolhido é usado diretamente em `plannedMilli` e `actualMilli` na inclusão transacional no Firebase. O controle oferece passos de 1 unidade/pacote, 0,5 kg/L e 100 g/mL, sem impedir entrada manual exata. As quantidades digitadas são preservadas entre atualizações de tela enquanto a sessão permanece aberta. O valor representa a quantidade da apresentação escolhida: `2` no Amaciante 5 L significa **dois frascos de 5 L**, não 2 L. Para itens com unidade fiscal não identificada, a lista mantém a sinalização de revisão.

## Contingência quando Firestore retornar quota excedida

O Firebase pode recusar gravações com `resource-exhausted` / `Quota exceeded` quando o banco ultrapassa limites de uso. **Não** reiterar automaticamente transações, pois isso pode consumir ainda mais cota.

Na tela **Compras**, a primeira tentativa de adicionar ao carrinho, editar um item, ajustar quantidade ou incluir produto que encontrar erro de quota registra a alteração **apenas no localStorage do navegador**, em um diário isolado por UID, household e Mercado/Açougue. A interface utiliza uma projeção dessa lista, destacada por um aviso âmbar **NÃO sincronizada**; eventos seguintes do mesmo mercado permanecem locais, evitando novas chamadas que falharão. O usuário pode **Baixar backup JSON** durante a compra. Se o navegador não permitir escrita local, o app mostra um erro explícito e não afirma ter salvado.

**Tentar sincronizar** é uma ação manual. No retorno da capacidade de gravação, uma transação lê a cesta atual, verifica que continua na mesma geração de compra e recusa sobrescrever produtos alterados por outro aparelho. Só então reaplica as operações e descarta o diário local, após commit confirmado. Se houver conflito ou quota, o backup permanece intacto. **Finalizar compra** é bloqueado enquanto existir diário local pendente, evitando falsa confirmação fiscal e perda de itens. A conta e o household podem ser restaurados de identificação local já autenticada em caso de falha de leitura por quota após refresh, mas isso não concede acesso remoto sem permissões do Firebase.

**Limitações:** se o navegador apagar armazenamento do site antes de exportar, as ações locais podem desaparecer. O backup pode ser baixado manualmente, e não representa sincronização com outros aparelhos. Não habilitamos cobrança, nem alteramos os limites do Firebase. O responsável deve verificar **Firebase Console → Firestore Database → Usage** para identificar se o limite foi em leituras, gravações, dados armazenados ou outra operação. O diário não arquiva notas fiscais enquanto a nuvem está indisponível.

## Conferência fiscal no fim da compra

Em **Compras > No mercado > Conferir nota e finalizar** ou **Minha lista > Conferir nota e finalizar**, anexe o comprovante antes de salvar o histórico. O fluxo é opcional: **Salvar sem nota** registra a compra identificando explicitamente que a conferência fiscal não foi realizada.

**XML NFC-e/NF-e**: o navegador lê os campos `infNFe/ide`, `emit`, `total/ICMSTot/vNF` e `det/prod` (nome, código, quantidade, unidade, preço e desconto) do XML recebido, sem enviar o arquivo a serviços externos. Confere a quantidade e o valor efetivo das linhas da nota com os produtos marcados no carrinho. Faz sugestão de vínculo somente quando **nome e unidade são exatamente iguais** e a correspondência é única, podendo o usuário revisar e escolher manualmente outra linha. Exibe divergências individuais, itens não conciliados, diferença do total e loja. A autenticidade fiscal não é consultada na SEFAZ e, portanto, a etiqueta **Preços conferidos com o XML** não significa que o documento foi autenticado.

**Foto/PDF**: anexa o comprovante reduzido diretamente ao registro do histórico no Firestore, com limite conservador para respeitar 1 MiB por documento. Esses arquivos **não passam por OCR**: o usuário pode informar o total impresso e, se quiser, digitar as linhas no formato `Descrição; quantidade; unidade; preço unitário`. Valores digitados são confrontados, mas não apresentados como conferência documental automática. Arquivos grandes exigem XML ou uma foto menor. O comprovante fica acessível apenas ao casal com permissão de leitura do histórico no Firebase.

Ao salvar, o código cria o registro `receipt` e avança a cesta na **mesma transação Firestore**. O histórico guarda XML original ou comprovante comprimido, valor da nota, linhas extraídas, associações revisadas, diferenças, status e dados da compra, com opção de baixar o arquivo. Se houver diferença, ela é preservada como pendência; preços anotados no mercado nunca são corrigidos silenciosamente para igualar à nota. A compra antiga fica imutável pelas regras atuais. Estes recursos não requerem uma nova configuração de Firebase Storage, mas anexos grandes não são suportados nessa abordagem.

## Incluir compras imprevistas sem passar por Falta comprar

Em **Compras > No mercado**, o botão **+ Produto direto no carrinho** abre um formulário curto. Pesquise um item já cadastrado em Minha base/Por loja ou digite um produto novo, informe a quantidade, unidade e preço real pago. O formulário permite ajustar a quantidade com botões de mais/menos e preencher a categoria.

Ao confirmar, a inclusão usa `addToCart` em uma **transação Firestore**. O item entra com `bought=true`, preço real e quantidade efetivamente comprada, e a interface navega para **No carrinho**. Se já existe **exatamente um** produto pendente de mesmo nome e unidade, a transação o atualiza em vez de criar uma cópia. Em caso de nomes repetidos ou embalagens diferentes, os registros ficam separados para não mesclar indevidamente. O total e contadores são atualizados a partir da mesma cesta sincronizada. Nada disso altera o histórico anterior nem o catálogo de origem.

## Recarregamento robusto e modo mercado mais rápido

O site agora conserva **a tela/aba na URL, o mercado selecionado e o card que estava visível**, não apenas o deslocamento em pixels. A posição é salva durante a rolagem, quando a aba fica em segundo plano e ao fechar/recarregar, usando apenas sessionStorage da aba. O texto pesquisado em Catálogo, Minha lista e No mercado também é recuperado na mesma aba. O carregamento do modo mercado pode restaurar a posição assim que os documentos da cesta chegam, sem esperar todos os registros do catálogo. O navegador não disputa a restauração com o app. Links sem fragmento tentam recuperar a última rota aberta na mesma sessão.

As mudanças de cache/metadata do Firebase não causam reconstruções de interface; os snapshots de dados são renderizados uma vez por frame, preservando a rolagem quando a seção não muda. O app assina as cestas **antes** dos catálogos grandes. A inicialização usa a persistência de login padrão local do Firebase sem chamar `setPersistence` em todo refresh; escolha explícita e fallback de persistência só ocorrem no **login**. Isto evita migrações desnecessárias do estado de autenticação a cada atualização, sem guardar senhas.

No **No mercado**, cálculos de preços e histórico exclusivos da página **Minha lista** não são mais executados. As referências de preços restantes usam índice e cache por snapshot, evitando varrer centenas de produtos repetidamente. O Chrome pode deixar os cards fora da tela sem pintura/layout até serem necessários. A barra de pesquisa aparece primeiro e o resumo ocupa menos espaço no celular. Nada disso modifica produtos ou notas do Firestore.

Estas melhorias foram validadas por testes sintáticos e simulados, não por teste de uma sessão Firebase autenticada no navegador do usuário. A rede/armazenamento do dispositivo ainda podem causar atrasos ou impedir persistência de login.

## Recarregar sem perder tela e sessão

A web usa rotas no fragmento da URL (`#/`) para registrar a posição atual. Exemplos: `#/compras/mercado/catalogo/minha-base`, `#/compras/mercado/minha-lista`, `#/compras/acougue/no-mercado/carrinho`, `#/hoje`, `#/rotina`. Ao atualizar ou abrir um link guardado, a navegação principal, Mercado/Açougue e subaba de Compras são restaurados. O histórico do navegador (Voltar/Avançar) também troca de tela sem carregar outro documento. A posição vertical é salva apenas na sessão do navegador e recuperada após o primeiro carregamento dos registros compartilhados.

O formulário de login permanece oculto enquanto o Firebase verifica o usuário. A autenticação usa **browserLocalPersistence** para manter a sessão após recarregamento e fechamento, com fallback para **browserSessionPersistence** quando o armazenamento local é bloqueado pelo navegador. Se nenhum armazenamento estiver disponível, o site informa a limitação e pode exigir autenticação novamente; nenhuma senha é salva manualmente na página. **Sair da conta** continua a encerrar a sessão. A rota é estado de interface, não conteúdo privado.

## Pesquisa em Minha lista e No mercado

Em **Compras > Minha lista**, a barra de busca fica logo abaixo da escolha Mercado/Açougue e filtra simultaneamente pendentes e itens comprados, por nome do produto, grupo, categoria, subgrupo, apresentação, unidade ou cômodo. Em **Compras > No mercado**, outra barra filtra as abas **Falta comprar** e **No carrinho**, com resultados e contadores próprios em cada situação. O catálogo já dispõe de pesquisa por nome, categoria e loja.

A pesquisa ignora maiúsculas/minúsculas e acentos, aceita múltiplas palavras em ordem livre (por exemplo `acucar`, `amaciante 5 l` e `hamburguer caixa`), exibe quantidade de resultados e oferece **Limpar**. A consulta é local no conteúdo já sincronizado: não exige novas leituras nem altera dados do Firestore. Os textos digitados são mantidos por mercado e por aba durante as atualizações do Firebase; ao mover um item para o carrinho, ele desaparece dos pendentes e fica encontrável no carrinho. É possível mudar de aba sem perder o termo pesquisado.

## No mercado e estimativas recuperadas

Em **Compras > No mercado**, a navegação separa **Falta comprar** de **No carrinho**, com contadores e progresso. Ao informar o preço real e tocar em **Colocar no carrinho**, o item recebe `bought=true` e desaparece da lista de pendentes, mas permanece na lista do carrinho. **Retirar do carrinho** reverte a marcação, sem apagar o item, preservando a quantidade planejada.

As inclusões rápidas da base não descartam mais um preço de referência existente. O preço entra como estimativa, mas uma unidade/embalagem desconhecida continua marcada para revisão antes de compor a estimativa conferida do orçamento.

Para listas existentes, **Minha lista** identifica preços ausentes e mostra referências sugeridas por ID do item-base, ou por SKU e apresentação fiscal identificável. O botão **Aplicar referências** grava somente sugestões com origem segura em uma transação Firebase, sem alterar preço real, itens comprados, preços já cadastrados nem quantidade. Nomes homônimos sem ID só recebem sugestão visual e exigem confirmação manual. Apresentações diferentes do mesmo SKU nunca recebem o último preço de outra apresentação. O subtotal distingue estimativas conferidas de referências com unidade ainda a revisar. Referências históricas não devem ser interpretadas como cotações atuais.

## Preços por embalagem, volume, peso e quantidade de peças

O catálogo agora interpreta apresentações **somente quando a informação consta na descrição fiscal, no nome do SKU ou na unidade da nota**. Agrupa cada série por **SKU + apresentação + unidade fiscal**, tanto dentro de um mesmo SKU quanto entre as variantes de uma família. Por exemplo: amaciante de 1 L, 1,5 L e 5 L, hambúrguer avulso, caixa com quantidade de peças documentada e multipacks "6x1 L".

Cada apresentação mostra o valor unitário na nota e, quando seguro, o preço equivalente por **litro, quilo ou peça**. Oferece gráficos separados, menor, média e maior preço, comparação com a compra anterior, loja, data, quantidade registrada, descrição original e valor estimado da linha após rateio (quando existente). A comparação por conteúdo declarado apresenta valores recentes de diferentes embalagens em tabela separada, sem unir curvas de apresentações distintas.

Quando uma nota contém apenas "UN" ou "CX" e não informa tamanho nem número de peças, **a informação não é inventada**. A variação de preço dessas linhas é marcada como inconclusiva, evitando conclusões como "subiu 69%" ao misturar frascos diferentes. Uma caixa sem quantidade explícita não recebe preço por peça. Uma descrição "caixa 12 un, 90 g" não vira "1,08 kg" automaticamente; somente multiplicadores explícitos como "12x90g" permitem esse cálculo. Descrições fiscais diferentes dentro de uma mesma apresentação recebem aviso de revisão.

Nos detalhes da apresentação há um botão **+ Adicionar esta apresentação à lista**. Essa ação usa uma transação Firestore e preserva o vínculo com o SKU original, a unidade fiscal e o preço histórico mais recente como **estimativa**, não como preço atual. O nome da entrada diferencia frasco de 5 L, 1,5 L, caixa e avulso. Os dados originais das notas e as associações confirmadas não são alterados.

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
