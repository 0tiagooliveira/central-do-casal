# Testes e gates

1. Domínio JVM: centavos exatos, vírgula brasileira, peso fracionado, kg/g, arredondamento, rejeição de valores inválidos, variações de preço/quantidade separadas, orçamentos separados, fechamento sem preços recusado, recorrência e Sossego.
2. Android/JVM Robolectric (API 35): persistência após nova instância, fechamento idempotente com preservação do orçamento histórico e ausência de lançamento financeiro, revisão desatualizada rejeitada. Testes não equivalem a dispositivo físico.
3. Firebase Emulator Suite (projeto demo): membros/estranhos, usuário anônimo, saúde privada, autores falsos, perfil forjado, limite de participantes, saída/revogação, recibos e contribuições imutáveis, revisão de sessão, aceites individuais, dois consentimentos, convite válido/expirado/usuário errado/e-mail não confirmado/claim parcial, snapshot e reset atômicos.
4. Build Gradle: assembleDebug deve ser comprovado com artefato e checksum. Assinatura APK validada com apksigner. Não chamar código fonte de APK.
5. Gate físico pendente: instalação S23 FE/S25 FE, duas contas reais, sessão compartilhada com alterações concorrentes, modo avião/reconexão, recusa de notificações, reinício/bateria, TalkBack, fontes ampliadas, privacidade no bloqueio, logout e remoção.
6. Gate condicionado: cada serviço externo necessita chamada legítima e recibo/estado real. Hardware e SOS requerem validação própria. Não ativar emergência para teste demonstrativo.

Resultados efetivos são registrados em `VALIDACAO.md`. CI fornecido deve ser executado no repositório futuro; não há execução remota presumida.

## Cobertura da prévia 0.1.3

- Comparação de preços: mesmo SKU e unidade, ordem de datas, observações inválidas, preço inicial zero e precisão decimal; não combinar unidades diferentes.
- Lista/carrinho: quantidade e preço previstos/reais, rascunho a confirmar, orçamento, fechamento idempotente e itens pendentes preservados.
- CatalogLinks: confirmação/rejeição separadas das propostas originais, escopo e autorização do espaço, atualização concorrente e ausência de associação automática.
- Ano de compras: cobertura parcial, mês sem documento não vira zero, previsto/manual fora do anual, filtro e seleção correspondem aos documentos.
- Experiência: sons opt-in, vibração do sistema, notificação manual privada e permissões recusadas. Testes JVM não comprovam entrega física.
- UI: quatro cartões iniciais, seleção da semana e três destinos da Jornada sem confirmação por terceiros.

O agente principal confirmou 110 testes sem falhas na versão atual; os resultados e limites estão em `VALIDACAO.md`. Esta lista descreve a cobertura planejada e não certifica testes físicos.
