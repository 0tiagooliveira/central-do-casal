# Redesign UX/UI: Central do Casal (iniciado em 14/10/2026)

## Objetivo

Reorganizar a experiência para **uma ação importante por tela** e **detalhes sob demanda**. A interface deve ser agradável nos Samsung S23 FE e S25 FE, legível com fonte ampliada e útil com uma mão. A reformulação deve preservar autenticação, isolamento de dados, histórico, regras de acesso e notificações reais.

> Este documento é um mapa de implementação; não declara como testado um fluxo sem evidência de build, CI e aparelhos físicos.

## Diagnóstico baseado no código (main em 14/10)

- **Compras**: `ShoppingSessionScreen` usa grandes cards de orçamento, modo planejamento, reconciliação e três abas antes dos itens. A montagem mensal usa um `AlertDialog` com uma lista contida a 360dp.
- **Ficha de compras**: o formulário exige ver à primeira vista destinatário, grupo, subgrupo, prioridade, cômodo, unidade, quantidades e preços, inclusive quando a pessoa só quer anotar leite.
- **Rotinas**: filtros e bloco de cuidados competem pelo espaço principal; tarefas compartilhadas podem ficar ocultas com o filtro inicial "Para mim". Itens concluídos desapareciam da lista da rotina, escondendo a sensação de progresso.
- **Cuidados**: ações e histórico estão dentro da tela de rotina, com chips desabilitados ocupando espaço constante.
- **Momentos**: são registros `moment` com título, data e anotação; **não há campo nem infraestrutura de foto** para "Noite do sushi".
- **Configurações**: concentra personalização, convite, notificações, diagnósticos longos, backup, atualização, integração e saída em uma rolagem.
- **Lembretes**: o código usa `AlarmManager`, `WorkManager` e permissão de notificações. Isso **não equivale a entrega comprovada** em ambos os Samsung.
- **UI geral**: existem Material 3, cores personalizadas, haptics e animação. O problema central é arquitetura de informação e sequência de ações, não ausência de bordas arredondadas.

## Referências de UX

