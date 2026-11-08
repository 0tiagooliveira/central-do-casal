# Auditoria de 31/10: duas contas, cota Firestore e backup

## Escopo e evidência disponível

Em 31/10/2026 houve relatos de `resource-exhausted / quota exceeded`, falta de sincronização da compra compartilhada, finalização bloqueada, operações locais pendentes e divergências entre carrinho e nota.

A conexão atual não oferece leitura autenticada das métricas Cloud Monitoring e nem da cesta de produção. **Não foi possível confirmar o número real de leituras, gravações ou o motivo exato de `resource-exhausted`.** Não confundir cota gratuita teórica, estimativa do console ou contagem de mensagens recebidas no navegador com faturamento medido.

O plugin Firebase pode ser conectado pelo titular do projeto. A análise da nuvem exige credenciais e permissão `monitoring.timeSeries.list`, em modo somente leitura. Não gravar credenciais ou tokens no repositório.

## Como auditar o dia exato sem alterar dados

1. Abrir https://console.firebase.google.com/project/central-do-casal/firestore e selecionar **Firestore → Uso**, com data 31/10/2026. Capturar leituras, gravações, exclusões e os picos por horário. Anotar timezone do painel; a cota gratuita reseta perto de meia-noite **Pacific Time**, não no fuso de Brasília.
2. Verificar no Google Cloud Console: Firestore → database → Usage ou Cloud Monitoring. Comparar **Document Reads**, **Document Writes**, **Active Connections**, **Snapshot Listeners** e status de chamadas. Analisar períodos próximos ao horário da compra.
3. Em Firestore → Regras → monitoramento, verificar solicitações negadas, erros de avaliação e erros na época. Correlacionar com `resource-exhausted` observado pelo aplicativo. O erro isolado não prova qual quota foi atingida.
4. Se houver relatório de billing, usar seus valores para verificar consumo faturado; o painel de uso oferece estimativas e pode omitir operações faturadas.
5. No mesmo tempo de sessão e com o mesmo número de itens, comparar medições antes/depois da implementação; diferenciar app Web, Android, reconexões e outros usuários do mesmo projeto.

### Dados solicitados para concluir a auditoria

| Métrica | 31/10/2026 | Fonte | Status |
| --- | --- | --- | --- |
| Leituras de documentos | Não disponível | Firebase / Cloud Monitoring | Bloqueado por acesso |
| Escritas de documentos | Não disponível | Firebase / Cloud Monitoring | Bloqueado por acesso |
| Conexões e listeners | Não disponível | Cloud Monitoring | Bloqueado por acesso |
| Erros `resource-exhausted` e denied | Relatados pelo usuário, contagem indisponível | Logs / Regras | Parcial |
| Limite efetivamente atingido | Não determinado | Usage + billing / quota | Pendente |

## O que foi alterado na versão web (PR #101)

- Carrinho `baskets/Mercado` / `baskets/Açougue` continua em tempo real; o layout do documento foi preservado para compatibilidade com Android.
- Registros pessoais, registros compartilhados e vínculos de catálogo só assinam suas coleções quando necessários ao fluxo. Caso a pessoa entre diretamente em **Compras → No mercado**, o aplicativo não assina toda a coleção histórica por antecipação.
- Transações tratam edição de produtos diferentes sem rejeitar a compra inteira por revisão diferente; conflitos no mesmo campo continuam bloqueados.
- O ID da operação é gerado antes da transação e reaproveitado na contingência offline.
- Regras distinguem criação e atualização, mantendo revisão sequencial para proteger contra sobrescrita.
- Recuperação excepcional de `permission-denied` só ocorre se a leitura explícita de servidor demonstrar que **a revisão da mesma geração avançou**. Erros de permissões autênticos ou geração nova não são retentados. Teto de duas tentativas adicionais. Cada conferência excepcional pode gerar leituras cobradas.
- O painel **Diagnóstico de sincronização** mede eventos locais, não unidades faturadas; não executa consultas extras.
- O botão **Conferir backup com servidor · somente leitura** faz `getDocFromServer()` sob ação explícita do usuário (pode custar leituras), compara em memória e não grava, fecha ou restaura dados.

## Backup exportado em 01/11

Analisado **somente localmente** em memória, sem incluir o arquivo real no GitHub: 56 itens iniciais, 26 atualizações, 2 inclusões, 58 itens finais e 32 marcados como comprados. Os valores monetários reais não foram colocados no repositório. Reprodução exata, sem IDs duplicados. A comparação com o estado real do servidor continua pendente por falta de acesso autenticado. Não interpretar a reprodução como sincronização confirmada.

## QA seguro para duas contas

1. No Firestore Emulator, preparar uma casa sintética com membros Alice e Bob e duas listas separadas, nunca contas ou dados reais.
2. Abrir duas instâncias autenticadas e simular duas transações **verdadeiramente sobrepostas** em itens diferentes do mesmo mercado. Confirmar que ambas são gravadas uma única vez, com revisão final +2 e mesmos totais para os dois membros.
3. Alterar o mesmo campo de um produto simultaneamente: o conflito deve bloquear a sobrescrita, não duplicar o item.
4. Desligar a rede em A, editar item independente em B, reconectar A e sincronizar: preservar as duas alterações; se houver conflito, guardar backup e bloquear.
5. Encerrar enquanto o outro dispositivo edita: exigir revisão do estado antes de confirmar. Verificar histórico e notas sem duplicação.
6. Repetir com dispositivos físicos e contas reais de teste **somente após aprovação de ambiente e publicação de QA**.

**Gate:** teste automatizado emulado não substitui homologação em dois celulares físicos. Nenhum deploy do PR sem liberação explícita.

## Documentação oficial
- https://firebase.google.com/docs/firestore/monitor-usage
- https://firebase.google.com/docs/firestore/pricing
- https://firebase.google.com/docs/firestore/manage-data/transactions
- https://firebase.google.com/docs/firestore/transaction-data-contention
