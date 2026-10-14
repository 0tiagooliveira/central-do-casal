# Ferramentas enviadas pelo usuário

Repositórios consultados em 10/10/2026:
- https://github.com/Yeachan-Heo/oh-my-codex
- https://github.com/tirth8205/code-review-graph

OMX 0.21.8 foi instalado e executado em ambiente isolado. Upstream recomenda macOS/Linux e indica menor suporte a Windows nativo/Codex App. Nesta revisão foram usados diagnóstico, AST Kotlin e registro de estado/backlog. Não houve workers OMX/tmux nem hooks/configurações globais. Os especialistas utilizaram colaboração nativa Codex.

Code Review Graph 2.3.9 construiu e consultou o grafo real do código. Para a 0.1.3, o índice foi reconstruído após constatar que atualização incremental sem Git não descobriu os novos arquivos. O banco conferido possui 28 arquivos, 324 nós e 5898 arestas. Impacto Kotlin entre arquivos permanece incompleto; esses resultados não certificam funcionalidade Android ou aprovação de testes.

Instalação dessas ferramentas não é pré-requisito técnico para compilar/testar o APK. Não modificamos o código ou banco do Poup+; o JDK/SDK já existente foi usado apenas como ferramenta de leitura/compilação.
