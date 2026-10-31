# Central do Casal Web · Mercado sincronizado

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
