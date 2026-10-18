# Sophia: cérebro operacional da Central do Casal

Status em 18/10/2026: implementação inicial em PR draft, ainda não distribuída.

## O que funciona nesta implementação
- SophiaBrain.kt: roteador local de pedidos em português. Consulta agenda, compras, planos e cadastro de dispositivos. Não há IA generativa, áudio nem acesso automático a integrações externas.
- SophiaReference.kt: conversa digitada com respostas explicando a origem dos dados; confirmação explícita de propostas, cancelamento, indicação de processamento e atalhos. Até 24 mensagens em memória, isoladas por UID e não persistidas.
- CentralViewModel: executor de operações tipadas. Apenas criar tarefa pessoal ou concluir tarefa existente; revalida UID, permissão, data e estado no repositório real antes de salvar.
- SophiaBrainTest.kt: testes de interpretação de comandos, isolamento de dados, tarefas concluídas, horários inválidos, confirmação necessária e impossibilidade de controlar hardware não conectado.

## Princípios obrigatórios
1. Planejar não significa executar. O cérebro só retorna uma resposta ou uma proposta.
2. Tarefa ou conclusão exige toque de confirmação. Cancelar não escreve nada.
3. A Sophia não recebe acesso arbitrário ao Firestore, câmera, geolocalização, senhas, Home Assistant ou outras APIs.
4. Ao criar uma tarefa, o registro é privado por padrão; só o titular vê o conteúdo. Conclusão de tarefa exige responsável autorizado, data correspondente e estado ainda pendente.
5. O resultado de sucesso só aparece depois da tentativa confirmada de persistência. Em erro de rede, avisar que o resultado não foi confirmado.
6. Nunca confundir inventário de dispositivos com estado on-line; tarefas com lembretes podem depender de permissões Android.
7. A interface é um coordenador local determinístico. Não usar a expressão “chat generativo conectado” até existir backend seguro, consentimento e testes.

## Comandos disponíveis
- “Como está meu dia?” e “Agenda amanhã”: tarefas pendentes no perfil.
- “O que falta nas compras?”: contagem de itens da lista.
- “Como está nossa casa?”: dispositivos cadastrados (sem telemetria).
- “Quais são meus planos?”: resumo do cadastro de planos.
- “Crie uma tarefa estudar amanhã às 19:00”: prévia, confirmação, gravação privada.
- “Lembre-me de dar ração à Mel amanhã às 22:00”: exige horário e confirmação.
- “Concluir tarefa Estudar”: exige correspondência exata e única com tarefa pendente.
- “Ligue a luz”: ainda não suportado, nenhum comando enviado.

## Etapas para chegar ao Jarvis
### A: validar MVP
CI Android/JVM e regras Firebase, QA em Samsung S23 FE/S25 FE, dois perfis, fonte ampliada, TalkBack, falhas e retomada. Sincronização Firebase não pode ser inferida dos testes locais.

### B: IA com backend autenticado
Firebase Auth, validação do usuário e household no servidor, limites de uso, registro mínimo e privado, chamadas a modelo na nuvem sem chave no APK. O modelo sugere operações tipadas e cada ferramenta revalida escopo e permissão. Toda escrita sensível demanda consentimento explícito.

### C: Sophia proativa
Integrar agenda real, motor meteorológico, rotinas familiares, limites de notificações, janelas silenciosas e memória opt-in corrigível. Não enviar informações privadas do parceiro ao outro usuário.

### D: casa inteligente
Conector Home Assistant com OAuth/armazenamento protegido, leitura recente de disponibilidade e comandos físicos confirmados, com rechecagem de estado. Alexa, lâmpadas e TV continuam sem controle até integração real.

### E: voz e design
Microfone push-to-talk e TTS consentidos, testes de privacidade e bateria. Aplicar skills originais Dembrandt, Jakub, Emil e LottieFiles; usar Lottie/Rive apenas se recurso e licença forem aprovados, sem efeitos decorativos na Home.

## Limites conhecidos
As propostas não são um agente geral. Não há planejamento autônomo contínuo, chat de IA, acesso a bancos, importação de calendário, controle da Alexa nem operação de dispositivos físicos. O aplicativo de produção não é modificado por este PR.
