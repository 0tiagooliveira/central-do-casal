# Sugestões de associação revisadas pela IA

As sugestões são uma revisão feita nesta entrega. Não constituem um serviço de IA contínuo no aplicativo, associação confirmada, prescrição ou substituição de produtos. O usuário deve confirmar ou rejeitar os candidatos.

## Plano de dados

O arquivo privado `work/import-casal/ai-matching-proposals.json` usa `schema: 1`, `sourceHash`, `records`, `counts`, `reviewSource`, `automaticLinkChanges: false` e `externalProviderUsed: false`. O hash deve corresponder ao arquivo original de compras usado na revisão. Não há arquivo integral arquivado nem importação de compras duplicadas.

Cada registro tem `id: ai_match_<sku_id>`, `kind: matchSuggestion`, `private: false`, `skuId: import_2026_<sku_id>`, `title`, `candidates`, `confidence`, `note`, `reviewSource: Revisão da IA nesta entrega` e `status: needs_confirmation`. Os candidatos contêm `baseItemId: import_2026_<item_id>`, `title`, `categoria` e `reason`. Confiança é `alta`, `média` ou `baixa`; confiança alta continua exigindo confirmação. Lista vazia de candidatos significa que não há associação segura na base atual.

Bases homônimas mantêm seus IDs e categorias próprios. Descrições fiscais, abreviações e embalagens incompletas não justificam corrigir quantidades silenciosamente, assumir composição ou combinar produtos diferentes.

## Operação administrativa

O utilitário privado `work/firebase-import-admin.cjs` oferece o modo `--matching`, separado de `--unified`. Combinar os dois modos é recusado. O utilitário e os arquivos pessoais não devem acompanhar um pacote público de código.

- `--matching --validate-local`: valida o plano, os IDs, categorias, contagens e hash da fonte local, sem autenticar ou acessar Firebase.
- `--matching --plan`: consulta as contas e o espaço existentes, confere as referências do catálogo e calcula documentos novos. Não publica dados.
- `--matching --apply`: cria somente sugestões ausentes e seu manifesto. Não altera perfis, catálogo, compras, contas, tarefas ou associações confirmadas.
- `--matching --verify`: exige que todas as sugestões e o manifesto já existam e compara os campos persistidos. Não grava documentos no Firebase.

O modo requer as contas já vinculadas ao mesmo espaço privado. Cada referência de SKU e item base precisa existir no catálogo compartilhado com seu tipo esperado. Qualquer conteúdo previamente importado que divirja é recusado; não há sobrescrita silenciosa.

As sugestões recebem `sourceHash`, nome da fonte, `contentHash` e horário de importação definido pelo servidor. O manifesto separado `manifest_matching_2026` contém IDs das sugestões, contagens, hash da fonte e hash do conjunto revisado. Prévia e resultado ficam em arquivos privados próprios, preservando os relatórios anteriores.

Este modo não aplica vínculos. A confirmação no aplicativo deve gerar uma decisão do usuário em registro próprio e conservar a proposta original para rastreabilidade. A rejeição deve igualmente permanecer separada da proposta original.

## Publicação do aviso de atualização

O mesmo utilitário oferece modos separados `--publish-release` e `--verify-release`, sem exigir `--plan`. Eles usam o arquivo privado `work/import-casal/release-publication.json` com `versionCode`, `versionName`, `minVersionCode`, `url`, `sha256`, `title` e `note`. A validação restringe a versão preparada, a URL HTTPS do APK no Drive e o formato do hash SHA-256. `minVersionCode` deve ser inteiro não negativo e não ultrapassar a versão publicada; no registro do app recebe o nome `minimumVersionCode`.

`--publish-release --validate-local` verifica somente o arquivo local. A publicação real modifica exclusivamente `records/app_release_android` do espaço já vinculado: `kind: appRelease`, `private: false`, campos de versão, URL, hash e textos, com `publishedAt` definido pelo servidor. Usa a versão atual do documento como precondição ou exige documento inexistente na primeira criação; não reduz uma versão mais recente e não reescreve uma publicação idêntica.

`--verify-release` lê o documento e compara os campos publicados, sem gravar no Firebase. Ambos os modos ficam separados de importações de dados, preservam perfis e exigem contas já vinculadas. Resultado e cópia anterior permanecem em arquivos privados de trabalho. Um aviso na nuvem informa a versão disponível; a instalação do APK continua sendo uma ação do usuário.