| Domínio | Aplicativo | O que adaptar | O que não copiar |
|---|---|---|---|
| Rotina e agenda | [TickTick](https://ticktick.com/features) | captura rápida, calendário visual, tarefas recorrentes, lembretes claros, progresso | excesso de filtros e ferramentas de produtividade profissional |
| Casal | [Paired](https://www.paired.com/) | experiências privadas, momentos, datas especiais, pequenos convites opcionais | questionários/engajamento excessivo, pressão na relação |
| Lista | [AnyList](https://www.anylist.com/lists) | entrada imediata com sugestões, categorias automáticas, listas compartilhadas e favoritos | transformar cada item em cadastro obrigatório |
| Mercado | [Bring!](https://www.getbring.com/en/features) | compra com poucos toques, histórico recente, gestos reversíveis, agrupamento de corredores | mover item para "comprado" sem preço confirmado quando isso afetar dados de custo |
| Componentes | [Material 3 Android](https://developer.android.com/design/ui/mobile/guides/patterns/settings) | agrupamento em subtelas, bottom sheets, acessibilidade de toque 48dp | diálogos pequenos contendo fluxos longos |

Referências são inspiração de comportamento e hierarquia, não para copiar marcas, ilustrações ou componentes protegidos.

## Navegação-alvo

- **Hoje**: saudação curta, agenda do dia, tarefas e atalhos relevantes. Não mostrar dashboards vazios.
- **Rotina**: Hoje | Semana | Agenda; capturar tarefa, concluir, editar, responsável e lembrete; histórico de cuidados em rota específica.
- **Compras**: lista de itens ocupa o maior espaço; campo "O que comprar?" sempre acessível. Orçamento resumido; detalhes, catálogo, importações e notas em rotas secundárias.
- **Planos**: painel de metas com progresso e um próximo passo concreto; telas de detalhe para cronograma e contribuições.
- **Nós**: momentos em mosaico com fotos reais, álbum compartilhado e atividades leves; privacidade por momento.
- **Configurações**: Conta e casal; Lembretes; Aparência; Privacidade; Avançado. Ferramentas técnicas não ficam na abertura.

## Etapa 1: primeira correção estrutural [branch ux/fluxos-limpos-2026-10-14]

### Implementado na branch (a validar no CI)

- `ShoppingSimple.kt`: tela de lista com adição em um campo, lista principal expansível, carrinho, orçamento compacto e menu secundário.
- `ShoppingHub.kt`: substitui o ponto de entrada da lista por essa tela, mantendo histórico, catálogo e revisão disponíveis.
- `ShoppingEditor`: campos de uso cotidiano primeiro, opções de catálogo escondidas; ao tocar "Comprar", abrir com compra selecionada para preenchimento de preço real.
- `RoutineScreen`: filtro inicial completo, progresso de tarefas, registro de concluídas na mesma data e cuidados abaixo das tarefas.
- `TaskRow`: hierarquia enxuta e feedback visual de conclusão.
- `CareEventsPanel`: ação única de registrar e histórico expansível; não atribuir pontos por fralda, banho ou alimentação de pet.
- `SettingsScreen`: categorias de navegação e diagnósticos técnicos recolhidos.
- Teste de UI legado adaptado às rotas alteradas.

### Gates para aceitar a etapa 1

1. Android APK compila sem erros; suíte unitária e regras Firestore passam.
2. Inserir arroz, leite e fralda sem abrir formulário; listas persistem e sincronizam no casal.
3. Compra não registra valor inventado; edição exige preço real antes do fechamento.
4. Tarefa concluída continua visível na lista do dia e no progresso.
5. Cuidado é registrado com usuário/horário reais e sem efeitos de jogo; desfazer mantém regra de autoria.
6. Configurações principais cabem em uma tela com fonte normal e são acessíveis com fonte 130%.
7. Testar no S23 FE/S25 FE, navegação por teclado, TalkBack, rotações, IME, modo escuro se adotado e aparelhos sem conexão.
8. Verificar que logout/privacidade/convite/backup/diagnóstico não perderam acesso.

## Etapa 2: momentos e fotos (P0 funcional)

Objetivo: **"Noite do sushi" deve permitir anexar uma foto real**. Fluxo: Nós → Momentos → abrir "Noite do sushi" → `Adicionar foto` → seletor Android → prévia → salvar → abrir em tela cheia → alterar/remover.

- Implementar Photo Picker sem exigir acesso amplo à galeria.
- Fotos do casal exigem Cloud Storage/serviço equivalente com **regras restritas aos membros do household**; momento privado fica restrito ao autor.
- Validar tipo de arquivo, limite de tamanho, miniatura, compressão, status de upload e tentativa novamente.
- Armazenar caminho de foto associado ao registro, não URL pública permanente; tratar exclusão e mudança de membros.
- Fazer fallback offline e indicador de upload; não prometer sincronização enquanto Storage/regras não estiverem configurados em produção.
- Preservar momentos existentes, seus títulos, datas e textos.

## Etapa 3: tarefas, agenda e lembretes (P0/P1)

- **Hoje** com linha de tempo por horários; **Semana** com faixa de datas; **Agenda** dia/semana/mês.
- Criação em sheet: título obrigatório, dia e responsável; recorrência e aviso opcional.
- Para rotina recorrente de domingo a quinta: lembrete de domingo para preparar a reserva de segunda; tratar tarefa individual ou recorrência explicitamente.
- Notificação própria de cada conta, checar permissão Android 13+, alarmes exatos, modo Sossego e otimização de bateria Samsung.
- Botões na notificação: abrir tarefa, adiar, concluir (após validação de autoria e sessão). Sem prometer push remota até ser criada.
- Gamificação **opt-in** para tarefas domésticas e atividades recreativas: progresso e sequência sem competição entre parceiros e **sem pontuar cuidados do Lucca/Mel**.

## Etapa 4: mercado prático (P0/P1)

- Substituir a "pré-lista do mês" em modal de 360dp por tela de lista completa.
- Sugestões de recorrência por compras históricas e catálogo local; favoritos, itens recentes e entrada por voz se permissão.
- Editar quantidade e unidade inline; arrastar por setor/corredor; gestos com desfazer.
- **Modo Mercado** distinto de "planejar": foco em comprar, unidade, preço atual e subtotal. Barra fixa de total/orçamento/saldo, sem obstruir produtos.
- Alertas de preço só com produto/unidade comparáveis, sem falsa precisão. Nota fiscal e revisão de vínculo em área especializada.
- Catálogo, produto, histórico de preço, etiqueta fiscal e correções **separados** do fluxo de lista.
- Deixar "Açougue" como lista irmã do "Mercado" no esquema atual até definir múltiplas listas sem migração arriscada.

## Etapa 5: restante das telas

- **Produto**: título/variante, último preço confiável, quantidade, CTA "Adicionar". Detalhes técnicos e correção de vínculos em seção avançada.
- **Planos**: ações imediatas, próximo marco e barra de progresso; editar meta em fluxo secundário.
- **Casa/Sophia/Segurança**: separar controle do dia a dia, configuração de dispositivos e experimentos; avisos de limite e consentimento visíveis.
- **Hoje**: eliminar blocos repetidos, mostrar compromissos realmente relevantes. Resumos de mercado/casal só quando houver algo que requeira atenção.
- **Entrar e convite**: telas claras, estados de erro, carregamento e recuperação sem compartilhar credenciais.
- **Estados de sistema**: vazio, carregando, offline, falha, sem permissão, erro de sincronização, edição simultânea, sucesso e desfazer.

## Tokens e microinterações

- Cards com raios de 18 a 24dp, campos 14 a 18dp, espaçamento base 8dp, hierarquia 8/12/16/24dp.
- Fundo suave, superfícies brancas e sombra discreta apenas para informação tocável. Evitar card dentro de card e excesso de texto.
- Primário pessoal existente, acentos apenas quando necessário; contraste AA e tamanho de toque mínimo 48dp.
- Movimentos curtos de 160-240ms e haptic sutil de confirmação, respeitando preferências e acessibilidade do sistema.
- Gamificação: feedback pela conclusão, streak opcional, celebração discreta, sem pontuar cuidados sensíveis, sem culpabilizar dias incompletos.
- Preservar significado das cores: erro não é chamado de recompensa.

## Qualidade e entrega

Cada etapa: revisão das telas reais → fluxos → código na branch → testes e acessibilidade → PR → QA em 2 aparelhos → merge aprovado. Nunca usar alterações visuais como justificativa para apagar dados ou relaxar regras. A partir de uma falha observada, reproduzir com screenshot e evento correspondente antes de marcar o gate como resolvido.
