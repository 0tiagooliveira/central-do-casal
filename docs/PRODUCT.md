# Central do Casal — implementação 0.1.3-preview

Android nativo Kotlin/Compose/Material 3. Cinco abas: Hoje, Compras, Rotina, Planos e Nós. Finanças, Saúde e Configurações têm retorno consistente. O modo local é persistente e pessoal neste aparelho; não simula outra pessoa nem importa silenciosamente dados Firebase. Contas cloud são individuais, com até duas pessoas por espaço e caminhos privados separados.

## Fluxos implementados

1. Entrada: e-mail/senha, cadastro, verificação e recuperação; alternativa local. Erros não equivalem a autenticação.
2. Casal: convite com e-mail confirmado e validade, aceite atômico e saída que remove acesso. Aceites são individuais.
3. Compras: Planejar, Modo Mercado e Carrinho; Mercado/Açougue; orçamento; ficha com preços e quantidades; fechamento transacional confirmado e recibo imutável. Não lança despesas automaticamente.
4. Catálogo: produtos/base e notas; revisão de 80 sugestões de associação preparadas na entrega. Confirmar/rejeitar cria catalogLinks próprio; originais preservados. Não é IA contínua.
5. Ano de compras: documentos, cobertura parcial, escolha de ano/mês/categoria e barras selecionáveis. Meses desconhecidos não são zero; contas previstas e lançamentos manuais ficam fora do anual.
6. Preços: histórico do mesmo SKU e unidade; diferença/tendência por datas; não mistura unidades nem produtos distintos.
7. Rotina: semana selecionável, recorrências, ocorrência por tarefa/data e desfazer. Importados em revisão ficam inativos; lembretes exigem confirmação.
8. Planos: projetos, prazo/horizonte e contribuições append-only. Finanças registra receitas/despesas manuais com privacidade por padrão; não é saldo bancário.
9. Nós: momentos e três destinos de Jornada; duas confirmações próprias dão origem a memória. O início apresenta quatro cartões de navegação.
10. Experiência: desenho Strong, feedback do sistema, sons opcionais inicialmente desativados e testes manuais de vibração/som/notificação privada.

## Limites

Build, 110 testes e assinatura atuais foram confirmados pelo agente principal e registrados em VALIDACAO.md. O APK foi entregue no Drive; o ZIP de fonte sanitizado aguarda aprovação de envio. Validação física e sincronização em dois aparelhos continuam pendentes. Serviços externos de IA/voz, bancos, Notion, hardware, SOS e rastreamento não estão operacionais. Propostas de automação não executam ações. Não equivale à versão integral 1.0.
