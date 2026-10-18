# Execução real das skills originais | Central do Casal

Data: 2026-10-18. Branch: `ux/upstream-skills-aplicadas-fase10-20261018`. Base: PR #61.

## O que significa usar as skills aqui

Os arquivos `SKILL.md` originais dos autores estão versionados, sem adaptações, em `.agents/skills/<nome>/SKILL.md`. Foram copiados das fontes GitHub indicadas pelo usuário, com as respectivas licenças MIT em `.agents/skills/_licenses/`. Não executamos códigos externos, instaladores npm, servidores MCP ou bibliotecas de animação por causa da simples presença dessas instruções.

O agente primeiro lê a skill original, sua finalidade e suas restrições, depois aplica suas etapas à implementação Kotlin/Compose existente. As skills `central-*` fornecem apenas o contexto adicional do projeto. Nenhuma skill nativa de CSS/DOM foi executada como código Android.

## Fonte, arquivos originais e versão de referência

| Autor / fonte | Skills ou materiais efetivamente copiados | SHA do blob de SKILL.md na leitura |
| --- | --- | --- |
| [Emil Kowalski](https://github.com/emilkowalski/skills) | `animate` + `RECIPES.md`, `review-animations` + `STANDARDS.md`, `improve-animations` + `AUDIT.md` e `PLAN-TEMPLATE.md` | animate `7c3b20c6`; review `577a6439`; improve `d6e77f37` |
| [Dembrandt skills](https://github.com/dembrandt/dembrandt-skills) | `dembrandt`, `visual-emphasis-and-hierarchy`, `ui-density`, `notifications-and-recovery`, `wcag-accessibility` | `ccbf1394`, `63b875ab`, `cd9f4834`, `aaea7815`, `ef3f9717` |
| [Jakub Krehel](https://github.com/jakubkrehel/skills) | `interface-review`, `better-interface`, `better-accessibility`, `better-layout`, `better-writing`, `better-typography`, `better-colors`, `better-ui` | interface-review `a5a9832d`, better-interface `4d423eb7` |
| [LottieFiles](https://github.com/LottieFiles/motion-design-skill) | `motion-design` | `9b5e2df4` |

O programa [Dembrandt](https://github.com/dembrandt/dembrandt) é um extrator/organizador voltado a referências web. Seu mecanismo não foi instalado nem executado contra dados autenticados do casal. [LottieFiles](https://lottiefiles.com/) e [Rive](https://rive.app/) são ferramentas/runtimes de animação, **não** substitutos das skills. Não há asset .riv/.json/.lottie licenciado nem motivo técnico para instalar seus players neste PR. Subdocumentos referenciados por algumas skills (sobretudo Jakub/Dembrandt/LottieFiles) permanecem acessíveis no repositório original e podem ser trazidos sob demanda.

## Caso real aplicado: Home / cartão de clima

Arquivo: `app/src/main/java/com/centraldocasal/app/WeatherExperience.kt`.

### 1. Dembrandt: etapas e evidência

- **Brand e tokens (1–2): preservadas, não reprojetadas.** `Design.kt` já define identidade azul e rosa, tipografia Compose e escala de espaçamentos, então mudar marca seria fora do escopo.
- **Layout / hierarquia (3): aplicado.** `ui-density` e `visual-emphasis-and-hierarchy`: no resumo da Home, as três dicas permanecem visíveis automaticamente; a linha de chuva repetida fica somente em `expanded`, com os dados por hora. Resultado: menos leitura repetida e um contexto meteorológico só.
- **Componente / interação (4): aplicado.** A ação `Detalhes` muda para `Ver por hora` ou `Ocultar previsão`, indicando explicitamente o conteúdo e a ação.
- **Polimento (5): aplicado.** O cartão usa easing compartilhado de origem documentada, sem outra biblioteca de animação. Nenhuma animação automática em cada dica; o movimento existente pertence à expansão.
- **Acessibilidade (6): inspeção estática.** A ação de detalhes passa a ter área de toque mínima de 48dp. O `Switch` de avisos recebe nome acessível no Compose. **Faltam testes reais com TalkBack**, dimensionamento de fonte e confirmação visual no S23 FE / S25 FE.

### 2. Jakub Krehel: revisão da interface

- `better-writing`: label do botão deve ser verbo + objeto. Antes: `Detalhes` / `Fechar`; agora: `Ver por hora` / `Ocultar previsão`.
- `better-accessibility`: área tocável ampliada para 48dp e nome do interruptor específico, sem reconstruir um controle nativo.
- `better-layout` / `better-ui`: menos informação duplicada na Home e destino de detalhamento reconhecível.
- `better-typography`: preservados tokens Compose existentes. **Não medimos texto ampliado em aparelho real**.
- `better-colors`: tokens de tema mantidos; **sem auditoria medida de contraste neste PR**, portanto não se declara aprovação WCAG global.
- `interface-review`: escopo limitado ao diff desta branch; não atribuir problemas históricos da Home a essa revisão.

### 3. Emil Kowalski: funções distintas

- `improve-animations` (somente leitura): identificou o cartão expandível como único ponto de movimento deste escopo que precisa comunicar mudança de contexto; as dicas diárias não ganham animação a cada atualização, porque seriam frequentes e distrativas.
- `animate` (implementação): `MotionTokens.kt` recebe `centralEaseOut = CubicBezierEasing(0.23, 1, 0.32, 1)`, o valor exato de `animate/SKILL.md`. O `WeatherCard` usa `tween(centralMotionDurationMillis(MotionKind.LAYOUT), easing=centralEaseOut)`.
- `review-animations` (revisão do diff): uso restrito a movimento de expansão, 220ms (inferior a 300ms), com motion pessoal e `ValueAnimator.areAnimatorsEnabled()` já integrados; não há loop decorativo. **Avaliação física de interrupção e frames permanece pendente**.

### 4. LottieFiles: motion-design

Intenção emocional: calma e utilidade doméstica, não espetáculo. Narrativa: abrir detalhes → ver janelas por hora. Propriedades usadas: somente reflow do Compose existente, sem efeito novo para sugestões meteorológicas. O runtime Lottie/Rive foi **deliberadamente dispensado** nesta alteração por não existir uma ilustração/estado interativo que justifique custo de APK, assets e licenças. A decisão é parte da aplicação da skill, não falta de execução.

## Quadro de achados restrito ao caso

| Tipo | Severidade | Arquivo / componente | Antes | Alteração | Validação |
| --- | --- | --- | --- | --- | --- |
| Melhoria | Média | WeatherExperience.kt / ação de detalhes | Rótulo abstrato e alvo mínimo 38dp | Verbos claros e 48dp | Código; TalkBack pendente |
| Melhoria | Média | WeatherExperience.kt / avisos | Switch com rótulo visual, sem nome semântico explícito | Semântica Compose no Switch | Código; leitor de tela pendente |
| Melhoria | Baixa | WeatherExperience.kt / chuva | Linha extra de chuva junto das três dicas | Linha passa para detalhes expandidos | Código; revisão visual pendente |
| Melhoria | Baixa | MotionTokens.kt / WeatherCard | tween com easing default | curva ease-out exata centralizada | Teste JVM/CI pendente neste PR |
| Pré-existente | Não avaliado | Home, tipografia/contraste | Sem medição com hardware | Não modificado | Não declarar aprovado |

## Próximas telas usando as skills originais

| Prioridade | Tela | Skills necessárias | Status |
| --- | --- | --- | --- |
| P0 | Hoje / Clima | dembrandt, ui-density, better-writing, better-accessibility, animate, review-animations, motion-design | Primeira aplicação em código nesta branch |
| P1 | Rotina / Lembretes | better-interface, better-layout, notifications-and-recovery, better-accessibility, improve-animations | A auditar com screenshots reais |
| P1 | Compras (PR #61) | interface-review, better-layout, better-writing, better-accessibility, dembrandt | A conciliar e validar |
| P1 | Casa inteligente | better-interface, ui-density, better-writing, notifications-and-recovery | Estados conectados e offline precisam de QA |
| P2 | Nós / Jornada | motion-design, animate, review-animations, better-ui | Código anterior existe; QA físico pendente |
| P2 | Planos, Finanças, Saúde, Segurança, Entrada e Nossa Vida | better-interface e skills de domínio selecionadas; animate apenas onde houver propósito | Revisões anteriores não substituem auditoria original |

## Gates de entrega

- [x] Arquivos upstream e licenças copiados sem editar o conteúdo dos SKILL.md originais.
- [x] AGENTS.md usa as skills originais como referência de execução e informa quando uma skill é somente auditora.
- [x] Um caso real revisado com mudanças no Kotlin/Compose, sem alterar Firebase ou dados familiares.
- [ ] CI Android/JVM/Firestore deste PR.
- [ ] Avaliação de TalkBack, contraste medido, fontes grandes, capturas lado a lado e hardware S23 FE / S25 FE.
- [ ] Reconciliar toda a pilha de PRs com a branch principal e avaliar regressões cumulativas.
- [ ] Decidir se uma animação Lottie/Rive é justificável em telas futuras com assets revisados.

Não confundir "skill instalada no repositório" com "agente executado automaticamente". Estas skills são documentação operacional lida e aplicada pelos agentes de código. O PR registra a primeira execução assistida e auditável.
