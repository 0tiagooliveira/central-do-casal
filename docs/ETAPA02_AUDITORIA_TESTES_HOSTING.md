# Etapa 02 — Auditoria, testes e Firebase Hosting

Data de referência: 05/11/2026. Base auditada: `main`. Este documento descreve o código consultado, não declara build ou deploy concluído.

## 1. Inventário comprovado
- Android nativo: um módulo Gradle `app`, Kotlin 2.2.20, AGP 8.13.0, Compose Material 3. O workflow usa **JDK 21**, porém o bytecode ainda tem alvo Java/Kotlin 17. Esses requisitos não são equivalentes: manter o alvo 17 durante a auditoria evita uma migração de compatibilidade sem testes.
- Estado: `CentralViewModel` ainda está em `MainActivity.kt`; `CentralRepository` concentra sessão, Firestore, persistência local, compras e outras operações. `DataState` agrega registros, cestas e sincronização.
- Testes já existentes: JUnit 4 + Robolectric + Compose UI Test em `app/src/test`; Emulator de regras Firestore via `npm run test:rules`. O manifesto sugere JUnit 5 + MockK, mas uma migração segura exige preservar os testes JUnit 4/Robolectric/Compose (ou adicionar Vintage) e introduzir interfaces injetáveis, antes de mockar dependências de Firebase.
- `LocalRepositoryTest` já cobre fechamento repetido, revisão obsoleta, cuidado persistido e vínculos de planos. `CareEventsTest` cobre seleção de dia e ordenação. Regras Firestore já verificam autoria e exclusão de cuidado apenas pelo autor.
- UI web existente: `web/index.html` + `web/main.js` e módulos de compras. A web possui identidade e cinco destinos, mas não tem paridade com funções nativas (ex.: notificações Android, SOS e localização).

## 2. Contratos e riscos antes de refatorar
- Separar gradualmente `ScreenState` (dados, sync e loading, erro com origem, dados do autor) e `ScreenEvent` (ações humanas explícitas) por feature, sem substituir todos os usos de `DataState` num único commit.
- Preservar isolamento entre conta pessoal e household, autorização por usuário, Mercado/Açougue independentes, revisão de cestas, geração idempotente de recibos, origem das etapas de Planos, metadados de autoria de cuidados e histórico imutável.
- Priorizar o caso difícil: timeout ou falha após commit remoto, retentativa do mesmo fechamento, concorrência entre aparelhos, quota excedida e atualização de UI originada do cache. Não prometer sincronização enquanto houver escrita pendente.
- `CentralViewModel.action` retorna sem executar quando `busy` já está ativo; testes futuros devem assegurar resposta apropriada sem perder interações importantes.
- A regra de 30 linhas é para *apresentação de patches*, não para inserir `...` inválido em arquivos Kotlin reais.
- O repositório encontrado está público. Nunca anexar exports reais, documentos pessoais, notas, segredos, keystores ou `service-account*.json`.

## 3. Preparação e gate de testes
1. Acrescentar regressões Robolectric no modo local: idempotência do fechamento após reiniciar o repositório; orçamento isolado por mercado; metadados de Lucca/Mel sobrevivendo à persistência.
2. Acrescentar verificação estática do Firebase Hosting usando Node, sem exigir acesso à produção.
3. Em fases seguintes, adicionar contratos de repositório injetáveis e testar `StateFlow` do ViewModel com fakes/MockK; manter os testes Compose existentes e ampliar tags semânticas estáveis para linhas de compras e cuidados; testar concorrência e autoria com o Firestore Emulator.
4. Gate real da branch: `./gradlew :app:testDebugUnitTest`, `npm run test:hosting`, `npm run test:rules`, `./gradlew :app:assembleDebug`. Verificação estática não substitui navegador ou dois aparelhos reais.
5. Não editar dados, regras ou permissões de produção durante este pacote. Publicação apenas após gates e autorização separada.

## 4. Frente Firebase Hosting (nova prioridade do plano)

**Estado do código:** `.firebaserc` aponta para `central-do-casal`; `firebase.json` aponta para `web/`, inclui headers básicos; já existe `.github/workflows/deploy-web-firebase.yml` usando Workload Identity Federation e `firebase-tools deploy --only hosting`. O arquivo `web/README.md` relata execução anterior bloqueada por autenticação WIF ausente. Não há comprovação nesta auditoria de que o secret continue ausente ou de que o Hosting já esteja provisionado.

