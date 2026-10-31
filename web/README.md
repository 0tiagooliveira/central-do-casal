# Central do Casal · versão Web (consulta)

Primeira versão web responsiva, com **somente leitura**, criada para validar a issue #69 sem alterar dados de compras.

## O que já funciona no código

- Login por e-mail e senha usando Firebase Authentication do mesmo projeto `central-do-casal`;
- consulta aos registros privados e compartilhados a que o usuário autenticado já tem acesso;
- abas **Hoje, Compras, Rotina, Planos e Nós**;
- listas Mercado/Açougue e histórico dos recibos confirmados no Firestore;
- alertas visuais para produtos com dados incompletos e títulos aparentemente duplicados, sem mesclar itens;
- PWA responsiva, utilizável no navegador do celular ou desktop;
- nenhuma gravação/exclusão de dados, nenhum fechamento de compra, nenhum envio de SOS ou disparo de notificações.

**Limitações:** recursos Android nativos não foram portados; a web não vê SharedPreferences, banco local nem dados da variante `com.centraldocasal.app.dev` que ainda não tenham sido sincronizados no Firebase. Nunca desinstale/limpe o APK para testar a web sem backup e confirmação de sincronização. Não há transferência automática de dados locais.

## Publicar gratuitamente pelo Firebase Hosting

1. No **Firebase Console → Configurações do projeto → Seus apps**, registre um app **Web** no projeto `central-do-casal`. Confira a configuração pública devolvida. Se o console informar `appId`, adicione-o ao objeto `firebaseConfig` em `web/main.js`; substitua os outros valores se forem diferentes dos atuais. A chave pública referenciada veio de `app/src/main/assets/firebase-config.json`. **Não use conta de serviço nem chave privada no frontend.**
2. Confirme no Firebase Authentication que o login por **e-mail/senha** está habilitado e que os domínios de hospedagem estão autorizados (Authorization domains). Publique/valide as regras Firestore separadamente, com o fluxo já existente. O build web não implanta regras.
3. Com Node 22 e o Firebase CLI autenticado em sua conta do projeto, execute na raiz do repositório:
   ```bash
   npx firebase-tools login
   npx firebase-tools deploy --only hosting --project central-do-casal
   ```
4. Use a URL exibida pelo deploy, normalmente o domínio `web.app` do projeto. **Não presuma que ela existe antes de um deploy confirmado.**
5. Entre com a mesma conta do Android e valide a sincronização e o `householdId` em dois dispositivos.

A configuração de Hosting está em `firebase.json`, sem alterar os emuladores ou regras existentes.

## Alternativa GitHub Pages

Foi incluído o workflow `.github/workflows/web-pages.yml` que publica apenas `web/` após merge em `main` ou acionamento manual. Em **Settings → Pages**, selecione **GitHub Actions** como fonte. Para repositórios privados, a disponibilidade de Pages depende do plano GitHub. A URL, se habilitada, é informada na execução do workflow. O domínio publicado deve estar autorizado no Firebase Auth.

## Checklist específico da issue #69

- [ ] Testar e-mail/senha no navegador com **mesmo UID** usado no Android.
- [ ] Confirmar `householdId`, orçamento e quantidade de itens da lista no Firestore.
- [ ] Confirmar se recibos antigos aparecem na web. Se só existem no aparelho, preservá-los/exportá-los antes da migração.
- [ ] Revisar títulos repetidos como «Alho» sem juntar unidades diferentes.
- [ ] Revisar qualquer item sem nome/unidade, **sem** finalizar compra.
- [ ] Testar notificações **no APK físico**, pois a versão web de leitura não implementa esse recurso.

## Segurança

A versão web usa apenas Firebase Auth + Firestore, com regras por UID e household; não contém token de servidor nem segredo administrativo. É possível executar os arquivos estáticos em um servidor HTTPS ou local; não carregar via `file://`. **Não altere as regras para acesso público** a fim de fazê-la funcionar. Se aparecer `permission-denied`, diagnostique membership, identidade e deploy das regras.
