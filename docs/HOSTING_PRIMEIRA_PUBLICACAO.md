# Primeira publicação — Central do Casal Web / Firebase Hosting

Este roteiro é para a liberação inicial do site. **Não executa deploy, não altera dados e não substitui os testes em dois aparelhos.**

## Onde estamos
- Projeto Firebase indicado no código: `central-do-casal`.
- Diretório a publicar: `web/`, configurado em `firebase.json`.
- Aplicação web já possui interface e login por Firebase, mas a paridade com Android é parcial.
- Workflow do GitHub está preparado nesta branch para **execução manual**, na `main`, com confirmação `PUBLICAR`; não publica automaticamente por commits.
- A URL `https://central-do-casal.web.app` é um **objetivo**, não endereço em produção confirmado.

## Passo A — Confirmar o site, sem criar outro por engano

No [Firebase Console](https://console.firebase.google.com/project/central-do-casal/hosting) confira o site padrão e o endereço registrado. Alternativamente, em terminal autenticado:

```bash
npx firebase-tools hosting:sites:list --project central-do-casal
```

Se aparecer o site com ID `central-do-casal`, usar `https://central-do-casal.web.app`. Se o ID for outro, usar o endereço efetivo retornado pela plataforma e verificar qual site é default antes de mudar o código.

**Se não houver site:** provisionar **uma única vez**, conscientemente, pelo Console ou pelo comando abaixo, após verificar disponibilidade do ID:

```bash
npx firebase-tools hosting:sites:create central-do-casal --project central-do-casal
```

Criar o site modifica a configuração do projeto Firebase. Não repetir cegamente em caso de erro. Após 15/10/2026, a criação do site padrão pode ser adiada; o primeiro site criado torna-se o padrão.

## Passo B — Revisar aplicativo Web e autenticação

No Firebase Console → Configurações do projeto → Seus aplicativos:
1. Confirmar que existe um aplicativo Web **deste projeto**; se não existir, registrá-lo.
2. Conferir configuração pública de SDK, especialmente `appId`, `authDomain` e `projectId`. A versão web atual traz `projectId` e `authDomain`, mas **não declara `appId`**; preencher apenas com o valor fornecido pelo Console, nunca inventar.
3. Em Authentication → Settings → Authorized domains, confirmar o domínio real do site.
4. Em Firestore Database → Rules, comparar as regras publicadas com `firestore/firestore.rules`. Testes de Emulator **não são publicação**; não permitir acesso público para “consertar” login.

## Passo C — Habilitar publicação segura no GitHub

O fluxo `.github/workflows/deploy-web-firebase.yml` utiliza Workload Identity Federation em vez de chave JSON:

- Google Cloud IAM: criar/reusar pool e provider OIDC do GitHub, restringindo a identidade ao repositório `0tiagooliveira/central-do-casal` e à branch `main`.
- Conceder à service account autorizada as permissões mínimas de Firebase Hosting.
- GitHub → Settings → Secrets and variables → Actions: cadastrar `GCP_WORKLOAD_IDENTITY_PROVIDER` e `GCP_SERVICE_ACCOUNT` **diretamente na interface**.
- Verificar a identidade da conta e permissões antes de tentar publicar.
- Não copiar valores dos secrets, tokens, service accounts JSON ou credenciais para chats, commits ou arquivos.

A documentação do projeto relata uma falha anterior porque `workload_identity_provider` não foi disponibilizado à execução do workflow. O estado atual desses secrets **não foi consultado**.

## Passo D — Validação antes de abrir ao público

No PR de desenvolvimento, conferir `./gradlew :app:testDebugUnitTest`, `npm run test:hosting`, `npm run test:rules` e APK debug. O teste estático de Hosting não prova provisionamento do site, domínio ativo, autenticação real ou sincronização Android/Web.

Com contas e dados de teste em ambiente apropriado:
- Login/logout; abertura direta de rotas `#/hoje`, `#/compras/mercado`, `#/rotina`, `#/planos`, `#/nos`.
- Mercado e Açougue com compras e orçamento separados.
- Escrita Android → Web e Web → Android, após confirmação do Firestore, com dois logins corretos.
- Concorrência: edição simultânea com revisão desatualizada, sem sobrescrever alterações.
- Quota/sem conexão: “não sincronizado” visível, backup exportável e nenhum recibo duplicado.
- Autoria de eventos de Lucca/Mel, separação de saúde pessoal e privacidade entre households.

**Nunca usar dados familiares reais em previews conectados ao Firestore de produção.** Prévia Hosting não cria um banco isolado.

## Passo E — Publicação somente após aprovação de release

Depois de revisar e mesclar o PR na `main`, vá a GitHub → Actions → **Publicar Central do Casal Web no Firebase Hosting** → Run workflow. Selecione `main`, informe `PUBLICAR` e inicie.

O fluxo deve validar configuração, listar os sites e executar `firebase-tools deploy --only hosting --project central-do-casal`. Confirmar nos logs o URL **real**, abrir em navegador anônimo e testar autenticação. Caso falhe, não tentar publicar usando credenciais privadas no repositório.

## Passo F — Liberação e preservação

- Não apagar backups de carrinho, dados locais ou app Android antes de conferir ambos os aparelhos.
- Manter endereço legado acessível apenas pelo período necessário à migração, evitando deploys extras.
- Registrar data, revisão, URL final, resultado dos smoke tests e problemas pendentes.
- O site web não substitui notificações, localização, SOS ou demais funções nativas não implementadas.

## Fontes
- https://firebase.google.com/docs/hosting/default-site
- https://firebase.google.com/docs/hosting/quickstart
- https://firebase.google.com/docs/hosting/multisites
