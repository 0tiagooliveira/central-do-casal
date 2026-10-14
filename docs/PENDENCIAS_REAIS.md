# Pendências reais — 0.1.3-preview

## Validação e distribuição

- Build, 110 testes, assinatura e checksum da 0.1.3 foram confirmados pelo agente principal em `VALIDACAO.md`. O APK foi entregue no Drive com metadados verificados; o ZIP de fonte sanitizado está pronto localmente e aguarda aprovação de envio.
- Instalação física, sincronização em dois aparelhos/duas contas, concorrência offline, reconexão, reinício/bateria, teclado, fontes grandes e TalkBack: ainda não certificados.
- Sons, vibração e notificações: implementação do sistema e testes manuais disponíveis; comportamento físico pendente de verificação.
- Assinatura de produção e Play Store: não configuradas. Distribuição de desenvolvimento.
- ZIP fonte público: lista permitida e revisão de conteúdo; configuração cliente não substitui configuração legítima do projeto de destino.

## Produto e serviços

- Modo Mercado, orçamento/carrinho/fechamento, painel anual de documentos, comparação do mesmo SKU/unidade e confirmação humana estão implementados. Servidor para recalcular fechamento, retificação/reabertura, favoritos e fila offline colaborativa continuam pendentes.
- Painel anual não é conciliação financeira ou painel bancário. Orçamentos financeiros completos, planejamento integral e importador genérico no celular continuam pendentes.
- Seleção semanal implementada; calendário mensal completo, prioridade/esforço, soneca e FCM remoto incompletos. WorkManager não garante hora exata.
- Três destinos de Jornada e quatro cartões iniciais implementados. Mapa integral, álbum sincronizado, cápsulas com duplo consentimento, selos e arquivo integral de memórias incompletos.
- Login Google, exclusão completa de conta, política de retenção, App Check, Crashlytics, rate limits, backend privilegiado, auditoria e migrações não concluídos.
- Sophia/backend/voz, Alexa/Avant, Poup+, Notion, calendários/clima externos e hardware doméstico dependem de serviços legítimos não integrados. Nenhuma proposta importada os ativa automaticamente.
- SOS, Volta Segura e rastreamento não implementados. Não usar como serviço de emergência.

## Ferramentas

OMX oficial executado para diagnóstico, AST e estado/backlog. Especialistas são da colaboração nativa Codex; não houve workers OMX/tmux. CRG construiu e consultou grafo real, com limites de descoberta incremental sem Git e impacto Kotlin. Consulte `ORQUESTRACAO.md`.

Estas pendências impedem classificar a prévia como release integral 1.0.

