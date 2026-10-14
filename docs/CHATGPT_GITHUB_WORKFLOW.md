# Fluxo de desenvolvimento e GitHub

Este repositório usa `main` como histórico protegido e `develop` como integração. Alterações não devem ir diretamente para `main`.

## Limite do conector GitHub do ChatGPT

A integração padrão GitHub do ChatGPT é de leitura: ela permite analisar e pesquisar repositórios autorizados, mas não cria branches, commits ou pull requests. Para mudanças diretas por IA é necessário usar uma experiência que tenha ações de escrita autorizadas (por exemplo, Codex) ou criar a branch e o pull request pela interface do GitHub. O CI deste repositório funciona independentemente de como o código chegou ao GitHub.

## Fluxo seguro

1. Crie `feature/<tema>` a partir de `develop` em uma ferramenta com permissão de escrita.
2. Antes de enviar, revise para não incluir arquivos de chave, `.env`, `local.properties`, APK/AAB, dados de usuários, logs ou backups.
3. Abra um pull request de `feature/<tema>` para `develop`. O workflow **Android DEV APK** deve concluir build e testes; o APK de debug fica como artefato temporário do GitHub Actions.
4. Depois de revisão, abra um pull request separado de `develop` para `main`. Faça merge apenas com CI verde e revisão humana.

## Firebase / Firestore

- Regras devem permanecer em arquivos versionados (`firestore.rules` e `firebase.json`) quando o projeto os fornecer.
- Toda mudança deve ser validada no Firebase Emulator Suite com testes de regras antes de qualquer deploy.
- Deploy só pode ocorrer com Firebase CLI autenticado para o projeto correto e após confirmação explícita do identificador do projeto e das regras que serão publicadas.
- Nunca use SSH para Firebase: Firebase não usa SSH para deploy de regras. Não armazene tokens, contas de serviço ou credenciais no repositório ou em prompts.

## Rollback

- Nunca force-push em `main` ou `develop`.
- Para reverter, abra um novo PR que use `git revert` do commit aprovado; preserve o histórico e o log de CI.
