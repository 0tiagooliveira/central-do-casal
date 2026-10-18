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


## Revisão com skills originais, tela 02: Rotina (PR da fase 11)

Base da inspeção: `ux/upstream-skills-aplicadas-fase10-20261018`. Escopo: `RoutineScreen` e `TaskRow` em `MainActivity.kt`, junto de novos testes de regras determinísticas. Este relatório descreve somente a fase 11, não é aprovação de todo o aplicativo.

### Dembrandt: etapas aplicadas
- Etapas 1 e 2 (marca/tokens): preservadas por existirem em `Design.kt`; identidade azul/rosa inalterada.
- Etapa 3 (estrutura): cinco grupos em ordem temporal: madrugada (00–04h), manhã, tarde, noite e sem horário. Sem horário inválido ou em revisão passa ao grupo sem horário para não afirmar um agendamento inexistente.
- Etapa 4 (interação): o dia solicitado por deep link é mantido no contexto de Semana; o fluxo de revisão exibe tarefas para corrigir, sem checkbox de conclusão ou progressos de outro dia.
- Etapa 5 (polimento): ação “Nova tarefa” deixa de aparecer duplicada na maioria das abas. Não adicionamos animação à conclusão recorrente de tarefas.
- Etapa 6 (acessibilidade): checkbox explica “Concluir” ou “Reabrir” conforme estado; controle de criação conserva alvo de pelo menos 48dp. Não houve teste TalkBack físico.

### Skills originais específicas e evidência
- `better-layout` + `ui-density`: grupos de horário exclusivos, em ordem cronológica e sem item duplicado.
- `better-writing`: títulos “Tarefas para revisar”, “Nada para revisar”, sem confundir revisão com “Atividades do dia”.
- `better-accessibility`: rótulo semântico dinâmico para o checkbox e área de toque mínima para criação.
- `notifications-and-recovery`: abrir um dia de lembrete anterior/futuro não pode saltar silenciosamente para Hoje.
- `improve-animations` (somente leitura) + `animate`: decisão explícita de **não animar** interação altamente frequente de conclusão de tarefa; remove `animateContentSize` do `TaskRow`. O comportamento instantâneo comunica o estado por checkbox e cor.
- `interface-review`: achados relacionados à experiência de Rotina são anteriores a esta alteração; nenhum resultado físico foi inferido.

### Resultado por achado
| Severidade | Tipo | Problema anterior | Correção implementada | Gate |
| --- | --- | --- | --- | --- |
| Alta | Pré-existente | Deep link para dia diferente podia voltar para Hoje na inicialização | `initialRoutineSection` e atualização coerente da aba e data | Testes JVM + CI |
| Média | Pré-existente | Compromissos às 00h apareciam em Noite | `routineTaskPeriod` cria Madrugada | Testes JVM + CI |
| Média | Pré-existente | Revisão mostrava contagem de conclusão de data sem relação com o item | Substitui por contagem de itens a revisar; desliga checkbox na revisão | CI + QA manual |
| Média | Pré-existente | Um horário que precisava ser confirmado aparecia sob a hora antiga | Item agrupado em Sem horário e microcopy “Horário a confirmar” | Testes JVM |
| Baixa | Pré-existente | CTA “Nova tarefa” duplicado | Somente CTA contextual em atividades; atalho em Cuidados | UI inspection |
| Baixa | Pré-existente | Checkbox sempre descrevia “Concluir” mesmo concluído | Nome acessível passa a “Reabrir” quando necessário | TalkBack pendente |

### Fora de escopo / próximos cuidados
- O filtro de acesso do backend permanece a fonte de autorização; não mudei Firestore.
- Testar navegação real de deep links por notificações em ambos os aparelhos e validar a permanência do dia após restaurar estado.
- Testar tarefas importadas com `needsReview` e `needsTimeReview` no aplicativo instalado.
- Validação de acessibilidade com fonte 1.3x/2x e TalkBack; screenshots em S23 FE e S25 FE pendentes.
- Resgatar e reconciliar PR #44 (lembretes) antes de mexer na entrega real de notificações.


## Revisão com skills originais, tela 04: Lembretes (fase 12)

Escopo: `ReminderInbox.kt`, caller de `MainActivity.kt` e `ReminderInboxPrivacyTest.kt`.
Base da fase 11; alterações não incluem o agendador `Reminders.kt` nem regras Firebase.

### Etapas originais usadas
- **dembrandt** etapas 3/4: listas separadas para pendências confirmadas do dia e tarefas que exigem revisão, porque nenhuma das duas é uma lista de entregas garantidas do Android.
- **better-writing**: "Tarefas pendentes de hoje" substitui a interpretação implícita de que toda pendência tem aviso; cada botão tem verbo e objeto.
- **notifications-and-recovery**: status realmente consultável por `Reminders.inspect`, com avisos para configurações desligadas e ligação direta ao fluxo de revisão importada. "Programado" não equivale a "entregue".
- **better-layout / ui-density**: cartão de cada tarefa mantém informações essenciais e oferece diagnóstico detalhado sob demanda. `FlowRow` permite que ações quebrem linha em aparelhos estreitos.
- **better-accessibility**: ações de 48dp, sem controles decorativos inertes. TalkBack físico pendente.
- **Emil / motion-design**: nenhuma animação adicionada. Alternar diagnóstico é um uso recorrente e não exige movimento; transição instantânea mantém os rótulos atualizados.

### Achados e correção
| Severidade | Status do achado | Problema | Correção |
| --- | --- | --- | --- |
| Alta | Pré-existente | Tarefas importadas com `needsReview` não aparecem em pendências porque `occurs` as exclui | Lista explícita `reminderReviewTasks` independente de ocorrência; navega à rota "Revisar rotinas" |
| Alta | Pré-existente | Horário visível podia sugerir aviso agendado/entregue sem evidência | `reminderScheduleHint` e opção "Ver estado do aviso" usando diagnóstico local existente |
| Média | Pré-existente | Histórico de tarefas do outro perfil poderia ser exposto por nova lista | Filtro `taskBelongsTo` em ambas listas, com testes |
| Média | Pré-existente | Itens à meia-noite ordenados como texto ou misturados aos sem horário | Ordenação por `LocalTime` com fallback ao final |
| Baixa | Pré-existente | Mais de um botão numa única linha fixa seria estreito | `FlowRow` responsivo com alvo >=48dp |

### Gates pendentes
- [ ] Gradle, testes JVM e Firestore do CI desta fase.
- [ ] QA S23 FE/S25 FE, fontes ampliadas, TalkBack.
- [ ] Confirmar em aparelho real `Reminders.inspect` com aviso habilitado, bloqueado, sem alarme, após reboot e economia de bateria.
- [ ] Revisar PR #44 antes de modificar entrega de alarmes; esta fase não faz alterações ao scheduler.
