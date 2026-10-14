# Casa Inteligente: arquitetura de integrações reais

## Fonte de verdade
O cadastro `smartDevice` é um inventário da casa, não uma prova de que um equipamento está on-line ou que a Central o controla. O painel deve mostrar **desconectado / cadastrado / pareado / controle verificado** conforme evidência real. Não fabricar estado ligado/desligado.

## Camadas
1. **Inventário**: nome, tipo de aparelho, cômodo, provedor, ID estável e configurações não sensíveis.
2. **Conectores**: um adaptador por provedor com capacidades verificadas, autenticação explícita e revogação.
3. **Estado**: leitura de estado com `observedAt`, indicador de origem e prazo de validade; cache não é observação em tempo real.
4. **Comandos**: enviados por toque explícito, com resultado confirmado pelo provedor. Timeout/falha não vira sucesso.
5. **Cenas**: regras entre dispositivos somente depois que cada adaptador estiver validado, com opção de desfazer quando suportada.

## Ordem de conectores
- **Home Assistant**: candidato a ponte integradora, via API documentada sobre HTTPS e autorização individual. Ler `light` / `switch` e depois alternar sob comando expresso. Nunca armazenar bearer tokens no Firestore/GitHub; proteger credenciais no Android Keystore ou em backend que forneça cofres de segredos.
- **Alexa**: por enquanto abrir app oficial. Parear Bluetooth não concede controle dos dispositivos Alexa. Controle direto depende de API/skill e permissão reconhecidas, não de automação de interface.
- **Smart Life / Tuya**: somente integração documentada e com tokens autorizados. Não capturar credenciais da conta por automação.
- **Avant NEO**: pesquisar modelo, firmware e API oficial antes de prometer comandos.
- **Bluetooth**: exibir dispositivos já pareados apenas como inventário. Nenhum GATT ou comando genérico sem protocolo do equipamento.
- **TVs e Xbox 360**: apenas inventário até confirmar protocolos de rede suportados; não assumir controle remoto universal.

## Regras de segurança
- Acesso compartilhado do inventário segue o household. Preferências/credenciais pessoais permanecem privadas ao dono.
- Ações em iluminação e tomadas precisam ser solicitadas diretamente pelo usuário e confirmadas pelo dispositivo.
- Fechaduras, câmeras, alarmes, aquecimento, portões e outros dispositivos sensíveis não entram no controle genérico. Requerem UX de autorização e critérios extras.
- Sem execução de comandos enquanto offline; agendamentos futuros só depois de comprovada idempotência, confiabilidade e cancelamento.
- Nunca enviar token, senha, fotos da casa, coordenadas GPS ou configuração de rede para logs.
- O usuário pode revogar uma integração e limpar a credencial; inventário não pode fingir continuar conectado.
- Para dispositivos LAN, app precisa estar na rede correspondente ou acessar uma ponte autenticada com TLS. Não utilizar o celular POCO como servidor sem necessidade comprovada.

## Entrega iterativa
- [x] Inventário por cômodo/tipo, status honesto de pareamento e provedor (PR #12).
- [ ] Escolha explícita do provedor e demonstração de conectividade em dispositivo real.
- [ ] Conexão Home Assistant sob autorização de cada usuário e armazenamento de credencial seguro.
- [ ] Leitura de estados com horário de observação e status indisponível em erro.
- [ ] Controle de luz/tomada, botão explícito, resultado confirmado e auditoria.
- [ ] Cenas e automações residenciais com consentimento dos participantes.
- [ ] Teste em S23 FE e S25 FE, Wi-Fi e rede móvel, expiração/revogação e falha de internet.
