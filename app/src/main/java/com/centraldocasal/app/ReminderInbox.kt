package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurs
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate
import java.time.LocalTime

/**
 * Today's unfinished activities, not a list of confirmed Android notifications.
 * Only the signed-in member's tasks and shared tasks can be shown.
 */
internal fun reminderVisibleTasks(
    records: List<Record>,
    profileName: String,
    today: LocalDate
): List<Record> = records.filter { task ->
    task.kind == "task" && taskBelongsTo(task, profileName) && occurs(task, today) &&
        records.none {
            it.kind == "occurrence" && it.id == occurrenceId(task.id, today) && it.flag("done")
        }
}.sortedWith(compareBy<Record> {
    runCatching { LocalTime.parse(it.text("time")) }.getOrNull()
        ?: LocalTime.MAX
}.thenBy { it.id })

/** Imported tasks requiring review may not occur on any date until confirmed. */
internal fun reminderReviewTasks(records: List<Record>, profileName: String): List<Record> =
    records.filter { task ->
        task.kind == "task" && taskBelongsTo(task, profileName) &&
            (task.flag("needsReview") || task.flag("needsTimeReview"))
    }.sortedWith(compareBy<Record> { it.text("title") }.thenBy { it.id })

/** A human-readable schedule hint is not proof that the OS has delivered an alarm. */
internal fun reminderScheduleHint(task: Record): String = when {
    task.flag("needsReview") -> "Dados da tarefa precisam ser confirmados."
    task.flag("needsTimeReview") -> "Horário precisa ser confirmado."
    task.flag("skipReminder") -> "Aviso desligado para esta tarefa."
    runCatching { LocalTime.parse(task.text("time")) }.getOrNull() == null ->
        "Sem horário válido, sem aviso automático."
    else -> "Horário registrado, consulte o estado do alarme."
}

@Composable
internal fun ReminderInbox(
    session: SessionState,
    data: DataState,
    openRoutine: () -> Unit,
    openSettings: () -> Unit,
    openReview: () -> Unit = openRoutine
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val prefs = context.getSharedPreferences("reminders_${session.uid}", android.content.Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean("enabled", true)
    val allowed = AndroidInteractions.notificationsAllowed(context)
    val allPending = reminderVisibleTasks(data.records, session.name, today)
    val reviewTasks = reminderReviewTasks(data.records, session.name)
    val tasks = allPending.filterNot { it.flag("needsReview") || it.flag("needsTimeReview") }
    val diagnostics = Reminders.diagnostics(context, session.uid)
    var inspectedTask by rememberSaveable(session.uid) { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHero("Lembretes", "Tarefas e avisos do seu aparelho.", ReminderBell)
        }
        item {
            QuietCard("Notificações neste aparelho", when {
                !allowed -> "O Android não permite notificações. Revise as permissões."
                !enabled -> "Avisos desativados nas suas preferências."
                else -> "${diagnostics.futureSchedules} alarme(s) futuro(s) registrados no aparelho. Isso não confirma entrega."
            }) {
                TextButton(onClick = openSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Configurar avisos")
                }
            }
        }

        item {
            Text("Tarefas pendentes de hoje", style = MaterialTheme.typography.titleLarge)
            Text("Uma tarefa pendente não significa que há um aviso programado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tasks.isEmpty()) item {
            QuietCard("Sem tarefas pendentes", "Não há atividades confirmadas pendentes para hoje.") {
                TextButton(onClick = openRoutine, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Abrir Rotina")
                }
            }
        }
        items(tasks, key = { "pending-${it.id}" }) { task ->
            val validTime = runCatching { LocalTime.parse(task.text("time")) }.getOrNull()
            val timeText = validTime?.toString() ?: "Sem horário"
            QuietCard(task.text("title").ifBlank { "Tarefa sem título" },
                "$timeText · ${task.text("owner", "Ambos")}") {
                Text(reminderScheduleHint(task), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = openRoutine, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Abrir na Rotina")
                    }
                    TextButton(onClick = {
                        inspectedTask = if (inspectedTask == task.id) null else task.id
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (inspectedTask == task.id) "Ocultar status" else "Ver estado do aviso")
                    }
                }
                if (inspectedTask == task.id) {
                    val check = Reminders.inspect(context, session.uid, task, session.name)
                    Text(check.status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (reviewTasks.isNotEmpty()) {
            item {
                Text("Tarefas para revisar (${reviewTasks.size})",
                    style = MaterialTheme.typography.titleLarge)
                Text("Confirme os dados e horários antes de ativar os avisos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(reviewTasks.take(5), key = { "review-${it.id}" }) { task ->
                QuietCard(task.text("title").ifBlank { "Tarefa sem título" },
                    reminderScheduleHint(task)) {
                    TextButton(onClick = openReview, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Revisar tarefa")
                    }
                }
            }
            if (reviewTasks.size > 5) item {
                TextButton(onClick = openReview, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Ver todas as ${reviewTasks.size} tarefas para revisar")
                }
            }
        }
    }
}
