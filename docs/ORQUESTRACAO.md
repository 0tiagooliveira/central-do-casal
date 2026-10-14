# Orquestração e revisão estrutural

Verificação realizada em 10/10/2026, no projeto Android nativo Kotlin/Compose/Firebase. Foram executados comandos reais dos pacotes oficiais **oh-my-codex 0.21.8** e **code-review-graph 2.3.9**. Os especialistas deste trabalho são agentes da colaboração nativa do Codex. **Nenhuma equipe de workers OMX/tmux foi iniciada.**

## Isolamento

Runtime, dependências, caches e evidências ficaram em `D:/CodexBuild/central-do-casal-2026-10-10/orchestration`. Nenhuma configuração de `~/.codex`, modelo global, credencial, hook global ou código Kotlin foi alterado por esta revisão. O diretório `.code-review-graph` vazio na raiz é o marcador exigido pelo CRG, pois este projeto não tem `.git` próprio. O banco SQLite e o registro CRG permanecem em D:.

Na primeira tentativa, a opção CRG `build --data-dir` criou automaticamente um registro de projeto em `~/.code-review-graph/registry.json`. A única entrada era a desta execução; o arquivo recém-criado foi movido para a pasta de evidências. As execuções seguintes usam `CRG_HOME` e `CRG_DATA_DIR` isolados. Não foi instalado nenhum hook nem executado `code-review-graph install`.

## OMX realmente utilizado

Instalação local: `npm.cmd install --prefix <runtime>/omx-runtime --ignore-scripts --no-audit --no-fund oh-my-codex`, seguida da instalação igualmente local de `@ast-grep/cli`.

Comandos executados:

- `omx setup --scope project --dry-run --no-mcp --disable-team --verbose`: prévia completa; **setup não aplicado**. O setup padrão gera AGENTS, hooks e configurações de modelos. Aplicá-lo integralmente excederia a configuração limitada solicitada para esta revisão.
- `omx doctor`, `omx doctor --team`, `omx status`, `omx list --json`, e ajuda dos comandos `code-intel`, `team api`, `state`.
- `hydrateNativeBinary('omx-runtime')`, função do pacote oficial, baixou e validou o binário Windows em cache isolado. O lookup automático permaneceu indisponível nesta versão/ambiente; a variável `OMX_RUNTIME_BINARY` apontando esse binário corrigiu a resolução. Doctor final, após criar o estado de revisão: **15 verificações passaram, 6 avisos, 0 falhas**. Os avisos são sobre a ausência deliberada de config, roles, skills, AGENTS e MCP instalados no perfil isolado.
- `omx code-intel ast_grep_search` com linguagem `kotlin` e padrão `FirebaseAuth.getInstance()`: busca AST real encontrou chamadas em `Reminders.kt` e `data/CentralRepository.kt`. A primeira tentativa evidenciou ausência de ast-grep; a instalação local e PATH temporário resolveram.
- `initTeamState(...)`, API exportada pelo pacote OMX, criou **somente um registro de backlog**, com `worker_count=0` e `workers=[]`. Quatro chamadas `omx team api create-task` criaram as frentes de dados/privacidade, Android/recorrências, integração/entrega e QA. `omx team api list-tasks` verificou os quatro registros.
- `omx state write --input-file tools/orchestration/omx-review-state.json --json` persistiu o estado das frentes reais em `omx-state/.omx/state/ultraqa-state.json`. O documento declara `workers="native Codex collaboration"` e `omx_tmux_team_executed=false`.

O backlog de tarefas OMX permanece `pending`: esse é o estado de seu registro sem workers OMX. A API rejeitou uma tentativa de claim por especialista nativo com `worker_not_found`. Não foram criados workers fictícios nem forçadas transições. O estado `ultraqa` registra separadamente a conclusão real dos especialistas, conforme evidência transmitida pelo agente principal: dados/privacidade (74 registros, 50 compartilhados, 24 privados, 10 testes) e Android/recorrências (Domain e Reminders, 8 testes). A QA de orquestração/grafo também foi concluída após verificar a documentação e o wrapper. Integração e entrega permanecem em andamento; por isso o modo de registro continua ativo.

Limitações verificadas: `codex login status` com `CODEX_HOME` isolado retornou **Not logged in**; não foram copiadas credenciais. `omx team --help` declara o runtime tmux e recomenda usar colaboração nativa no Codex App para trabalhos pequenos. Nem `tmux` nem `psmux` estavam disponíveis. `doctor --team` retornar sucesso na ausência de equipe não comprova que workers foram iniciados. As capacidades CLI/MCP locais de AST, memória, estado e tarefas são utilizáveis; o fluxo integral de equipe exige uma sessão CLI autenticada e um mux compatível.

## CRG realmente utilizado

Python Windows Store falhou ao criar venv em D: com erro 6000 de criptografia. Foi instalado Python 3.12.13 por `uv` em diretório isolado D:, e o CRG oficial foi instalado no venv `crg-python312`.

Comandos reais: `build`, `status --json`, `architecture`, `impact --files ... --depth 2`, `large-functions` e um `update` incremental após mudanças recentes em `MainActivity.kt` e `ImportedData.kt`. Nenhum embedding/modelo externo foi usado.

O build inicial informou 16 arquivos, 229 nós e 4052 arestas na contagem de processamento. O `status` do banco após a atualização informou **15 arquivos, 226 nós e 3764 arestas**, linguagens bash/JavaScript/Kotlin, `build_incomplete=false`, `vcs=none`. Essas contagens diferem porque o status descreve o banco persistido após o pós-processamento, e não os itens processados pelo build.

