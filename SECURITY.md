# Segurança

## Nunca versionar

- Chaves de assinatura Android (`.jks`, `.keystore`), certificados ou chaves privadas.
- Tokens do Firebase/Google, credenciais de contas de serviço, arquivos `.env`, `local.properties` ou arquivos de configuração com segredos.
- APKs, AABs, logs e backups.
- Dados pessoais de usuários, exportações do Firestore, capturas de tela ou identificadores de produção.

## Antes de um pull request

Execute uma revisão por padrões de segredo e dados pessoais. Se algum segredo já tiver sido versionado, revogue/rotacione-o no provedor e remova-o do histórico somente com um plano de migração aprovado.

## Divulgação

Relate vulnerabilidades de forma privada ao mantenedor do repositório. Não publique credenciais em issues, PRs ou comentários.
