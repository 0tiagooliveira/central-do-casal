package com.centraldocasal.app

import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import com.centraldocasal.app.domain.occurs
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Local auditable orchestration. Never sends household data to an LLM or executes an unconfirmed write. */
internal sealed interface SophiaOperation {
    val sessionUid: String
    data class CreateTask(override val sessionUid: String, val recordId: String,
                          val title: String, val day: LocalDate, val time: LocalTime?) : SophiaOperation
    data class CompleteTask(override val sessionUid: String, val taskId: String,
                            val title: String, val day: LocalDate) : SophiaOperation
}

internal data class SophiaDecision(
    val message: String,
    val evidence: String = "Dados locais autorizados",
    val proposedOperation: SophiaOperation? = null
)

internal object SophiaBrain {
    private fun normalized(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim().replace(Regex("\\s+"), " ")

    private val taskCreation = Regex(
        "^(?:me lembre de|lembre-me de|lembra de|crie (?:uma )?tarefa(?: de)?|criar (?:uma )?tarefa(?: de)?|adicione (?:uma )?tarefa(?: de)?|anote(?: uma tarefa)?(?: de)?)\\s+(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val taskCompletion = Regex(
        "^(?:concluir tarefa|conclua a tarefa|marcar tarefa|marque a tarefa)\\s+(.+?)(?:\\s+como conclu[ií]da)?$",
        RegexOption.IGNORE_CASE
    )
    private val timeAtEnd = Regex("(?:\\s+(?:às|as|pelas|para as)\\s+)(\\d{1,2})(?::(\\d{2}))?h?\\s*$", RegexOption.IGNORE_CASE)
    private val formatHour = DateTimeFormatter.ofPattern("HH:mm")

    internal fun visiblePendingTasks(data: DataState, session: SessionState, day: LocalDate): List<Record> =
        data.records.filter { task ->
            task.kind == "task" &&
                SophiaMomentPlanner.belongsTo(task, session.name) &&
                occurs(task, day) &&
                !task.flag("needsReview") && !task.flag("needsTimeReview") &&
                data.records.none {
                    it.kind == "occurrence" && it.id == occurrenceId(task.id, day) && it.flag("done")
                }
        }.sortedWith(compareBy<Record> {
            runCatching { LocalTime.parse(it.text("time")) }.getOrNull() ?: LocalTime.MAX
        }.thenBy { it.id })

    fun decide(input: String, session: SessionState, data: DataState,
               now: LocalDateTime, requestId: String = UUID.randomUUID().toString()): SophiaDecision {
        if (session.uid.isBlank()) return SophiaDecision("Entre na sua conta para consultar sua rotina.")
        val raw = input.trim().replace(Regex("\\s+"), " ")
        if (raw.isBlank()) return SophiaDecision("Escreva o que precisa. Posso consultar a agenda, compras, planos ou preparar uma tarefa.")
        if (raw.length > 280) return SophiaDecision("Para sua segurança, envie um pedido de até 280 caracteres.")
        val query = normalized(raw)

        // Writes take precedence over keyword-based read commands, and ALWAYS return a draft.
        val create = taskCreation.matchEntire(raw)
        if (create != null) {
            var title = create.groupValues[1].trim()
            var day = now.toLocalDate()
            var at: LocalTime? = null
            val match = timeAtEnd.find(title)
            if (match != null) {
                val hour = match.groupValues[1].toIntOrNull()
                val minute = match.groupValues[2].ifBlank { "00" }.toIntOrNull()
                if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59)
                    return SophiaDecision("Confira o horário. Use, por exemplo, «às 19:30».")
                at = LocalTime.of(hour, minute)
                title = title.removeRange(match.range).trim()
            } else if (Regex("\\s+(?:às|as|pelas|para as)\\s+\\S+", RegexOption.IGNORE_CASE).containsMatchIn(title)) {
                return SophiaDecision("Não reconheci o horário. Escreva «às 19:30», por exemplo.")
            }
            if (normalized(title).endsWith(" amanha")) {
                day = day.plusDays(1)
                title = title.replace(Regex("\\s+amanh[ãa]$", RegexOption.IGNORE_CASE), "").trim()
            } else if (normalized(title).endsWith(" hoje")) {
                title = title.replace(Regex("\\s+hoje$", RegexOption.IGNORE_CASE), "").trim()
            }
            if (title.length !in 3..120) return SophiaDecision("Qual é o nome da tarefa? Use de 3 a 120 caracteres.")
            if ((query.startsWith("me lembre") || query.startsWith("lembre-me") || query.startsWith("lembra de")) && at == null)
                return SophiaDecision("Para programar um lembrete, informe o horário. Exemplo: «Lembre-me de dar ração à Mel amanhã às 22:00».")
            val op = SophiaOperation.CreateTask(session.uid, requestId, title, day, at)
            return SophiaDecision(
                "Posso criar a tarefa pessoal «$title» para $day" +
                    (at?.let { " às ${it.format(formatHour)}" } ?: " sem notificação") +
                    ". Confirmar?",
                "Prévia, nenhuma alteração foi salva",
                op
            )
        }
        val complete = taskCompletion.matchEntire(raw)
        if (complete != null) {
            val title = complete.groupValues[1].trim()
            val matches = visiblePendingTasks(data, session, now.toLocalDate())
                .filter { normalized(it.text("title")) == normalized(title) }
            return when(matches.size) {
                0 -> SophiaDecision("Não encontrei uma tarefa pendente de hoje com esse nome na sua conta. Confira o título na Rotina.")
                1 -> {
                    val task = matches.single()
                    SophiaDecision("Quer marcar «${task.text("title")}» como concluída hoje?",
                        "Tarefa existente na sua Rotina",
                        SophiaOperation.CompleteTask(session.uid, task.id, task.text("title"), now.toLocalDate()))
                }
                else -> SophiaDecision("Há mais de uma tarefa com esse título. Abra a Rotina para escolher a correta.")
            }
        }
        val day = if (Regex("\\bamanha\\b").containsMatchIn(query)) now.toLocalDate().plusDays(1)
                  else now.toLocalDate()
        if (listOf("agenda", "tarefas", "compromissos", "meu dia", "nossa noite", "minha noite",
                "o que tenho", "o que temos", "minha rotina").any { it in query }) {
            val pending = visiblePendingTasks(data, session, day)
            return if (pending.isEmpty()) SophiaDecision(
                "Não encontrei tarefas pendentes para $day na sua agenda da Central. Calendários externos ainda não estão conectados.",
                "Rotina do aplicativo"
            ) else SophiaDecision(
                "Para $day, encontrei ${pending.size} tarefa(s) pendente(s): " +
                    pending.take(4).joinToString("; ") { task ->
                        val at = task.text("time").takeIf { it.isNotBlank() }?.let { " às $it" } ?: ""
                        "${task.text("title")}$at"
                    } + if (pending.size > 4) "; e mais ${pending.size - 4}." else ".",
                "Rotina do aplicativo"
            )
        }
        if (listOf("compras", "mercado", "acougue", "lista de compra").any { it in query }) {
            val waiting = data.baskets.values.sumOf { basket -> basket.entries.count { !it.bought } }
            return SophiaDecision("Há $waiting item(ns) ainda não marcados como comprados nas listas da Central. Posso abrir Compras, mas não faço pagamentos.",
                "Listas de Compras")
        }
        if (listOf("planos", "metas", "projetos").any { it in query }) {
            val count = data.records.count { it.kind == "plan" }
            return SophiaDecision("Encontrei $count plano(s) cadastrados. Para mudar metas e valores, use Planos e confirme a alteração.",
                "Planos cadastrados")
        }
        if (listOf("casa", "alexa", "lampadas", "luzes", "dispositivos", "tv", "televisao").any { it in query }) {
            val count = data.records.count { it.kind == "smartDevice" && !it.flag("archived") }
            return SophiaDecision("Nossa Casa tem $count aparelho(s) cadastrados. Isso não comprova que estejam on-line. Ainda não controlo lâmpadas, TVs ou Alexa.",
                "Inventário da Casa")
        }
        return SophiaDecision(
            "Posso consultar «meu dia», «compras», «planos» e «casa». " +
                "Também preparo «crie uma tarefa estudar amanhã às 19:00» ou «concluir tarefa estudar» para você confirmar. " +
                "Sou um coordenador local nesta versão, ainda não um chat generativo ou assistente de voz.",
            "Comandos locais disponíveis"
        )
    }
}
