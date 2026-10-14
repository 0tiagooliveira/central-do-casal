# Central do Casal

Aplicativo Android nativo em Kotlin, Jetpack Compose e Material 3 para organização compartilhada: rotina, compras, planos, memórias, casa inteligente e segurança.

## Recursos atuais

- Compras organizadas por pessoa, grupo, subgrupo, prioridade e cômodo; histórico de preço por produto e revisão de vínculos de notas fiscais.
- Rotinas pessoais e compartilhadas, recorrência por dia da semana e lembretes locais.
- SOS com ligação, WhatsApp, alerta sonoro/vibração e compartilhamento de localização entre os membros do espaço.
- Tela de segurança mostra o mapa do parceiro quando ambos estão autenticados, pertencem ao mesmo espaço e ativam o compartilhamento. A atualização ocorre enquanto o aplicativo permanece aberto.
- Cadastro de aparelhos de casa inteligente e organização por cômodo.

## Segurança e Firebase

O projeto Firebase é `central-do-casal`. As regras e índices são versionados em `firestore/`. O arquivo `firebase-config.json` contém a configuração pública de cliente do Firebase; ele não concede acesso administrativo. As regras do Firestore, autenticação e permissões de Android protegem os dados.

Nunca envie para o repositório:

- chaves de serviço (`service-account*.json`), arquivos `.env`, keystores ou `local.properties`;
- arquivos de banco local, notas fiscais, fotos, APKs ou dados exportados de pessoas;
- tokens do GitHub, Firebase ou Google Cloud.

## Compilar e testar localmente

Requisitos: JDK 21, Android SDK e Node 22.

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest
npm ci
npm run test:rules
```

No Windows, use `gradlew.bat`.

## Publicar regras pelo GitHub

O fluxo **Publicar regras do Firebase** só roda manualmente e exige digitar `PUBLICAR`. Antes da primeira publicação, configure no repositório GitHub os secrets:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

Eles devem usar Workload Identity Federation do Google Cloud; não armazene um JSON de conta de serviço no GitHub. A conta de serviço precisa ter a função **Firebase Rules Admin** ou permissão equivalente no projeto `central-do-casal`.

## Trabalhar pelo chat

Após enviar este repositório privado ao GitHub, conecte-o ao ChatGPT em **Configurações → Conectores → GitHub**. Assim, você pode pedir alterações no chat apontando para o repositório, revisar diffs e criar commits sem depender desta cópia local.