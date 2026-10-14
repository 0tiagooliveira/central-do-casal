# SOS e localização em tempo real: QA obrigatório

> **Estado:** EXPERIMENTAL, não validado em aparelhos físicos. Não substitui serviços de emergência. As regras Firestore nesta branch ainda não estão publicadas em produção.

## Comportamento contratado
- Só uma conta autenticada pertencente a household com **dois membros** pode iniciar uma sessão.
- Cada pessoa decide por si quando compartilhar a própria localização.
- O Android solicita localização enquanto usa o app; não solicitamos `ACCESS_BACKGROUND_LOCATION`.
- O serviço do Android é do tipo `location`, com notificação persistente e ação **Parar**.
- O serviço solicita atualizações aproximadamente a cada 15 s; isso não é garantia de frequência.
- A sessão dura no máximo 30 min e **não** reinicia automaticamente após encerramento ou morte do processo.
- O documento de posição tem expiração lógica curta. O aplicativo não exibe cache como localização atual.
- Apenas o próprio UID escreve/exclui a própria sessão; somente membros do household podem ler.
- Não há carregamento automático de OpenStreetMap/Google Maps: abrir mapa externo exige toque.
- SOS pode registrar pedido de ajuda **sem GPS**; ligação e WhatsApp precisam de toque independente.

## Matriz de verificação física (Tiago S23 FE e Rhayra S25 FE)
- [ ] Ambos autenticar com contas separadas e confirmar household.
- [ ] B negar a localização; A não deve conseguir iniciar sessão no aparelho de B.
- [ ] A permitir localização e iniciar voluntariamente; notificação persistente surge no Android.
- [ ] B abrir SOS/localização e ver apenas localização recente de A.
- [ ] Confirmar que C (conta de fora do household) não lê o documento.
- [ ] A colocar app em segundo plano e bloquear a tela por alguns minutos; B verificar se posições continuam chegando quando Android permite.
- [ ] A tocar **Parar** na notificação, B não deve mais ver posição como ativa.
- [ ] A iniciar nova sessão, desligar internet; B não deve considerar uma posição expirada como atual.
- [ ] A religar internet; confirmar recomeço só por comando explícito.
- [ ] A encerrar sessão pelo aplicativo, conferir desaparecimento/expiração em B.
- [ ] A trocar conta/sair do app: nenhuma atualização de localização de conta anterior deve continuar.
- [ ] B retirar A do household: acesso compartilhado deve ser recusado.
- [ ] Confirmar limite de 30 minutos e que não há reinício automático.
- [ ] Confirmar tela de bloqueio não expõe nome/coordenadas/URL do parceiro.
- [ ] Sem permissão de localização, SOS ainda tenta registrar alerta sem mapa.
- [ ] Testar Samsung em modo de economia de bateria, localização desativada e notificações bloqueadas.
- [ ] Testar botão 190: deve abrir apenas o discador; jamais ligar automaticamente.

## Pendências até produção
1. Publicar `firestore/firestore.rules` após autorização e evidência CI do commit correto.
2. Configurar política administrativa de TTL/deleção física para `expiresAt`, quando suportada; a expiração lógica da UI **não** substitui retenção do backend.
3. Validar comportamento de foreground service em Android 15/16, restrições Samsung, permissões e conectividade.
4. Validar envio e recebimento de notificações remotas para SOS; atualização Firestore não garante despertar o aparelho.
5. Testes instrumentados/QA real, incluindo casos de perda de conta, processo e conexão.
6. Não anunciar rastreamento contínuo garantido nem resposta de emergência assegurada.
