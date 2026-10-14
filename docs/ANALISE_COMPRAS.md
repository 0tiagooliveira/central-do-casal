# Análise anual de compras

Este documento descreve o tratamento dos dados; não inclui dados pessoais, valores da família, nomes de estabelecimentos, identificadores de contas ou documentos fiscais. Pode acompanhar o código-fonte público.

## Origem e cobertura

A análise usa os registros autenticados de `documentedMonth`, suas categorias e as notas importadas. A fonte é um retrato dos documentos recebidos até uma data de corte, e não uma integração contínua com estabelecimentos ou bancos. A cobertura é incompleta, inclusive nos meses com documentos.

O painel deve apresentar os doze meses do ano. Valor mensal nulo significa desconhecido: mostrar “Sem documentos” ou “Futuro na fonte”, conforme a situação original. Não representar ausência como gasto zero. O mês correspondente à data de corte é parcial; não extrapolar o restante do mês silenciosamente. Meses classificados como futuros no arquivo conservam essa descrição de origem até receberem documentos novos.

## Totais, categorias e comparação

O total anual é a soma dos totais mensais conhecidos dos documentos; o rótulo deve dizer “Total documentado”. Ele não representa toda a despesa familiar, pagamentos confirmados nem saldo financeiro. Contas previstas, previsões de compra, aportes e lançamentos manuais ficam fora desse cálculo.

Categorias anuais agregam as categorias mensais em centavos inteiros. Para cada mês documentado, a soma das categorias precisa corresponder ao total documentado. Arredondar somente a apresentação; não transformar os valores em ponto flutuante para somar.

Participação percentual usa apenas o total documentado da seleção como denominador. As categorias podem conter rateios e classificações estimadas; a legenda deve informar isso. Nenhuma categoria sem evidência deve ser tratada como comprovação de pagamento.

Comparações exigem dois períodos com valores conhecidos. Evitar percentuais para base zero ou desconhecida. Se houver média, explicitar que considera apenas meses com documentos, sem preencher lacunas com zeros. Comparar mês parcial com mês inteiro requer aviso de cobertura.

## Notas, lojas e preços

Preservar identidade do estabelecimento e do documento, sem agrupar lojas por semelhança textual. Não duplicar uma mesma nota por aparecer em arquivos diferentes. Documentos sem detalhamento conservam o total e o aviso; não inventar produtos para preencher a lista.

O desconto total da nota é documental. O desconto de cada item e seu valor líquido rateado são estimativas; não subtrair esse desconto novamente do total líquido da nota.

Quantidades e preços unitários impressos podem ter mais de duas casas decimais. Preservar os textos decimais originais, unidade e precisão; comparação de preços deve considerar unidade, produto e estabelecimento compatíveis. Vínculos ambíguos e classificações sob revisão permanecem identificados, sem fusão automática de produtos.

Notas fiscais dos modelos 55 e 65 documentam a aquisição informada. Não representam, por si, pagamento conciliado. O histórico importado não cria uma segunda despesa financeira.

## Interatividade e privacidade

Filtros de ano, mês, categoria e estabelecimento devem mudar a seleção de documentos exibida, com identificação clara do período e estado vazio compreensível. Abrir uma nota deve mostrar o documento correspondente, sem alterar seu conteúdo. Contagens e valores visíveis derivam da seleção real; botões decorativos não substituem ações.

A análise anual considera somente documentos disponíveis à conta autenticada. Não buscar arquivos administrativos nem embutir a base pessoal no APK ou no código-fonte publicado. Ocorrências de tarefas mantêm a mesma privacidade da tarefa; navegar entre dias não altera escopo nem confirma atividades automaticamente. Revisão de rotina e agendamento de lembretes continuam ações explícitas e independentes do painel de compras.

## Verificação recomendada

Usar exemplos fictícios para verificar: meses ausentes e futuros continuam nulos; totais mensais conciliam com categorias; previsões e contas planejadas não entram no anual; descontos não são aplicados duas vezes; preços com precisão extensa permanecem íntegros; filtro e detalhe apontam para o registro correto; tarefa privada gera ocorrência privada. A verificação numérica dos arquivos reais deve ficar em ambiente privado, sem publicação no Drive compartilhado.
