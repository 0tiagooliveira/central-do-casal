package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class CareKind(val id: String, val label: String, val subject: String)

internal val careKinds = listOf(
    CareKind("diaper", "Troca de fralda", "Lucca"),
    CareKind("bath", "Banho do Lucca", "Lucca"),
    CareKind("petFood", "Ração da Mel", "Mel")
)

internal fun careEventsForDay(records: List<Record>, day: LocalDate): List<Record> =
    records.filter { it.kind == "careEvent" && it.text("date") == day.toString() }
        .sortedByDescending { it.number("occurredAt") }

private fun careLabel(record: Record): String =
    careKinds.firstOrNull { it.id == record.text("careType") }?.label ?: "Cuidado registrado"

private fun careTime(record: Record): String =
    runCatching {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(record.number("occurredAt")))
    }.getOrDefault("--:--")


@Composable
internal fun CareEventsPanel(
    vm: CentralViewModel,
    session: SessionState,
    data: DataState,
    day: LocalDate
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val events = careEventsForDay(data.records, day)
    val today = LocalDate.now()
    val canRecord = session.local || session.household.isNotBlank()
    var showHistory by rememberSaveable(day.toString()) { mutableStateOf(false) }
    val icons = mapOf("diaper" to "🍼", "bath" to "🛁", "petFood" to "🐾")
    val labels = mapOf("diaper" to "Fralda", "bath" to "Banho", "petFood" to "Ração")

    fun register(kind: CareKind) {
        val now = System.currentTimeMillis()
        vm.action("${kind.label} registrada") {
            vm.repo.save(
                Record(
                    id = "care-${session.uid}-${UUID.randomUUID()}",
                    kind = "careEvent",
                    isPrivate = false,
                    fields = mapOf(
                        "careType" to kind.id,
                        "subject" to kind.subject,
                        "actorUid" to session.uid,
                        "actorName" to session.name,
                        "occurredAt" to now,
                        "date" to today.toString()
                    )
                )
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cuidados registrados", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (day == today) "Registre cada cuidado com um toque"
                        else day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${events.size} hoje".takeIf { day == today } ?: "${events.size} registros",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            if (day == today) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    careKinds.forEach { kind ->
                        val count = events.count { it.text("careType") == kind.id }
                        Card(
                            onClick = { register(kind) },
                            enabled = !busy && canRecord,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(icons[kind.id] ?: "♡", style = MaterialTheme.typography.titleLarge)
                                Text(labels[kind.id] ?: kind.label, style = MaterialTheme.typography.labelLarge)
                                Text("$count registrado(s)", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+ Registrar", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (!canRecord) Text("Conecte o espaço do casal para compartilhar os cuidados.",
                    style = MaterialTheme.typography.bodySmall)
            }

            if (events.isEmpty()) {
                Text("Ainda não há registros neste dia.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Histórico do dia", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (events.size > 3) TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if(showHistory) "Mostrar menos" else "Ver todos")
                    }
                }
                (if(showHistory) events else events.take(3)).forEach { event ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(icons[event.text("careType")] ?: "♡", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(careLabel(event), style = MaterialTheme.typography.bodyMedium)
                            Text("${careTime(event)} · ${event.text("actorName", "Conta do casal")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (event.text("actorUid") == session.uid) {
                            TextButton(enabled = !busy,
                                onClick = { vm.action("Registro desfeito") { vm.repo.remove(event) } }) { Text("Desfazer") }
                        }
                    }
                }
            }
        }
    }
}
