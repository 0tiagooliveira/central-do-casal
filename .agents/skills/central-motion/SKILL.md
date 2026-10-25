---
name: central-motion
description: Planeja e implementa animações e microinterações Kotlin Compose da Central do Casal segundo as metodologias de Emil Kowalski e LottieFiles.
---

# Central Motion: menos ruído, mais resposta

Ativar ao alterar AnimatedVisibility, Crossfade, tween, animar progresso, feedback de toque, navegação, cards da Sophia e estados dos dispositivos.

## Fontes
- https://github.com/emilkowalski/skills (animate; review-animations; improve-animations).
- https://github.com/LottieFiles/motion-design-skill (motion-design, padrões de entrada, saída e feedback).

Use improve-animations apenas para auditoria/planejamento, conforme o seu escopo de leitura; implemente as mudanças separadamente pela skill central-motion e revise o diff com central-interface-review.

## Processo obrigatório
1. Pergunte qual informação a animação comunica: atenção, hierarquia, sucesso, transição ou estado. Se não tiver função, mantenha estático.
2. Use a opção nativa mais barata: mudança simples com Compose; recurso vetorial Lottie para ilustração curta; Rive somente para máquina de estados realmente interativa.
3. Centralize durações em MotionTokens.kt; entre 100 e 220 ms para feedback/transições comuns; use durações maiores somente quando justificadas. Nunca animar a cada recomposição.
4. Suportar interrupções, mudanças rápidas de navegação e estados offline sem saltos/loops excessivos.
5. Desligar movimento quando preferência pessoal motion estiver desativada OU animações do sistema estiverem desligadas. Não substituir o conteúdo por silêncio: o estado final continua acessível.
6. Não disparar som/vibração ao simplesmente abrir tela ou consultar dados. Haptics só após gesto real e conforme opção do usuário.
7. Proteger toques repetidos, acessibilidade de movimento reduzido, consumo de bateria e uso de GPU. Testar ambos os Samsung.

## Gate de qualidade
- Animação tem propósito mensurável?
- Respeita movimento reduzido?
- Não rouba o foco da tarefa, nem a leitura da previsão?
- Não faz shake ou pulso permanente em alertas?
- A interface continua legível se animação falhar?
- Tem teste de comportamento e validação física para mudanças visuais?

Em PR, documente função, duração, easing, gatilho, cancelamento e fallback.
