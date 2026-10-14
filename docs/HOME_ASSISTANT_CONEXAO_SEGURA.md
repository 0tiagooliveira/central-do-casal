# Nossa Casa: integração incremental com Home Assistant

## Entregue nesta etapa
- Componente na tela Nossa Casa para **consulta real** de entidades existentes em uma instância do Home Assistant.
- API oficial `GET /api/states`, com `Authorization: Bearer` somente na requisição HTTPS.
- Aceita somente URL HTTPS base, sem credenciais no URL, sem parâmetros, sem redirecionamento.
- Token digitado no aparelho, não salvo no app, nos dados do casal, Firebase ou GitHub.
- Só mostra luz, tomada/switch, ventilador, climatização e mídia, sem pessoa, rastreador, câmera ou localização.
- Tempo limite, limite de tamanho de resposta e limite de entidades na apresentação.
- **Nenhum comando físico**, agendamento ou automação é executado neste estágio.

## Limitações explícitas
- Exige que o casal tenha uma instância Home Assistant acessível por HTTPS e um token autorizado da própria instância.
- Tokens long-lived podem ter amplos privilégios. Esta prova de conceito **não** substitui um fluxo de OAuth dedicado nem deve ser usada para publicar credenciais.
- Pegar estados atuais não significa que estão sincronizados continuamente; há somente uma consulta manual.
- A Central do Casal não controla Alexa diretamente, nem importa pareamentos genéricos como se fossem dispositivos Home Assistant.
- Para um modo permanente: OAuth (Home Assistant auth API), armazenamento protegido pelo Android Keystore, expiração/revogação, reconexão e assinatura segura de chamadas; validar antes de liberar `POST /api/services/{domain}/{service}`.
- Não instalar Home Assistant Core ou servidor no celular do casal apenas para esta primeira integração.

## Aceite manual
1. Configure um Home Assistant do próprio casal atrás de TLS.
2. Na tela Nossa Casa, use URL HTTPS e token autorizado; toque Consultar estados reais.
3. Verifique se as luzes/tomadas são exatamente as registradas na instância e se não houve nenhuma ação física.
4. Teste negar autorização, URL HTTP, certificado inválido, rede offline e uma URL externa que redirecione.
5. Reabra a tela/aplicativo e confirme que não há token salvo. Confira também Firestore, logs e repositório.

## Alternativas open source pesquisadas
- Home Assistant Core e Companion Android (Apache 2.0): plataforma madura recomendada como gateway; não precisamos reimplementar os protocolos de cada fabricante.
- OwnTracks Android (EPL-1.0): referência técnica para localização familiar, **não incorporado**. Nosso SOS deve permanecer voluntário, temporário e protegido.
- ntfy/UnifiedPush: alternativa de infraestrutura de push, mas adiciona distribuidor/servidor; preferir validar FCM autenticado antes de trocar a entrega de alertas.

Fontes:
- https://developers.home-assistant.io/docs/api/rest/
- https://developers.home-assistant.io/docs/auth_api/
- https://github.com/home-assistant/android
- https://github.com/home-assistant/core
- https://github.com/owntracks/android
- https://unifiedpush.org/users/distributors/ntfy/
