# Estado atual — Central do Casal

**Data de referência:** 14/10/2026  
**Fonte oficial do código:** branch `main` do repositório privado.  
**Versão do aplicativo:** `0.3.22-dev`, `versionCode 30` (definidos em `app/build.gradle.kts`).  
**Pacote DEV:** `com.centraldocasal.app.dev`. A build DEV pode coexistir com a versão sem sufixo, mas o certificado de assinatura de builds de CI diferentes pode mudar.

## O que existe no código

- **Identidade:** cadastro/login Firebase por e-mail/senha, contas pessoais e espaço compartilhado com convite; modo local separado da nuvem.
- **Hoje:** tarefas reais, compras pendentes, projetos, nossa jornada, atalhos e contagens de cuidados registradas no próprio dia, quando houver eventos.
- **Compras:** Mercado e Açougue, orçamento, lista, histórico, preços previstos/reais, fechamento e documentos importados.
- **Rotina Viva (inicial):** tarefas e recorrência; registros persistidos de fralda/banho do Lucca e ração da Mel, com autoria, horário, histórico por dia e desfazer pelo autor. Sem gamificação de cuidados.
- **Planos:** projetos, etapas e contribuições; etapa pode gerar item em Compras ou tarefa na Rotina por confirmação explícita, preservando `sourcePlanId + sourceStepId`; deduplicação entre Mercado/Açougue e na tarefa.
- **Nós:** momentos, jornadas e experiências, ainda sem todas as interações de UX aprovadas.
- **Segurança/Casa:** SOS e localização experimentais, ações externas explícitas, cadastro inicial de dispositivos; sem substituição de serviços de emergência.
- **Integrações futuras:** Sophia, Poup+, Alexa, Avant NEO, Notion e Open Finance **não estão operacionais**.

## Evidência automatizada

- PR [#6](https://github.com/0tiagooliveira/central-do-casal/pull/6) mesclado: links Planos → Compras/Rotina, testados.
- PR [#8](https://github.com/0tiagooliveira/central-do-casal/pull/8) mesclado: painel contextual de cuidados no Hoje.
- [CI do PR #8](https://github.com/0tiagooliveira/central-do-casal/actions/runs/38557432319): **sucesso** em build APK debug, testes Android/JVM e **39/39 testes Firestore no Emulator**.
- O CI valida a revisão do código; **não** demonstra que o Firebase de produção recebeu as regras, nem substitui teste físico de sincronização.
- `VALIDACAO.md` é um registro histórico da versão 0.2.0 e não certifica a 0.3.x.

## O que ainda impede uso confiável em dois celulares

1. Conferir/publicar no **projeto Firebase correto** as regras Firestore versionadas. O workflow de deploy requer credenciais WIF legítimas e acionamento manual autorizado; testes de Emulator não são deploy.
2. Instalar e testar duas contas distintas em dois aparelhos Samsung, ambos no mesmo household; verificar leitura/escrita, privacidade, reconexão e concorrência. Acompanhar o [roteiro de QA](https://github.com/0tiagooliveira/central-do-casal/issues/7).
3. Definir assinatura DEV **estável** para atualizações sucessivas sem reinstalar/perder dados locais. Não assumir compatibilidade dos certificados gerados automaticamente pelos runners.
4. Proteger a `main`, fechar política de retenção de SOS/localização e completar exclusão de conta, App Check e telemetria com consentimento.
5. Validar acessibilidade, fontes grandes, rolagem, animações e notificações em Android físico.

## Como desenvolver sem Codex e sem Windows

1. Alterações partem do GitHub em `feature/*`, `fix/*` ou `docs/*`, feitas pelo chat/GitHub autorizado.
2. CI verifica Android/JVM + Firestore Rules e só publica APK quando o conjunto está verde.
3. Pull Request e merge controlado para `main`; não fazer force-push.
4. APK do workflow pode ser obtido pelos artefatos do GitHub; instalação/QA físico ainda depende dos aparelhos.
5. Registre próximos pacotes no [roadmap vivo](https://github.com/0tiagooliveira/central-do-casal/issues/3).

**Importante:** execução de testes com Emulator, compilação e artefato de APK não tornam SOS, localização ou sincronização física certificados.
