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

@Composable
internal fun ReminderInbox(session: SessionState, data: DataState, openRoutine: () -> Unit, openSettings: () -> Unit) {
    val context=LocalContext.current
    val today=LocalDate.now()
    val prefs=context.getSharedPreferences("reminders_${session.uid}",android.content.Context.MODE_PRIVATE)
    val enabled=prefs.getBoolean("enabled",true)
    val allowed=AndroidInteractions.notificationsAllowed(context)
    val tasks=data.records.filter { task -> task.kind=="task" && occurs(task,today) &&
        data.records.none { it.kind=="occurrence" && it.id==occurrenceId(task.id,today) && it.flag("done") } }
        .sortedBy { it.text("time").ifBlank { "99:99" } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { SectionHero("Lembretes", "Seu dia, sem cobranças.",ReminderBell) }
        item { QuietCard("Notificações no aparelho", when {
            !allowed -> "O Android ainda não permite notificações."
            !enabled -> "Os lembretes estão desligados nas suas preferências."
            else -> "Lembretes habilitados. A entrega depende das permissões e da bateria do aparelho."
        }) { TextButton(onClick=openSettings) { Text("Ajustar notificações e silêncio") } } }
        item { Text("Pendências de hoje",style=MaterialTheme.typography.titleLarge) }
        if(tasks.isEmpty()) item { QuietCard("Tudo em dia", "Nenhuma atividade pendente para hoje.") { TextButton(onClick=openRoutine) { Text("Abrir rotina") } } }
        items(tasks,key={it.id}) { task -> QuietCard(task.text("title"),"${task.text("time").ifBlank { "Sem horário" }} · ${task.text("owner","Ambos")}") {
            TextButton(onClick=openRoutine) { Text("Ver na rotina") }
        } }
    }
}