O update reparsou **2 arquivos**, com fallback por hashes de arquivos; houve aviso de falha no git diff, consistente com `vcs=none`. A consulta de arquitetura identificou 8 comunidades usando fallback por arquivo porque `igraph` não está instalado.

Revisão concreta: o impacto de `CentralRepository.kt`/`SyncAccess.kt` encontrou 41 nós diretamente alterados; o de `MainActivity.kt`/`ImportedData.kt` encontrou 47. Ambos retornaram 0 arquivos indiretos. **Isso não prova ausência de dependência**: a leitura do código mostra `CentralRepository(app)` em `MainActivity.kt`. O grafo/resolve de chamadas Kotlin neste ambiente não cobre todas as relações entre arquivos, e as respostas também truncam arestas. Portanto, build Android, testes de domínio/sincronização e revisão humana continuam necessários. O grafo serve como mapa estrutural, sem substituir essas verificações.

## Reproduzir e localizar evidências

`tools/orchestration/review.ps1` configura variáveis somente no processo e executa diagnóstico OMX, leitura do backlog, AST Kotlin, status e impacto CRG. Com `-RefreshGraph`, também executa atualização incremental. As ferramentas precisam estar instaladas no runtime D: descrito acima. O script não inicia workers, não instala hooks e não altera modelos.

Evidências brutas locais: `<runtime>/evidence/omx-setup-dry-run.txt`, `omx-doctor-after-hydration.txt`, `omx-kotlin-ast-search.json`, `omx-backlog-bootstrap.json`, `omx-backlog-list.json`, `omx-review-state-write.json`, `isolated-codex-login-status.txt`, `crg-build.txt`, `crg-update.txt`, `crg-status.json`, `crg-architecture.json`, `crg-impact.json`, `crg-ui-impact.json`, `crg-large-functions.json`. Estas evidências não devem ser publicadas automaticamente: contêm caminhos locais e metadados estruturais.

Fontes consultadas: [OMX oficial](https://github.com/Yeachan-Heo/oh-my-codex), [Code Review Graph oficial](https://github.com/tirth8205/code-review-graph), [variáveis de ambiente Codex, documentação oficial OpenAI](https://learn.chatgpt.com/docs/config-file/environment-variables).

## Revisão da 0.1.3-preview em 11/10/2026

Executados novamente OMX doctor e AST Kotlin no runtime isolado, sem iniciar workers. Doctor: 15 verificações passaram, 6 avisos de configuração deliberadamente ausente, 0 falhas. O estado da entrega não foi finalizado: build e testes finais ainda dependem da confirmação do agente principal.

O update CRG inicial atualizou 7 arquivos, mas a consulta de impacto não encontrou os três arquivos novos ShoppingHub, PriceComparison e AndroidInteractions. A pasta não tem Git e a descoberta incremental não os incluiu. Isso justificou um único rebuild completo: 29 arquivos processados; status persistido final 28 arquivos, 324 nós e 5898 arestas, build_incomplete=false. O status também reconhece PowerShell, pois o wrapper de revisão agora faz parte do projeto.

A consulta de impacto de ShoppingHub.kt, domain/PriceComparison.kt e AndroidInteractions.kt encontrou 21 nós diretos e zero indiretos. Novamente, zero indiretos não garante ausência de dependência Kotlin. Revisão por leitura confirmou os fluxos: Modo Mercado, painel anual de documentos, comparação do mesmo SKU/unidade, revisão de associações com catalogLinks, semana selecionável e interações do Android. Nenhum resultado de testes da 0.1.3 é presumido a partir do grafo.

Evidências isoladas: `<runtime>/evidence/013-omx-doctor.txt`, `013-omx-ast.json`, `013-crg-update.txt`, `013-crg-rebuild.txt`, `013-crg-status.json` e `013-crg-impact.json`. Não publicar esses arquivos brutos. O manifesto de fonte público exclui estado OMX, dumps de grafo, logs, dados pessoais e propostas completas.

Confirmação posterior do agente principal: build final BUILD SUCCESSFUL; 57 testes Android/JVM, 37 de regras e 16 de preparação passaram sem falhas (110 no total); APK e certificado verificados; regras publicadas e leitura posterior correspondente à fonte. VALIDACAO.md registra tamanho e hashes exatos. O estado OMX permanece ativo em `preview_013_delivery_pending`, aguardando confirmação da entrega no Drive; nenhum worker OMX foi iniciado. Evidência de atualização: `013-omx-state-tests-confirmed.json`.

Atualização da entrega: o agente principal confirmou o APK no Drive com tamanho 17.230.717 bytes e a pasta com quatro arquivos (APK, LEIA-ME, VALIDACAO e SHA256). Os metadados appRelease de versão/minVersion 4, hash e URL foram escritos e verificados no servidor. O ZIP de fonte sanitizado foi preparado localmente com 53 arquivos e 221.962 bytes, mas não foi publicado: a revisão automática rejeitou o envio de código-fonte ao Drive por falta de aprovação explícita. A solicitação de aprovação ao usuário está pendente. O estado OMX registra núcleo concluído e fonte aguardando aprovação, sem declarar conclusão integral. Esta etapa não mudou código Kotlin nem criou workers OMX.

Conforme instrução posterior do agente principal, o modo de QA foi encerrado em `qa_and_apk_delivery_complete` (active=false, run_outcome=finish), pois APK e documentos essenciais foram entregues. O envio opcional da fonte permanece separado: `source_status=pending_approval`, `source_uploaded=false`. Não há indicação de fonte publicada ou de equipe OMX bloqueada. Evidências finais: `013-omx-state-core-complete.json` e `013-omx-state-core-complete-read.json`.
