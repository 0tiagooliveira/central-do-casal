# Sophia Fiscal v1: compra inteligente por foto

Status: implementação em PR #67, baseada no cérebro local da Sophia (PR #66). Não é uma integração com SEFAZ, banco nem comprovante de validade fiscal.

## Experiência dentro do aplicativo
1. Sophia > **Ler foto de nota fiscal**, ou escrever "ler nota fiscal" no chat.
2. Escolher imagem da galeria. O OCR utiliza **ML Kit Text Recognition V2, Latin, modelo empacotado 16.0.1**, executado no Android. A imagem original e o texto bruto não são gravados no Firebase.
3. Mostrar estabelecimento, CNPJ opcional e data. Data ausente deve ser digitada, não presumida.
4. Extrair apenas linhas que tenham nome, quantidade, unidade e preço com formato reconhecível. Se a leitura falhar, o usuário pode adicionar linhas manualmente.
5. Para cada item, mostrar possível vínculo com produto do catálogo importado, base já confirmada ou produto de compras por foto, inclusive de outras lojas. A seleção do produto é revista individualmente.
6. Mostrar preço **por unidade equivalente** e comparar com última compra e menor preço histórico documentado. Não comparar pacote x quilo sem quantidade comum confiável nem variantes/tamanhos diferentes.
7. Exigir conferência individual de cada item. Se soma diferir do total detectado ou houver compra semelhante na mesma loja/data, solicitar reconhecimento explícito da situação.
8. Confirmar para salvar. A Central grava de forma atômica, na conta pessoal, o estabelecimento (se novo), produtos novos (se necessários) e a nota com itens/preços. Nenhuma transação financeira é criada automaticamente.
9. A seção **Compras por estabelecimento** agrupa compras desta fonte e, quando a identificação do estabelecimento é compatível, histórico importado já existente.

## Inteligência e limites
- Identidade prioritária de loja: CNPJ de 14 dígitos; sem CNPJ, nome normalizado com acentos/pontuação/case.
- Identidade de produto: vínculo explícito de catálogo; exatidão textual única pode ser pré-selecionada, mas a pessoa ainda marca "Conferi".
- Correspondência parcial é **apenas sugestão**. Números/tamanhos diferentes não devem se fundir.
- Valor: R$/un, R$/kg ou R$/L calculados usando quantidade; comparação feita apenas na mesma unidade. Diferença do desconto fiscal não é rateada automaticamente, nem simulamos que ela é uma nova cotação.
- Histórico de notas por foto entra no próximo comparativo; itens do catálogo importado dependem de vínculos confirmados.
- Sem leitura de CPF do comprador, sem upload do comprovante ao Drive, sem armazenamento do texto OCR original.
- Por privacidade, notas novas são **pessoais**, não compartilhadas automaticamente com a outra pessoa do casal. Uma opção de compartilhamento explícita exige regra de segurança e UX próprios no futuro.
- Notas parcialmente ilegíveis, descontos e produtos com peso variável devem ser conferidos antes de salvar.
- Nota por foto não implica que houve débito bancário, compra pela internet ou NFC-e autenticada.

## Arquitetura
- `FiscalVision.kt`: parse conservador, IDs canônicos, sugestões, comparação e conversão de rascunho confirmado em registros.
- `SophiaFiscalScreen.kt`: seletor de fotos Android, OCR no dispositivo, edição, revisão e confirmação.
- `CentralViewModel.saveSophiaFiscalReview`: valida UID da sessão antes de salvar.
- `CentralRepository.savePhotoFiscalRecords`: batch de records privados; operação local equivalente.
- `firestore/firestore.rules`: permite apenas `photoMerchant`, `photoProduct` e `photoReceipt` privados com limites de dados.
- Testes JVM `FiscalVisionTest` e teste de regras do Firestore.

## Validação antes de liberar o APK
- [ ] Gradle APK DEV compilado e testes Firebase/JVM aprovados no PR.
- [ ] Verificar foto real de NFC-e longa, cupom térmico apagado, total com desconto, peso decimal, datas, foto inclinada e produto repetido.
- [ ] Verificar dois perfis, criação repetida e tentativa de dados privados indevidos.
- [ ] Medir layout e scrolling em Samsung S23 FE e S25 FE e com TalkBack, fonte ampliada.
- [ ] Revisar tratamento de duplicidade entre dispositivos e adicionar identificador fiscal de 44 dígitos/QR SEFAZ opcional, com consulta consentida.
- [ ] Considerar compartilhamento explícito dos registros fiscais entre os membros com controle de acesso.
- [ ] No final do processo, gerar APK consolidado e colocá-lo no Google Drive. Um artefato ZIP do GitHub Actions pode conter o APK, mas **não** equivale a distribuição de release com QA físico.

## Fontes do reconhecimento no dispositivo
Documentação oficial: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
Versão empacotada: `com.google.mlkit:text-recognition:16.0.1`.
