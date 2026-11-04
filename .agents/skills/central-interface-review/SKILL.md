---
name: central-interface-review
description: Audita diffs e telas da Central do Casal com critérios de Jakub Krehel e Dembrandt, sem confundir achados antigos com regressões.
---

# Revisão de interface Android

Ativar em PR de UI, revisão de screenshots ou alteração visual significativa.

## Metodologias
- https://github.com/jakubkrehel/skills/tree/main/skills/interface-review
- https://github.com/jakubkrehel/skills/tree/main/skills/better-interface
- https://github.com/dembrandt/dembrandt-skills/tree/main/skills/dembrandt

## Método
1. Delimite PR/diff e as telas impactadas (incluindo componentes compartilhados). Se não houver diff, nomeie explicitamente revisão de estado atual.
2. Separar REGRESSÃO, NOVO PROBLEMA e PRÉ-EXISTENTE. Não atribuir problema anterior à mudança.
3. Auditar hierarquia, excesso de espaço, contraste, paleta individual, legibilidade, toque, fontes 1.3x/2x, TalkBack, dark/large text se suportado e elementos truncados.
4. Auditar estados vazios, erro, carregamento, offline, não autorizado, dispositivo indisponível, clima desatualizado e privacidade.
5. Auditar movimento: intencionalidade, repetição, interrupção e escala de animações Android.
6. Verificar navegação contextual e comportamento real; sem controles decorativos, inventados ou sem função.
7. Apresentar achados por severidade, caminho/elemento, evidência, ação corretiva, forma de testar e veredito BLOCK ou APPROVE.
8. Se não houver screenshots ou QA físico, explicitar limite. Não aprovar acessibilidade real só por ler código.

## Obrigatório para esta família
O cartão do clima deve mostrar automaticamente três recomendações quando houver dados, sem botão para solicitá-las; não inventar agenda nem compromissos privados. Evitar que uma dica oculte tarefas, finanças e cuidados.
