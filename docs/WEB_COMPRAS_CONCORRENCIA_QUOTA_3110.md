# Central do Casal Web: compras simultâneas, consumo e recuperação offline

## Objetivo e princípios
A compra compartilhada precisa funcionar em dois celulares e no navegador ao mesmo tempo sem perder alterações. Uma falha de internet ou cota **não pode ser interpretada como gravação concluída**. Dados reais da família e backups não pertencem ao repositório público.

## Auditoria antes da alteração
- `web/shopping.js` rejeitava **toda** transação quando a revisão global da cesta mudava, mesmo se outra pessoa editasse produto diferente. Essa decisão gerava conflito artificial.
- A callback de transações podia criar novos UUIDs após retentativas, e o backup de contingência podia usar um UUID diferente da tentativa remota.
- `replayEmergency` bloqueava uma alteração a um produto se qualquer campo dele mudasse e não protegia explícita e independentemente o orçamento contra mudanças concorrentes.
- `web/main.js` assinava toda a coleção de registros históricos e os vínculos de catálogo na abertura da casa, mesmo em uma sessão iniciada diretamente no modo mercado.
- O recebimento de snapshots de uma coleção **não é** um contador de custo. As regras de segurança podem gerar leituras dependentes; reconexões também podem gerar novas cobranças.

## Alterações desta branch (PR #101)
1. `basket-concurrency.js`: atualização por produto e campo, protegendo conflitos apenas quando dois aparelhos alteram o mesmo valor de forma incompatível. Orçamento e remoção também são protegidos.
2. `shopping.js`: transação usa geração da compra como barreira, relê a versão do servidor, aplica mudanças sobre a versão mais nova e não escreve se for no-op. IDs são estáveis durante retentativa.
3. `shopping-ui.js`: operação remota e operação de contingência compartilham o mesmo ID. O editor conserva o snapshot mostrado ao usuário durante todo o diálogo.
4. `emergency-cart.js`: recuperação reconstrói o estado via journal, valida-o frente ao backup e faz rebase entre a versão original, a versão local e a versão remota. Erros `resource-exhausted`, `unavailable` e `deadline-exceeded` podem preservar operações localmente; `permission-denied` continua bloqueado.
5. `main.js`: a cesta continua com atualização ao vivo, mas a assinatura de registros históricos e vínculos de catálogo é adiada quando a pessoa entra **diretamente** no modo mercado. Após a primeira abertura de Histórico/Catálogo, os listeners existentes continuam durante a sessão, para não fazer reconexões repetidas.
6. Testes Web via `npm run test:web`: concorrência, orçamento, recuperação offline, IDs repetidos, quota e política de escuta. Um teste usa fixture anônima com 56 registros e 28 operações, sem dados reais.

## Limitações e riscos não escondidos
- Os dois aparelhos ainda atualizam **um documento de cesta por mercado**. Se houver muitas gravações simultâneas, o Firestore poderá repetir transações, cobrar leituras, ou responder `ABORTED`. A versão estrutural com itens em subcoleção exige plano de migração compatível com o Android, regras e histórico.
- Não se sabe quantas leituras/gravações foram faturadas no incidente de 31/10; é preciso olhar `Firebase Console → Firestore → Usage` e métricas/alertas em Google Cloud, com o projeto real.
- Os listeners de histórico ainda são completos quando necessários. A optimização implementada evita carregá-los em algumas rotas, não resolve todos os casos de quota.
- Não há garantias de sincronização offline em paralelo entre dispositivos sem o backend disponível. Cada aparelho conserva seus dados locais e um deve reconciliá-los quando a rede voltar.
- O rebase recusa alterações sobre o **mesmo campo** e mudanças incompatíveis de orçamento, mantendo o backup. Quando os dois aparelhos editam campos independentes, preserva ambos.
- **Nenhum deploy nem alteração no Firebase de produção.** Código preparado para QA, não prova dois dispositivos de verdade.

## Evidência histórica e recuperação
Uma análise local do backup fornecido pelo usuário, exportado em 01/11, verificou que 26 operações de atualização e duas adições reproduziram o estado final de 58 itens a partir de uma base de 56. Nenhum backup, ID de conta, nome, CNPJ, nota ou dado pessoal foi commitado. Essa verificação não prova que o estado **atual** do Firebase ainda possui a mesma geração: verificar com uma conta autorizada e fazer prévia de conflitos antes de sincronizar.

## Plano de QA e aceite
1. Acessar a mesma casa em dois celulares com usuários diferentes e comprar dois itens distintos simultaneamente: ambos aparecem na cesta sem erros de revisão global e sem perda.
2. Alterar **o mesmo campo** do mesmo produto nos dois aparelhos: mostrar conflito e nunca sobrescrever silenciosamente. Campos diferentes do mesmo produto podem coexistir.
3. Forçar offline/cota no aparelho A, atualizar outro item no aparelho B, recuperar conexão e sincronizar o journal do A: ambos os itens devem ser preservados. Em caso de conflito, o backup continua intacto.
4. Reenviar o mesmo journal duas vezes: não criar registros ou escritas duplicados.
5. Tentar finalizar enquanto o segundo aparelho alterou a cesta: exigir atualização/revisão, não fechar às cegas.
6. Medir leituras/escritas no Firebase Console antes/depois, usando mesmo número de registros, tempo de sessão e aparelhos. Verificar métricas de leitura das regras de segurança e reconexões.
7. Validar Mercado e Açougue, histórico, nota e versão Android, pois o esquema Firestore compartilhado foi mantido.
8. Revisar PR e executar deploy somente após liberação explícita.

## Fontes oficiais Firebase
- https://firebase.google.com/docs/firestore/pricing
- https://firebase.google.com/docs/firestore/transaction-data-contention
- https://firebase.google.com/docs/firestore/manage-data/transactions
- https://firebase.google.com/docs/firestore/best-practices
