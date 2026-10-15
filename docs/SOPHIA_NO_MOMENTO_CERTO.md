# Sophia no Momento Certo (MVP, 15/10/2026)

## O que foi implementado

- Motor determinístico `SophiaMomentPlanner` relaciona **previsão por hora** a **tarefas reais** com data, hora e responsável.
- A cada conta corresponde o próprio `uid`, nome de perfil, cache local, configurações e alarmes. Informações privadas vêm apenas do repositório autorizado. Tarefas atribuídas à outra pessoa não alimentam os avisos deste perfil.
- Considera chuva horária >= 60% em tarefas com saída/deslocamento, frio <= 14°C para saída ou cuidado explícito do Lucca e calor >= 30°C para atividades externas. Não inventa previsão de chuva quando o dado é nulo.
- Envia até três avisos contextualizados por dia por perfil, com antecedência inicial de 45 ou 60 minutos. Não agenda avisos para tarefas concluídas, marcadas para revisão, com lembrete desativado ou para compromissos iminentes demais.
- Home compacta: clima em uma linha, somente a dica com relevância nas próximas duas horas. O restante não ocupa a tela inicial.
- `AlarmManager` agenda notificações Android; `WorkManager` atualiza oportunisticamente o tempo uma vez por hora (sem promessa de minuto exato). Receiver verifica UID conectado, prazo da previsão, limite diário e horários de silêncio.
- Configuração **opt-in**: o usuário primeiro consulta a previsão com permissão de localização, depois ativa os avisos; o app salva somente coordenadas arredondadas a duas casas e **não** faz GPS em segundo plano. Pode desligar no próprio card. Sem permissão de notificação do sistema, não entrega avisos.
- Alterações nas tarefas/conclusões atualizam o cache de planejamento pela `CentralApp`, com rearmamento e cancelamento do que deixou de ser relevante.

## Fluxo de usuário

1. Com a própria conta aberta, tocar **Consultar o clima**, autorizar localização e consultar novamente após a permissão se necessário.
2. Tocar **Ativar avisos inteligentes** uma única vez e conceder a permissão Android de notificação.
3. A partir daí, a Sophia usa a área aproximada salva para atualizações oportunistas e agenda avisos antes dos compromissos elegíveis. Em **Detalhes** é possível desligar.
4. Um aviso abre a tarefa da conta destinatária, desde que o usuário permaneça nessa conta.

## Limites conscientes

- **Não há IA generativa conectada**. O texto é gerado localmente a partir de evidências verificáveis, usando regras. Sophia é nome da experiência.
- Não há previsão nem aviso meteorológico se a consulta de clima estiver indisponível, sem consentimento ou desatualizada por mais de 3 horas.
- O worker usa o último snapshot **autorizado e sincronizado** de tarefas do próprio aparelho, não lê Firestore no background. Mudanças feitas em outro aparelho poderão demorar até que a Central sincronize no destinatário.
- A entrega do Android pode atrasar por bateria/OEM, Doze, falta de rede ou permissão de alarme exato. Alarmes e notificações não têm SLA de hora exata.
- Não há push FCM cross-device nem configuração avançada por tipo de dica nesta fase.
- Recomendações de cuidados com bebê/pet não substituem avaliação de responsáveis ou profissionais.
- Após logout, a checagem do `activeUid` impede notificação para perfil anterior. Worker é vinculado por `inputData` ao UID de origem.
- Testes em S23 FE e S25 FE e acesso ao clima real devem ser realizados antes de fechar o PR ou publicar produção.

## Testes

`SophiaMomentTest.kt` inclui isolamento Tiago/Rhayra, compartilhadas/privadas, conclusão, opt-out, revisão, limite diário, 45 minutos de antecedência, ausência de chuva, frio contextual, obsolescência da previsão e falta de tempo útil.

Validar em dispositivos:
1. Entrar como Tiago, criar tarefa de deslocamento 1h30 à frente e consultar previsão real relevante.
2. Ativar avisos e permissão; colocar o app em segundo plano, verificar programação e entrega com tolerância ao Android.
3. Concluir/alterar/excluir uma tarefa e verificar cancelamento sem novas notificações.
4. Entrar como Rhayra, conferir que os avisos do Tiago não são emitidos para ela.
5. Negar localização/notificações e verificar ausência de mensagens enganosas. Desativar opt-in e comprovar cancelamento dos alarmes.
6. Testar offline, rotação, fonte ampliada e reinicialização dos dois Samsung.

Branch: `ux/fluxos-limpos-2026-10-14`; [PR #45](https://github.com/0tiagooliveira/central-do-casal/pull/45).