**Endereço pretendido:** `https://central-do-casal.web.app`, com `https://central-do-casal.firebaseapp.com` como domínio associado **somente se** o site padrão possuir o ID `central-do-casal`. Desde 15/10/2026, um projeto Firebase novo pode não ter site Hosting padrão provisionado automaticamente. Confirmar o `siteId` real antes de divulgar qualquer URL.

### Sequência de implantação segura

1. No projeto Firebase de ID `central-do-casal`, verificar na seção Hosting ou via `firebase hosting:sites:list --project central-do-casal` se existe site padrão e qual URL oficial retorna. Se não houver, provisionar o site com ID `central-do-casal` **se estiver disponível**; caso não esteja, registrar o ID alternativo e revisar links/Configuração.
2. Confirmar registro do aplicativo Web no mesmo projeto e copiar os valores **públicos, reais** de SDK (`appId`, `authDomain`, `projectId`, etc.) para configuração versionável; nunca inventar um `appId`. Uma Firebase Web API key de cliente não equivale a uma service account, mas não autoriza tornar regras públicas.
3. Conferir domínios autorizados do Firebase Auth e regras Firestore efetivamente publicadas no projeto correto, sem expor coleções pessoais nem abrir leituras anônimas.
4. Revisar `web/`: login/logout, seleção do household, navegação por fragmento, Mercado e Açougue bidirecionais, histórico, estados offline/pendente/quota, backup e conciliação; preservar as limitações já documentadas de funcionalidades web versus Android.
5. Configurar GitHub OIDC/WIF: pool/provider restritos ao repositório e branch autorizada; service account com permissão de Hosting; secrets `GCP_WORKLOAD_IDENTITY_PROVIDER` e `GCP_SERVICE_ACCOUNT` configurados diretamente no GitHub. Não usar JSON de conta de serviço nem enviar valores ao chat.
6. Validar testes e prévia antes da publicação live; por padrão, usar dados fictícios e usuários de teste, lembrando que um preview Hosting conectado ao Firestore de produção ainda pode modificar dados reais.
7. Publicar *somente Hosting*, com aprovação de release, registrar URL real e validar HTTPS, login, privacidade, sincronização cruzada Android/Web, edição concorrente, modo offline, restore após refresh e navegação em celular.
8. O workflow foi preparado nesta branch para publicação **exclusivamente manual**, com entrada `PUBLICAR` e execução permitida somente pela `main`. O GitHub não publicará uma versão web apenas porque o PR foi mesclado. Configurar proteção de branch e revisão de produção conforme as permissões disponíveis.
9. Manter eventual domínio legado em modo somente leitura ou redirecionamento controlado até validar a nova URL; sem novos deploys na Netlify. Não desinstalar o APK nem apagar backups locais.

### Critérios para marcar Hosting como pronto
- Site padrão e URL confirmados no console/CLI, não inferidos do nome.
- Login Firebase de duas contas e visibilidade correta do mesmo household.
- Compras editadas em Web/Android sem duplicação; nenhuma alteração local é exibida como remotamente confirmada antes de commit.
- Regras, autenticação, configuração pública e políticas de retenção revisadas.
- CI verde, preview validado, release aprovada e deploy live efetivamente confirmado.

## 5. Papéis lógicos
- **Orchestrator:** ordem dos gates, compatibilidade, segurança e decisão de release.
- **Test Enforcer:** testes Robolectric/Compose, Emulator e regressões estáticas do Hosting.
- **Light Dev:** patches pequenos e isolados após testes, sem substituir arquivos grandes por placeholders.
- **UI/UX:** geometria/cores por perfil, acessibilidade e semântica Compose. Sem mudança visual durante esta fase.

## 6. Evidência da CI e limites
- GitHub Actions na execução [#318](https://github.com/0tiagooliveira/central-do-casal/actions/runs/42129402671) concluiu **sucesso** em Android/JVM, `test:hosting`, Firestore Rules e APK DEV para o commit `470857e`. Não equivale a QA em produção.
- O workflow de publicação manual foi modificado **depois** dessa execução. Revalidar a revisão atual antes de considerar o PR pronto.
- O uso do domínio `central-do-casal.web.app` depende da existência/ID real do site. A documentação oficial de Firebase (novembro de 2026) informa que projetos criados após 15/10/2026 podem ter criação do site padrão adiada. Se o projeto já tinha site padrão, não recriar nem tentar trocar esse ID.
- Verificar por `firebase hosting:sites:list --project central-do-casal`; se não houver site, aprovar explicitamente o provisionamento no Console ou por `firebase hosting:sites:create central-do-casal --project central-do-casal`, caso o ID esteja disponível. Criação de site é mudança de produção e não faz parte deste PR.
