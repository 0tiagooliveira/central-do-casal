# Integração EAN / GTIN na Central do Casal (web)

## Escopo desta branch
- Scanner opcional com `BarcodeDetector` e câmera em HTTPS. Fallback: EAN digitado. Fecha o stream ao cancelar, ocultar a aba ou concluir a leitura.
- GTIN-8, GTIN-12, GTIN-13 e GTIN-14 passam pela verificação do dígito final. **Códigos internos do Muffato, como `17246`, não são EAN**.
- A consulta começa com catálogo e linhas de XML fiscal já carregados no estado da família (sem leitura adicional). Depois tenta cache do navegador, PreçoMelhor e Open Food Facts. Resultados indicam a fonte e não incluem preços pagos.
- Rascunhos do scanner ficam no `localStorage` separados por usuário, casa e Mercado/Açougue. Até a confirmação do usuário, **zero leituras/gravações no Firestore por scanner**. Um lote é enviado por uma transação, com verificação de revisão e sem substituir registros dos outros celulares.
- O lote EAN não é enviado enquanto há operações ou finalização offline pendentes nessa lista. Itens já encontrados por EAN ou por título/unidade não são duplicados. Se a lista mudou enquanto o usuário revisava, o rascunho continua no aparelho. Para problemas de conexão, não limpar o rascunho.
- A importação fiscal de XML já preserva `cEAN` em `web/invoice-parser.js`. Somente itens do histórico carregado cujo `gtin` seja válido podem ser sugeridos para revisão. Esta etapa **não** vincula automaticamente descrições fiscais a códigos internos, não importa os PDFs desta conversa e não grava um catálogo global; associação com código interno de loja exige confirmação e evidência.
- Não se faz deploy, merge, escrita na base de produtos, migração de Firestore ou publicação de regras neste PR.

## Fontes e limitações
- PreçoMelhor: `https://precomelhor.com.br/api-publica` (API de consulta EAN sem chave, com atribuição obrigatória e recomendação de cache). É um serviço de terceiros e sua cobertura não é garantida.
- Open Food Facts: `https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/` (ODbL, share-alike, atribuição, limites de leitura). Navegadores não permitem definir livremente o cabeçalho User-Agent; validar acesso e política de identificação antes do rollout. Em caso de bloqueio ou indisponibilidade, o usuário consegue digitar o nome.
- O preço real/histórico só pode ser registrado a partir de compras/nota, não atribuído à identificação por EAN.
- `BarcodeDetector` não é suportado universalmente. Em browsers sem suporte, há entrada manual; não enviamos imagens para processamento remoto.
- Dados retornados de fonte externa não são automaticamente misturados ao catálogo compartilhado. O nome só integra a lista após revisão do usuário.

## Testes e QA pendentes
- `npm run test:ean` testa dígito de controle, conversão de fontes, fallback, cache, drafts, isolamento e duplicatas; `node --check` testa a sintaxe dos módulos/browser.
- Testar câmera e permissão em Chrome Android HTTPS e fallback em iPhone, com GTIN real.
- Testar duas contas e dois dispositivos na mesma casa; enviar rascunho só após confirmação, verificar concorrência e nenhuma duplicação.
- Conferir CORS e identificação com as APIs reais, inclusive situações 404, 429, offline e resposta inesperada.
- Verificar métricas efetivas de Firestore somente no Console autorizado.
- Revisar compatibilidade com a branch concorrente do PR #101 antes do merge.
