# Arquitetura e contratos

Um módulo Gradle funcional. `domain` contém moeda, quantidades, listas e recorrência, sem Android/Firebase. `data/CentralRepository` concentra JSON local, Auth, snapshots, convites e transações. `CentralViewModel` controla operações e erros. UI Compose depende do repositório via ViewModel, sem chamada direta ao Firestore. `Reminders` usa WorkManager.

## Persistência e permissões

- users/{uid}: displayName e householdId; leitura individual. householdId só aponta a um espaço em que o usuário efetivamente participa.
- users/{uid}/records/{id}: registros privados, inacessíveis ao parceiro.
- households/{hid}: creatorUid, members (até dois), inviteId, createdAt.
- households/{hid}/records/{id}: registros explicitamente compartilhados. Saúde não pode ser gravada nessa coleção.
- households/{hid}/baskets/{Mercado|Açougue}: sessão atual, geração UUID, revisão crescente, orçamento em centavos, lista de itens.
- invites/{random128bits}: e-mail destinatário, validade, autor, espaço, usuário que aceitou. Listagem negada.

Registros: kind, private, campos próprios, updatedBy, updatedAt. kind distingue task, occurrence, plan, contribution, receipt, moment, transaction, health, quest.

O servidor verifica membros em cada leitura/escrita. Alterar ponteiro de perfil não concede acesso. Sair remove o próprio uid e limpa o perfil no mesmo batch. Convite exige e-mail verificado e mutações de convite/espaço/perfil no mesmo commit.

## Integridade

Dinheiro Long em centavos; quantidade Long em milésimos. BigDecimal e HALF_UP para subtotal. Diferença de preço calculada na quantidade real; diferença de quantidade calculada ao preço previsto. Unidades explícitas evitam confusão kg/g. Até 200 itens por sessão.

Edição de compras usa transação e revisão esperada; Firestore rejeita transações offline. Não há confirmação final offline. Fechamento usa geração como idempotency key, preserva snapshot e abre geração nova no mesmo commit. Repetição após confirmação não cria recibo duplicado. Regras vedam update/delete de recibos e contribuições. Preço real do último fechamento vira estimativa reutilizável, sem alterar recibos anteriores.

Contribuições usam IDs estáveis, são append-only e a soma é derivada dos registros. Aceites/realizações de missões usam transações e regras que permitem adicionar somente o uid autenticado. A carta não pode ser trocada após proposta.

Dados locais usam commit de SharedPreferences; falha de disco não é tratada como sucesso. Os espaços local e cloud são separados. Firebase usa cache nativo, com estado de cache/pendência/servidor visível. Logout limpa estado em memória e cancela lembretes.

## Limites conhecidos

Sem backend privilegiado nesta versão; dados digitados são afirmações dos membros. As regras validam acesso e invariantes transacionais, mas não recalculam no servidor a soma arbitrária de todos os itens. A UI/motor de domínio verificam totais; backend de fechamento deve integrar o gate 1.0. CRUD de tarefas/metas usa resolução Firestore por último write para campos; edição colaborativa desses campos ainda requer UX de conflito por versão.

Cache/exportação não equivalem a backup completo de servidor. Lembretes dependem de WorkManager/bateria e não são alarmes de emergência. Não há armazenamento de segredos de IA, service account ou credenciais administrativas no APK. A configuração cliente Firebase do projeto registrado é pública por desenho e não substitui as regras.
