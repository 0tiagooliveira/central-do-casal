package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val canRecord = day == LocalDate.now() && (session.local || session.household.isNotBlank())
    var showHistory by rememberSaveable(day.toString()) { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Cuidados · Lucca e Mel", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (events.isEmpty()) "Nenhum registro neste dia" else
                            "${events.size} ${if (events.size == 1) "cuidado registrado" else "cuidados registrados"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canRecord) {
                    Button(onClick = { showActions = true }, enabled = !busy) {
                        Text("Registrar")
                    }
                }
            }
            if (events.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    careKinds.forEach { kind ->
                        Text(
                            "${kind.label}: ${events.count { it.text("careType") == kind.id }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showHistory) "Ocultar histórico" else "Ver histórico de cuidados")
            }
            if (showHistory) {
                if (events.isEmpty()) {
                    Text("Os registros aparecerão aqui, com horário e responsável.",
                        style = MaterialTheme.typography.bodySmall)
                }
                events.forEach { event ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(careLabel(event), style = MaterialTheme.typography.bodyMedium)
                            Text("${careTime(event)} · ${event.text("actorName", "Conta do casal")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (event.text("actorUid") == session.uid) {
                            TextButton(enabled = !busy, onClick = {
                                vm.action("Registro desfeito") { vm.repo.remove(event) }
                            }) { Text("Desfazer") }
                        }
                    }
                }
            }
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("Qual cuidado foi realizado?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registre com o horário atual. Sem pontuação ou comparação.",
                        style = MaterialTheme.typography.bodySmall)
                    careKinds.forEach { kind ->
                        FilledTonalButton(
                            onClick = {
                                if (canRecord) {
                                    val occurredAt = System.currentTimeMillis()
                                    showActions = false
                                    vm.action("${kind.label} registrada") {
                                        vm.repo.save(Record(
                                            id = "care-${session.uid}-${UUID.randomUUID()}",
                                            kind = "careEvent",
                                            isPrivate = false,
                                            fields = mapOf(
                                                "careType" to kind.id,
                                                "subject" to kind.subject,
                                                "actorUid" to session.uid,
                                                "actorName" to session.name,
                                                "occurredAt" to occurredAt,
                                                "date" to LocalDate.now().toString()
                                            )
                                        ))
                                    }
                                }
                            },
                            enabled = !busy && canRecord,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(kind.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showActions = false }) { Text("Fechar") } }
        )
    }
}
