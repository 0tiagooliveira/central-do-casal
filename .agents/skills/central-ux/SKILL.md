---
name: central-ux
description: Planeja e implementa telas e design system nativo da Central do Casal com a metodologia Dembrandt e os critérios de interface de Jakub Krehel.
---

# Central UX: marca, tokens, fluxos e Compose

Ativar quando a tarefa envolver design de tela, hierarquia, tokens, cor, layout, navegação, tipografia ou componente reutilizável.

## Referências consultadas
- Dembrandt: https://github.com/dembrandt/dembrandt
- Skills Dembrandt: https://github.com/dembrandt/dembrandt-skills (orquestrador dembrandt, brand-visual-language, modular-scale-typography, wcag-accessibility, component-family-consistency).
- Skills Jakub Krehel: https://github.com/jakubkrehel/skills (better-ui, better-layout, better-typography, better-colors, better-accessibility).

## Procedimento para esta base Android
1. Inspecione Design.kt e Experience.kt antes de sugerir tokens. Não substitua a identidade azul/rosa, as permissões ou fluxos funcionais.
2. Escreva uma hipótese do problema e os estados reais: carregando, vazio, pronto, erro, sem rede, bloqueado, dados privados, fonte grande.
3. Execute em sequência: identidade -> tokens -> estrutura -> componentes -> microcopy -> acessibilidade. Cada etapa precisa de decisão explícita antes da seguinte.
4. Reuse MaterialTheme, cores semânticas, RoundCornerShape e Compose. Não traduza bibliotecas CSS/React em dependências Android.
5. Escolha densidade da informação: uma ação primária por contexto, sem cards gigantes para informações pequenas. Conteúdo de clima e Sophia deve caber na Home sem esconder a agenda.
6. Verifique toque >= 48dp em controles essenciais, contraste WCAG AA para textos aplicáveis, semântica TalkBack, fonte ampliada, teclado e rolagem.
7. Mostre estados e evidências. Não chame mock de dado vivo.

## Entregáveis
Especificação de tela, componentes afetados, tokens necessários, comportamento em cada estado, testes e riscos. Em código, mudanças pequenas com PR e sem regressão de autenticação/sincronização.

## Restrição
O Dembrandt extrai CSS/DOM de sites; não é um extrator de telas Compose. Pode inspirar a avaliação de referências web públicas, mas não executar contra telas autenticadas com dados familiares nem adicionar MCP sem revisão de segurança e consentimento.
