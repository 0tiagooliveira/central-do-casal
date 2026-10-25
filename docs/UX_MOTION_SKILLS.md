# Integração de skills e animação: Central do Casal

Data: 18/10/2026. Alvo: projeto Android nativo, Kotlin + Jetpack Compose, branch de integração de skills.

## O que foi integrado de fato
- AGENTS.md: convenções globais e gate para agentes.
- .agents/skills/central-ux/SKILL.md: orquestração de design conforme Dembrandt e análise de UI conforme Jakub Krehel.
- .agents/skills/central-motion/SKILL.md: processo de criação e revisão de movimento conforme Emil Kowalski e LottieFiles.
- .agents/skills/central-interface-review/SKILL.md: revisão de diff, estados, acessibilidade e privacidade.
- .agents/skills/central-animation-runtime/SKILL.md: critérios de adoção de Compose/Lottie/Rive.
- MotionTokens.kt e MotionTokensTest.kt: primeiro componente real do design de movimento no app.
- ScreenChrome.kt: primeiro consumidor dos tokens, respeitando preferência pessoal e o Android.

Essas são skills LOCAIS, escritas para o projeto; os arquivos integrais dos fornecedores não foram copiados nem os programas externos instalados. Um agente pode usá-las imediatamente neste repositório ao ler AGENTS.md. Os projetos de origem continuam disponíveis abaixo e podem ser instalados em ambiente de agente, após revisão.

## Proveniência e uso das referências

| Origem | Skills/recursos úteis | Uso aqui |
| --- | --- | --- |
| https://github.com/emilkowalski/skills | animate, review-animations, improve-animations | Escolha da transição, auditoria e qualidade da implementação |
| https://github.com/dembrandt/dembrandt | mecanismo Dembrandt para extração de tokens web | Referência pública de design, não extração direta de Compose |
| https://github.com/dembrandt/dembrandt-skills | dembrandt, typography, visual language, accessibility | Fluxo UX em seis fases e tokens |
| https://github.com/jakubkrehel/skills | better-ui, better-layout, better-accessibility, interface-review | Revisão de layout e estados |
| https://github.com/LottieFiles/motion-design-skill | motion-design | Propósito, easing, hierarquia, transições e redução de estímulos |
| https://lottiefiles.com/ | biblioteca/editor de assets Lottie | Opcional e condicionado à aprovação de arte/licença |
| https://rive.app/ | runtime e editor Rive | Opcional para interação por máquina de estados |

Os repositórios de skills consultados publicam licença MIT; verificar novamente a licença e procedência de cada asset, dependência e versão antes de redistribuir. Uma biblioteca licenciada não concede automaticamente licença para todos os arquivos animados.

## Como usar no trabalho diário
1. Antes de redesenhar uma tela, ler AGENTS.md e central-ux.
2. Se houver movimento, ler central-motion e registrar propósito, duração, gatilho, estado final, cancelamento e fallback estático.
3. Se precisar ilustração animada, usar central-animation-runtime e justificar Compose/Lottie/Rive; não instalar runtime por catálogo.
4. Após mudanças em interface, aplicar central-interface-review ao DIFF e sua abrangência, não somente uma captura isolada.
5. Executar CI; documentar teste em aparelho físico separadamente, pois JVM/Emulator não os substituem.

## Auditoria inicial aplicada ao código existente

### Problemas confirmados por inspeção estática
1. ScreenChrome.kt usa animateContentSize(220ms), condicionado apenas à preferência motion do aplicativo, não diretamente ao estado global das animações do Android. A primeira correção adota MotionTokens.kt.
2. SectionHero limita o subtítulo a uma linha, o que pode ocultar instruções em fonte ampliada. Permitir múltiplas linhas conforme escala.
3. GoalEditor.kt, PlansExperience.kt e MainActivity.kt ainda possuem durações de tween específicas espalhadas; migrar com cautela numa etapa posterior para tokens compartilhados, mantendo testes de regressão.
4. A marca individual em Design.kt depende de uma verificação do nome do usuário. É útil hoje, mas frágil se houver alteração do nome; futura evolução deve usar preferências de perfil explícitas, sem inferir permissão pela cor.

### Hipóteses que precisam de QA físico
- Densidade da Home em S23 FE e S25 FE com 1.3x e 2x fontes.
- Desempenho de transições, duração real, consumo e respostas hápticas.
- Truncamentos em componentes menores e filtros.
- Alternância de estado na presença de dados offline e mudanças frequentes na agenda.

Não declarar falha física a partir de leitura do Kotlin. Não confundir auditoria visual com aprovação de segurança, Firebase ou operação de dispositivos.

## Política de motion
- Feedback: 140ms. Navegação: 180ms. Adaptação de layout: 220ms. Progresso: 400ms, se a mudança for real.
- Se motion desativado no perfil OU animações desativadas no Android: duração zero.
- Evitar loops infinitos, animações chamativas em tela de clima, tremores em alertas e sons não solicitados.
- Haptics e sons apenas após toque explícito e sob preferências.
- Animação não pode esconder informação, atribuir status falso ou causar perda de foco.

## Decisão sobre assets
Começar com animações Compose. Lottie Compose pode ser usado para microilustrações pontuais com licença revisada e versão fixada. Rive é justificável para personagem Sophia realmente interativo, caso exista arte .riv com entradas e máquina de estados validadas. Nesta etapa nenhuma dessas dependências é necessária. Exigir protótipo, revisão de licença, medição de APK e teste nos aparelhos antes de adicionar.

## Backlog sequenciado
- M1 [entregue na branch]: governança dos agentes, skills locais, tokens puros e migração do SectionHero.
- M2 [pendente]: migrar demais tweens de MainActivity, GoalEditor, PlansExperience e ShoppingExperience, sem mudar semântica.
- M3 [pendente]: auditoria com screenshots em S23 FE e S25 FE, fontes ampliadas e TalkBack.
- M4 [pendente]: piloto de uma transição funcional da Sophia/clima que não exija ação do usuário para gerar três dicas reais.
- M5 [condicional]: aprovar primeiro asset Lottie ou Rive (ou dispensar) e aferir desempenho.
- M6 [pendente]: adicionar screenshots de estado vazio, erro, online, offline, movimento desativado em PRs de UI.

## Segurança da cadeia de suprimentos
Nunca executar automaticamente npx/install scripts de repositórios externos em CI ou em máquina com tokens de produção. Avaliar versão, código e permissão; instalar manualmente em ambiente isolado e, se adotado, registrar versão e licença. O Dembrandt/MCP somente deve analisar websites públicos; páginas com dados familiares ou autenticação não devem ser enviadas para extratores web sem autorização explícita.
