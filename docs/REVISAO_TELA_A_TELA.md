# Revisão tela por tela: Central do Casal

Data da revisão inicial: 18/10/2026.

## Base e fluxo
Este documento analisa o redesign em desenvolvimento no PR #45 (`ux/fluxos-limpos-2026-10-14`), não a UI antiga da main. As skills de UX, motion e interface do PR #52 são a metodologia de referência; as duas branches ainda não foram mescladas. Cada tela será melhorada em PR pequeno, inicialmente baseado no redesign, com auditoria e CI antes de integrar. Não sobrescrever o trabalho pendente de outros PRs.

## Critérios de conclusão de qualquer tela
- Fluxo principal funcional usando dados reais e permissões do próprio usuário.
- Estados vazios/carregando/offline/erro/permissão negada/teste de fontes grandes.
- Sem truncamento em S23 FE e S25 FE (testar fisicamente; simulação de densidade não equivale a aparelhos reais).
- Botões com ação verdadeira; navegação preserva destino e contexto.
- Acentos azul (Tiago) e rosa (Rhayra), sem inferir autorização de dados pela cor/nome.
- Movimentos com função clara, opção de reduzir e fallback estático. Lottie/Rive só após asset real licenciado e testes.
- Testes unitários/Compose quando aplicável; regressões de Firebase se alterado. Evidência do CI obrigatória antes do merge.
- Classificar problemas encontrados como pré-existentes, regressões ou novos; não inventar resultados de QA.

## Backlog e prioridade

| Ordem | Tela / rota | Referência | Diagnóstico inicial e ação planejada | Fase |
|---|---|---|---|---|
| 01 | Hoje | HomeExperience.kt e WeatherExperience.kt | 4 cards estreitos de cuidados e alvo de toque de 40dp; ação de conclusão rotulada como registro. Implementar cards adaptativos de 1/2/4 colunas, área inteira clicável e texto correto. Depois conferir densidade do clima, privacidade, 3 dicas automáticas e atualização por hora. | Primeira correção em PR específico; demais itens pendentes |
| 02 | Rotina | MainActivity.kt / RoutineScreen | Revisar acesso a tarefas próprias/compartilhadas, recorrências, filtros, conclusão e edição; evitar ocultar pendências importadas. | Pendente |
| 03 | Compras | ShoppingHub.kt e ShoppingExperience.kt | Incorporar aprendizados do PR #51 sem replicar seu fluxo; ênfase em lista rápida, pré-lista, carrinho, quantidade, orçamento e teclado. | Aguardar decisão sobre PR #51 |
| 04 | Lembretes | ReminderInbox e Reminders.kt | Auditar horários, repetições, sossego, permissões e app fechado; reconciliar PR #44. | Pendente |
| 05 | Casa inteligente | HomeDevicesScreen | Ambientes e disponibilidade, diagnóstico offline, controles reais, integração Alexa/Home Assistant sem inventar estado. | Pendente |
| 06 | Planos | PlansExperience.kt | Hierarquia, etapas, acompanhamento, vínculos com compras/rotina e ações que geram tarefas por confirmação. | Pendente |
| 07 | Nós | TogetherScreen / JourneyExperience.kt | Acesso a momentos reais, confirmação dupla, privacidade e redução de gamificação indesejada. | Pendente |
| 08 | Sophia | SophiaReferenceScreen, WeatherExperience.kt | Três dicas diárias automáticas baseadas em previsão/agenda, com origem rastreável; nunca dados privados do outro perfil. | Pendente |
| 09 | Configurações/Mais | SettingsScreen, HomeMoreScreen | Preferências de som/haptics/motion, consentimentos, status de integração, limpeza de conta e acessibilidade. | Pendente |
| 10 | Finanças | FinanceScreen | Organizar dados reais e estados vazios, sem afirmar integração Poup+ ou Open Finance. | Pendente |
| 11 | Saúde | HealthScreen | Acesso estritamente individual, microcopy cuidadosa e estados de registro sem exposição ao household. | Pendente |
| 12 | Segurança/SOS | SafetyScreen | Validar confirmação, privacidade e estados de falha; reconciliar PRs #39 e #11 sem prometer serviço de emergência. | Pendente |
| 13 | Entrada e conta | AuthScreen | Revisar mensagens, validação, recuperação, convite, estados local/cloud e acessibilidade do teclado. | Pendente |
| 14 | Nossa vida / histórico | LifeOverview e telas de histórico | Consistência de vínculos e filtros; evitar duplicar contadores ou informações no feed principal. | Pendente |

## Primeira tela: Hoje
Problema confirmado por inspeção estática do PR #45: quatro colunas estreitas para cuidados, botão de mais com 40dp, texto da ação principal impreciso. A correção:
1. Layout responsivo por largura realmente disponível e escala de fonte: 1 coluna (<305dp ou >=1.6x), 2 colunas em celular, 4 apenas em área >=720dp com fonte padrão.
2. O card completo de cuidado é uma área de toque, preservando `home-care-<kind>` e o registro real.
3. "Concluir atividade" substitui "Registrar atividade" para indicar a operação de conclusão.
4. Alvos de toque do atalho de lembretes, ação de conclusão e progresso ficam com pelo menos 48dp.
5. O método puro `homeCareColumns` recebe testes JVM cobrindo diferentes larguras/escalas.

### Estado de validação
- Código e testes gravados na branch, ainda dependentes do CI.
- QA visual S23 FE e S25 FE não realizado.
- Previsão/Sophia não foram alteradas nesta primeira correção: não declarar três dicas garantidas no aplicativo instalado.
- Segurança Firebase e sincronização não foram alteradas.

## Dependências entre PRs
- PR #45: redesign base (não mesclado).
- PR #52: skills/tokens/motion para agentes (não mesclado).
- PR #51: pré-lista de compras, deve ser conciliado antes de mexer novamente no fluxo.
- PR #41: Home/clima automático e privacidade; comparar antes de mudanças posteriores em WeatherExperience.
- PRs #44/#39/#43: lembretes, SOS e momentos; dependências de merge/QA.

## Próxima revisão
Após CI e inspeção da Home: começar Rotina. No caso do clima, validar o motor de 3 dicas que respeite horas e contextos autorizados, sem completar espaços com alertas inventados.
