# Instruções para agentes: Central do Casal

A Central do Casal é um aplicativo Android nativo (Kotlin, Jetpack Compose, Firebase), não React, Expo ou uma landing page. Preserve os fluxos existentes. Antes de alterar uma tela, leia app/src/main/java/com/centraldocasal/app/Design.kt, Experience.kt, a tela afetada e docs/ESTADO_ATUAL.md.

## Skills do projeto
As skills em .agents/skills/ são instruções operacionais locais, adaptadas e escritas para este projeto; consulte os repositórios oficiais citados em cada SKILL.md para metodologias completas.

- central-ux: quando criar ou redesenhar telas, componentes, paleta, tipografia, hierarquia e fluxos; incorpora a abordagem de Dembrandt e Jakub Krehel.
- central-motion: quando implementar ou revisar movimento, transições e resposta háptica; aplica critérios de Emil Kowalski e LottieFiles.
- central-interface-review: em qualquer PR que altere interface, revisar legibilidade, alinhamento, acessibilidade, estado vazio, erro, loading e privacidade.
- central-animation-runtime: ao escolher Compose, Lottie ou Rive e aprovar recursos animados e suas licenças.

## Regras vinculantes do produto
- Tiago usa acento azul e Rhayra rosa; manter cores sem sacrificar contraste. Não inferir autorização de dados pelo nome ou cor do perfil.
- Alvos: Samsung S23 FE e S25 FE, telas pequenas, fonte ampliada, TalkBack e navegação por toque.
- Movimento é opcional pelo usuário; respeitar a escala de animação do Android. Feedback de áudio e vibração só em ações explícitas, obedecendo às preferências.
- Não colocar animação, Lottie ou Rive como dependência do app sem uso funcional, licença conferida e teste de desempenho.
- Não criar dados falsos, compromissos inventados ou componentes inertes que pareçam conectados. Estados de erro, offline e sem permissão devem ser explícitos.
- Respeitar o escopo de dados pessoais e do household, não gravar segredos, credenciais ou localização precisa em código/prints/logs.
- Alterações devem entrar por branch e PR; CI e revisão são necessários. Não declarar APK validado nem testes físicos sem evidências.

## Fluxo recomendado em cada PR de UI
1. Ler a skill de UX, descrever a tarefa e localizar os componentes e estados reais.
2. Ler a skill de motion quando houver movimento; preferir Compose para transições simples.
3. Implementar mudanças mínimas e reversíveis com semântica acessível, sem mexer no comportamento dos dados.
4. Executar testes Android/JVM e regras Firebase quando afetadas; declarar claramente testes não realizados.
5. Aplicar central-interface-review e registrar evidências de S23 FE e S25 FE.
6. Documentar decisões em docs/UX_MOTION_SKILLS.md quando afetarem padrões de projeto.
