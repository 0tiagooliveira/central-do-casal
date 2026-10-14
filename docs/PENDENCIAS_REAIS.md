# Pendências reais — estado atual 0.3.22-dev

## Validação e distribuição

- A versão de código é `0.3.22-dev` (`versionCode 30`). O [CI do PR #8](https://github.com/0tiagooliveira/central-do-casal/actions/runs/38557432319) concluiu com sucesso build, testes Android/JVM e **39/39 testes Firestore Emulator**. Isso **não comprova deploy** das regras no Firebase de produção nem QA nos aparelhos. `VALIDACAO.md` descreve a antiga versão 0.2.0; consulte `docs/ESTADO_ATUAL.md` para o estado vigente.
- Instalação física, sincronização em dois aparelhos/duas contas, concorrência offline, reconexão, reinício/bateria, teclado, fontes grandes e TalkBack: ainda não certificados.
- Sons, vibração e notificações: implementação do sistema e testes manuais disponíveis; comportamento físico pendente de verificação.
- Assinatura de produção e Play Store: não configuradas. Distribuição de desenvolvimento.
- ZIP fonte público: lista permitida e revisão de conteúdo; configuração cliente não substitui configuração legítima do projeto de destino.

## Produto e serviços

- Modo Mercado, orçamento/carrinho/fechamento, painel anual de documentos, comparação do mesmo SKU/unidade, links explícitos Planos → Compras/Rotina e confirmação humana estão implementados. Servidor para recalcular fechamento, retificação/reabertura, favoritos e fila offline colaborativa continuam pendentes.
- Painel anual não é conciliação financeira ou painel bancário. Orçamentos financeiros completos, planejamento integral e importador genérico no celular continuam pendentes.
- Seleção semanal e eventos reais de cuidados (Lucca/Mel) implementados; calendário mensal completo, prioridade/esforço, soneca e FCM remoto incompletos. WorkManager não garante hora exata.
- Três destinos de Jornada e quatro cartões iniciais implementados. Mapa integral, álbum sincronizado, cápsulas com duplo consentimento, selos e arquivo integral de memórias incompletos.
- Login Google, exclusão completa de conta, política de retenção, App Check, Crashlytics, rate limits, backend privilegiado, auditoria e migrações não concluídos.
- Sophia/backend/voz, Alexa/Avant, Poup+, Notion, calendários/clima externos e hardware doméstico dependem de serviços legítimos não integrados. Nenhuma proposta importada os ativa automaticamente.
- SOS e compartilhamento de localização têm implementação experimental: alerta Firestore, localização enquanto o app permanece aberto, WhatsApp e discador em ações explícitas. Ainda faltam validação em dois aparelhos, retenção/expiração dos registros e robustez de entrega. Volta Segura com sessão temporária completa ainda não está implementada. Não usar o app como substituto de serviços de emergência.

## Ferramentas

OMX oficial executado para diagnóstico, AST e estado/backlog. Especialistas são da colaboração nativa Codex; não houve workers OMX/tmux. CRG construiu e consultou grafo real, com limites de descoberta incremental sem Git e impacto Kotlin. Consulte `ORQUESTRACAO.md`.

Estas pendências impedem classificar a prévia como release integral 1.0.

