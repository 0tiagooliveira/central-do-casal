package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.occurs
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate

/**
 * Inbox is personal, even if shared records for the partner are present in DataState.
 * This is independent of notification delivery; "pending" is not a scheduled alarm.
 */
internal fun reminderVisibleTasks(
    records: List<com.centraldocasal.app.domain.Record>,
    profileName: String,
    today: LocalDate
): List<com.centraldocasal.app.domain.Record> = records.filter { task ->
    task.kind=="task" && taskBelongsTo(task,profileName) && occurs(task,today) &&
        records.none { it.kind=="occurrence" && it.id==occurrenceId(task.id,today) && it.flag("done") }
}.sortedBy { it.text("time").ifBlank { "99:99" } }

@Composable
internal fun ReminderInbox(session: SessionState, data: DataState, openRoutine: () -> Unit, openSettings: () -> Unit) {
    val context=LocalContext.current
    val today=LocalDate.now()
    val prefs=context.getSharedPreferences("reminders_${session.uid}",android.content.Context.MODE_PRIVATE)
    val enabled=prefs.getBoolean("enabled",true)
    val allowed=AndroidInteractions.notificationsAllowed(context)
    val tasks = reminderVisibleTasks(data.records, session.name, today)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { SectionHero("Lembretes", "Seu dia, sem cobranças.",ReminderBell) }
        item { QuietCard("Notificações no aparelho", when {
            !allowed -> "O Android ainda não permite notificações."
            !enabled -> "Os lembretes estão desligados nas suas preferências."
            else -> "Lembretes habilitados. A entrega depende das permissões e da bateria do aparelho."
        }) { TextButton(onClick=openSettings) { Text("Ajustar notificações e silêncio") } } }
        item { Text("Pendências de hoje",style=MaterialTheme.typography.titleLarge) }
        if(tasks.isEmpty()) item { QuietCard("Tudo em dia", "Nenhuma atividade pendente para hoje.") { TextButton(onClick=openRoutine) { Text("Abrir rotina") } } }
        items(tasks,key={it.id}) { task -> QuietCard(task.text("title"),"${if(task.flag("needsTimeReview") || task.text("time").isBlank()) "Horário a confirmar" else task.text("time")} · ${task.text("owner","Ambos")}") {
            TextButton(onClick=openRoutine) { Text("Ver na rotina") }
        } }
    }
}
