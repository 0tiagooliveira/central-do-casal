# Instruções para agentes: Central do Casal

A Central do Casal é um aplicativo Android nativo (Kotlin, Jetpack Compose, Firebase), não React, Expo ou uma landing page. Preserve os fluxos existentes. Antes de alterar uma tela, leia app/src/main/java/com/centraldocasal/app/Design.kt, Experience.kt, a tela afetada e docs/ESTADO_ATUAL.md.

## Skills originais (fontes primárias, copiadas sob MIT)

Em `.agents/skills/` estão os SKILL.md **originais** de Emil Kowalski
(`animate`, `review-animations`, `improve-animations`), Dembrandt
(`dembrandt`, `ui-density`, `visual-emphasis-and-hierarchy`,
`notifications-and-recovery`, `wcag-accessibility`), Jakub Krehel
(`interface-review`, `better-interface`, `better-*`) e LottieFiles
(`motion-design`). Licenças originais em `.agents/skills/_licenses/`;
origem e versão em `docs/SKILLS_ORIGINAIS_EXECUCAO.md`.

**Antes de editar uma tela, LER o SKILL.md original aplicável.**
A abordagem `dembrandt` define a ordem; `better-interface` e suas seis
skills de domínio auditam a tela; `interface-review` define escopo e
regressões do PR. `improve-animations` é **somente auditoria e plano**;
`animate` implementa mudanças de movimento; `review-animations` revisa
as animações do diff; `motion-design` orienta intenção e qualidade.
Nunca representar uma auditoria read-only como implementação executada.

Skills próprias `central-ux`, `central-motion`, `central-interface-review`
e `central-animation-runtime` são adaptadores de contexto Android.
**Em caso de divergência**, respeitar primeiro regras do produto,
segurança/privacidade, restrições nativas do Compose e instruções oficiais
de escopo de cada skill, registrando a decisão. Não executar receitas
CSS/Framer Motion em código Android; traduzir comportamento para Compose
e justificar no relatório.

## Regras vinculantes do produto
- Tiago usa acento azul e Rhayra rosa; manter cores sem sacrificar contraste. Não inferir autorização de dados pelo nome ou cor do perfil.
- Alvos: Samsung S23 FE e S25 FE, telas pequenas, fonte ampliada, TalkBack e navegação por toque.
- Movimento é opcional pelo usuário; respeitar a escala de animação do Android. Feedback de áudio e vibração só em ações explícitas, obedecendo às preferências.
- Não colocar animação, Lottie ou Rive como dependência do app sem uso funcional, licença conferida e teste de desempenho.
- Não criar dados falsos, compromissos inventados ou componentes inertes que pareçam conectados. Estados de erro, offline e sem permissão devem ser explícitos.
- Respeitar o escopo de dados pessoais e do household, não gravar segredos, credenciais ou localização precisa em código/prints/logs.
- Alterações devem entrar por branch e PR; CI e revisão são necessários. Não declarar APK validado nem testes físicos sem evidências.

## Fluxo recomendado em cada PR de UI
1. Resolver escopo do PR: `interface-review`; ler `Design.kt`, tela, tokens e diffs (incluindo as linhas removidas).
2. Executar `dembrandt` com fases pertinentes; `better-interface` + skills de domínio (a11y, layout, writing, typography, colors, ui). Registrar fases puladas.
3. Para motion: executar análise `improve-animations` sem tocar em código, depois delegar um fix específico a `animate`; revisar usando `review-animations` e `motion-design`.
4. Implementar mudanças mínimas e reversíveis em Compose com estado real, permissão correta, alt/semântica e suporte a motion reduzido.
5. Executar Android/JVM e Firebase quando afetados; registrar resultados e revisar PR sem confundir teste estático com QA físico.
6. Registrar evidências e pendências em `docs/SKILLS_ORIGINAIS_EXECUCAO.md` e, para padrões compartilhados, `docs/UX_MOTION_SKILLS.md`.
