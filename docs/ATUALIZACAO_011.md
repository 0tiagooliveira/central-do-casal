# Atualização 0.1.1 — Central do Casal

Abra Central-do-Casal-0.1.1.apk no celular e confirme a atualização do aplicativo existente. Não desinstale a versão anterior: o mesmo pacote e certificado preservam os dados locais e a sessão. Nenhum dado real foi apagado ou importado por esta correção.

## Correção de conexão

O código anterior começava as consultas do espaço quando o perfil mudava no cache, antes de a criação ou entrada estar confirmada no servidor. Além disso, um erro de uma consulta ficava retido no estado geral. São defeitos compatíveis com o print, onde o convite foi criado apesar da mensagem de acesso recusado; o print sozinho não permite identificar qual consulta recebeu a recusa.

Agora o app espera a confirmação da associação no servidor. O cache só é usado para um espaço previamente confirmado para a mesma conta. Falhas são acompanhadas por origem; uma consulta bem-sucedida não oculta a falha de outra. Há botão Tentar novamente, que atualiza o token e refaz as consultas. A confirmação de e-mail também atualiza o token de autenticação antes de aceitar convite.

As regras privadas do Firestore foram preservadas. Não houve abertura do banco ou alteração de dados de produção. A implementação segue os estados descritos na documentação oficial do Firebase: https://firebase.google.com/docs/firestore/query-data/listen

## Visual

Tema claro neutro para Tiago, sem atribuir uma cor pessoal definitiva. Rosa para Rhayra. Hierarquia de texto, espaçamento, cartões e navegação revisados. Hoje tem resumo do dia e cartões de compras/planos. Configurações tem identidade, conexão do casal, lembretes e privacidade em blocos menores. Convites possuem Copiar código e Compartilhar pelo seletor do Android. O código não ocupa um parágrafo enorme na tela.

As prévias em imagens são renderizações Android locais. A imagem de convite com fonte ampliada usa uma fixture explicitamente de teste, não uma conta criada nem evidência de sincronização em produção.

## Verificações

Resultados finais no arquivo VALIDACAO.md. Esta atualização continua sendo de desenvolvimento. A confirmação da correção no aparelho que apresentou o erro permanece necessária; não há validação física de duas contas nesta sessão. As pendências da versão integral continuam no pacote-fonte.
