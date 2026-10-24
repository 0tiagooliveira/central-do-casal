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
    var testResult by remember(session.uid) { mutableStateOf<String?>(null) }
    var refreshKey by remember(session.uid) { mutableIntStateOf(0) }
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
        item {
            QuietCard("Conferir entrega real", "Teste o canal de tarefas e um alarme do Android com o aplicativo fechado.") {
                OutlinedButton(onClick = {
                    testResult = Reminders.scheduleDeliveryTest(context,session.uid,session.name)
                    refreshKey++
                }, enabled = allowed && enabled) { Text("Testar aviso em 2 minutos") }
                testResult?.let { Text(it, style=MaterialTheme.typography.bodySmall) }
                if(!allowed || !enabled) Text("Confira as permissões e ative os lembretes nas Configurações.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
        }
        item { Text("Pendências de hoje",style=MaterialTheme.typography.titleLarge) }
        if(tasks.isEmpty()) item { QuietCard("Tudo em dia", "Nenhuma atividade pendente para hoje.") { TextButton(onClick=openRoutine) { Text("Abrir rotina") } } }
        items(tasks,key={it.id}) { task ->
            val check = remember(task, session.uid, session.name, refreshKey) {
                Reminders.inspect(context,session.uid,task,session.name)
            }
            QuietCard(task.text("title"),"${task.text("time").ifBlank { "Sem horário" }} · ${task.text("owner","Ambos")}") {
                task.text("note").takeIf { it.isNotBlank() }?.let {
                    Text(it,style=MaterialTheme.typography.bodySmall)
                }
                Text(check.status,style=MaterialTheme.typography.bodySmall,
                    color=if(check.plannedAt == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=openRoutine) { Text("Ver na rotina") }
                    if(check.plannedAt == null) TextButton(onClick=openSettings) { Text("Corrigir aviso") }
                }
            }
        }
    }
}
