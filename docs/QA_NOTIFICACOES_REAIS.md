# Teste físico de notificações (Samsung)

Fluxo de aceitação para o S23 FE e S25 FE:

1. Instale o APK DEV gerado por CI, confira a conta ativa e abra **Configurações > Notificações e permissões**.
2. Autorize notificações. No Android 14+, verifique também o acesso especial **Alarmes e lembretes**. Se negado, a entrega pode ser aproximada.
3. Toque **Testar agendamento real em 2 minutos**. Feche o aplicativo, bloqueie a tela e anote o horário esperado.
4. Aguarde até aparecer o título **Teste agendado da Central do Casal**, com horário, responsável e observação. Não confundir com o teste instantâneo: este usa o agendador Android e o BroadcastReceiver.
5. Abra uma tarefa real para 3–5 minutos à frente e confira se o texto exibido corresponde ao registro, não a mensagem genérica.
6. Teste uma tarefa concluída antecipadamente, recorrência amanhã, reinício do celular, economia de bateria, Não Perturbe e configuração do canal **Tarefas com detalhes**.
7. Registre: aparelho, Android/One UI, horário agendado, horário recebido, atraso, permissão de alarme exato, status da bateria, app aberto/fechado, captura sem dados sensíveis.

O teste programado é local e não grava registros no Firebase. Se o sistema negar a entrega ou adiar pelo gerenciamento de bateria, o aplicativo não deve afirmar sucesso sem a confirmação física. **SOS é separado de notificações de tarefa e não pode depender deste canal para segurança.**

## Falha por permissão ou canal bloqueado

Se o Android impedir uma notificação recorrente, o aplicativo deve registrar o bloqueio local e manter programada a próxima ocorrência. Isso não recupera avisos perdidos nem garante pontualidade no Samsung.

1. Confira o diagnóstico de entrega bloqueada em **Configurações > Notificações e permissões**.
2. Reative as notificações e toque **Reagendar tarefas**.
3. Faça **Testar agendamento real em 2 minutos** com tela bloqueada.
4. Na bandeja, título, horário, responsável e observação aparecem completos. Na tela bloqueada, detalhes ficam ocultos, a menos que o próprio usuário ative sua exibição.
