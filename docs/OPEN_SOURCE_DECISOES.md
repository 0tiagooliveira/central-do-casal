# Avaliação de open source e integração climática (2026-10-14)

## Decisões verificadas

| Tema | Solução adotada / candidata | Estado | Licença e observações |
| --- | --- | --- | --- |
| Previsão do tempo | [Open-Meteo](https://open-meteo.com/en/docs), via HTTPS e localização opt-in | Implementação inicial em `WeatherExperience.kt` | Código do servidor AGPLv3, **não incorporado** ao app. API/dados CC BY 4.0 exigem atribuição. Endpoint gratuito para uso não comercial: reavaliar contrato se o produto monetizar. |
| Avisos pontuais de tarefas | Android `AlarmManager` + `BroadcastReceiver` (API oficial) | Implementação inicial em `Reminders.kt` | Dispensa biblioteca externa; alarme exato depende de autorização; fallback aproximado pode atrasar pelo Android/OEM. Receivers após reinicialização. |
| Sincronização não urgente | `WorkManager` AndroidX | Já existente | Usar para trabalho adiado/background, **não para cumprir hora pontual de um lembrete**. |
| Automação residencial | [Home Assistant Core](https://github.com/home-assistant/core) + [Companion Android](https://github.com/home-assistant/android) | **Candidato, não conectado** | Apache-2.0. Preferir gateway Home Assistant com API autenticada, não implementar cada marca do zero. Não usar servidor no celular principal. |
| Localização familiar contínua | [OwnTracks Android](https://github.com/owntracks/android) | **Estudo, não integrado** | Licença EPL-1.0; sensível, exige opt-in individual, revogação, foreground service, retenção e backend com acesso protegido. Não copiar código sem revisão de licença. |
| Biblioteca menor de lembretes | `jakhapay/notification-reminder` | **Não adotar** | Biblioteca baseada em WorkManager, não melhora precisão; publicação de dependência ainda marcada como WIP. |

## Privacidade e qualidade

- Clima: usuário aciona consulta; Android fornece posição aproximada, arredondada a 3 casas decimais para Open-Meteo; nem posição nem histórico do tempo são gravados no Firestore.
- Lembretes: tarefa, responsável, horário e observação aparecem na bandeja de notificações; conteúdo sensível fica privado na tela bloqueada (`VISIBILITY_PRIVATE`). O canal anterior `personal` é preservado, mas o novo canal `central-tasks-v2` usa IMPORTANCE_HIGH; a pessoa pode alterar a importância nos ajustes do Android.
- Não prometer pontualidade absoluta sob Doze/economia de bateria; exibir diagnóstico e opção de autorização de alarme exato quando suportado.
- SOS **não é serviço de emergência certificado**. A tela hoje publica localização durante uso ativo; background real, precisão/frescura, expiração, consentimento e notificação remota segura ainda exigem um fluxo separado.
- Casa inteligente: ações físicas só após associação autenticada, estado vindo do dispositivo, confirmação de sucesso e auditoria. Não usar token em texto aberto, URL com token, Firestore compartilhado ou logs. Exigir TLS para acesso remoto.
- Separar aprovações de UX dos rascunhos; estas decisões técnicas não declaram novas telas visualmente aprovadas.

## Teste físico necessário

1. No S23 FE e S25 FE, autorizar notificações Android e verificar canal **Tarefas com detalhes**; agendar uma tarefa para alguns minutos à frente, deixar o app fechado e registrar horário real e título exibido.
2. Repetir com Android em modo de economia de bateria, Não Perturbe, reinicialização e alarme exato ligado/desligado.
3. Ativar tarefa diária/semanal e confirmar disparo da próxima ocorrência sem duplicar; concluir antecipadamente e confirmar ausência de aviso indevido naquele dia.
4. Abrir Hoje > previsão, autorizar localização e observar consulta; negar localização e verificar que o app não adivinha cidade.
5. Confirmar que notificação de SOS mantém nome/URL escondidos na tela bloqueada; validar no app desbloqueado, dois usuários e dois aparelhos.

## Fontes

- Android: https://developer.android.com/develop/background-work/services/alarms
- Android 14 exact alarms: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Open-Meteo: https://open-meteo.com/en/docs ; https://github.com/open-meteo/open-meteo
- Home Assistant: https://github.com/home-assistant/core ; https://github.com/home-assistant/android
- OwnTracks: https://github.com/owntracks/android
