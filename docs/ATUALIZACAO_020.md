# Central do Casal — 0.2.0

Esta rodada amplia a experiência Android de compras, metas e lembranças. O agente principal confirmou o build, a assinatura e 90 testes atuais sem falhas (74 Android/JVM e 16 de preparação). As regras Firestore permanecem sem alteração e mantêm a referência de 37 testes da 0.1.3; não foram executadas novamente nesta rodada. A atualização do APK no Drive ainda está pendente.

## Hoje mais compacto

O início apresenta atalhos para dia, compras, projetos e vida a dois; mostra até quatro tarefas do dia, com conclusão real por ocorrência. Orçamentos consideram somente preços e quantidades confirmados; fichas incompletas mantêm aviso de saldo parcial. Os atalhos levam às áreas correspondentes, e o histórico documentado abre o painel anual.

## Metas em uma página própria

Criar e editar metas usa uma tela inteira com nome, prazo, visibilidade, valor-alvo opcional e próximo passo. A data é escolhida no seletor de calendário Android/Material. Metas pessoais e compartilhadas ficam separadas; a visibilidade existente não muda durante a edição.

Detalhes mostram contribuições reais, valor registrado, restante e histórico. O progresso financeiro deriva dos registros explícitos, sem inferir saldo bancário. Etapas podem ser adicionadas e marcadas/desmarcadas; cada alteração compara a revisão esperada para rejeitar uma edição concorrente desatualizada. Editar os detalhes preserva as etapas mais recentes. Excluir uma meta exige confirmação e preserva contribuições anteriores para exportação.

Projetos importados oferecem uma ação explícita para iniciar o formulário de nova meta. O valor-alvo começa vazio e as etapas sugeridas começam pendentes, com done=false; salvar exige decisão do usuário. A referência original é preservada, sem conversão automática de ideias em metas ou de pendências em realizações.

## Compras na própria lista

Linhas compactas mostram produto, unidade, quantidade e subtotal. No Modo Mercado, quantidade e preço reais podem ser informados na própria linha antes de adicionar ao carrinho; unidades desconhecidas exigem confirmação na ficha. A ficha continua disponível para edição completa.

A comparação considera o último preço documentado do mesmo SKU e unidade, ou um vínculo humano confirmado e sem ambiguidade. Sem referência segura, mostra ausência de comparação; não associa produtos por semelhança de nome nem mistura unidades. A lista e os ajustes usam revisão da sessão; concluir a compra preserva o histórico e requer confirmação.

## Vida a dois com registros reais

O resumo do casal oferece atalhos para momentos, Jornada e criação de lembrança. A constelação usa momentos compartilhados e experiências com duas confirmações reais. Cada estrela seleciona um registro existente, mostra título/data e oferece um botão para abrir a área correspondente. O estado vazio convida a criar a primeira lembrança; não gera memórias demonstrativas.

## Estado da revisão

Nesta rodada foram executados diagnóstico OMX, busca AST Kotlin e construção/consulta do Code Review Graph sobre os arquivos atuais. Os especialistas são da colaboração nativa Codex; nenhum worker de equipe OMX/tmux foi iniciado. Grafo e leitura do código não demonstram entrega física, acessibilidade, sincronização em dois aparelhos ou aprovação dos testes.

Os resultados atuais, tamanho e hashes do APK estão em VALIDACAO.md. Instalação física, entrega no Drive e verificação em aparelhos ainda não foram demonstradas nesta rodada. O envio opcional de código-fonte continua dependendo de aprovação explícita; esta rodada não publicou ZIP de fonte.

Sophia/voz, serviços bancários, automação doméstica, SOS e rastreamento continuam indisponíveis. As telas os identificam como limites; esta versão não corresponde à entrega integral 1.0.
