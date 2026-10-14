# Central do Casal — 0.1.3-preview

Esta prévia amplia compras e a experiência diária. A lista descreve implementações observadas no código. Build, 110 testes e assinatura do APK foram confirmados pelo agente principal em `VALIDACAO.md`. O APK foi entregue no Drive; o ZIP de fonte está pronto localmente e aguarda aprovação de envio.

## Compras durante a compra

Compras separa **Planejar**, **Modo Mercado** e **Carrinho**. Prepare a lista do mês pelo catálogo ou adicione um item. Escolha Mercado/Açougue, orçamento e quantidade prevista. No Modo Mercado, informe quantidade e preço reais antes de adicionar ao carrinho. Pendências e orçamento continuam visíveis; revise os itens e confirme o fechamento. O histórico preserva o recibo e itens pendentes continuam na lista. Compras não criam despesas financeiras automaticamente.

## Ano e comparação de preços

**Ano de compras** (`YearPurchases`) mostra total documentado, cobertura de meses, média dos meses conhecidos, barras selecionáveis, escolha de ano/mês e categorias. Valores ausentes ficam a confirmar. Contas previstas e lançamentos manuais não entram no total documentado.

A comparação de preços (`SKUPriceComparison`) trabalha com observações do mesmo SKU na mesma unidade, ordenadas por data. Mostra primeiro/último preço, diferença e tendência; não mistura KG com UN nem combina produtos por nome parecido. Preço inicial zero não gera percentual. Não é previsão de preço futuro.

## Associações sob controle humano

Foram preparadas 80 propostas de associação para revisão humana. A tela distingue sugestão, confirmação e rejeição. Confirmar/rejeitar cria uma decisão própria em `catalogLinks`, preservando proposta e comprovantes originais. Confiança não dispensa escolha humana. Arquivos integrais de propostas e registros pessoais ficam fora do APK e da fonte pública.

## Experiência diária

O início tem quatro cartões: Meu dia, Mercado, Nossos projetos e Nossa jornada. O desenho Strong organiza hierarquia, contraste e navegação. Rotina inclui semana selecionável (`WeekRibbon`). Jornada apresenta Ilha das Surpresas, Bosque das Conversas e Observatório dos Sonhos; convites continuam com aceites individuais, sem confirmar por outra pessoa.

Vibração usa feedback do sistema. Sons ao tocar são opcionais e começam desativados. Configurações oferece testes manuais de som/vibração e notificação de teste genérica com visibilidade privada. Preferências, permissões e bloqueios do Android podem impedir som, vibração ou apresentação. Funcionamento físico não foi demonstrado nesta revisão.

## Instalação e limites

Atualizar deve preservar pacote e certificado, cuja verificação final consta em `VALIDACAO.md`. A instalação depende da confirmação do Android. Não há atualização silenciosa de código nem Play Store nesta prévia.

Sincronização em dois aparelhos, TalkBack, fontes grandes, notificações e uso offline concorrente precisam de validação física. Integrações externas e emergência continuam indisponíveis; esta prévia não é a versão integral 1.0.

