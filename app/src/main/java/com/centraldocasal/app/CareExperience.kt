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

    QuietCard(
        "Cuidados registrados",
        if (day == today) "Registros reais de hoje. Sem pontos, metas ou competição." else "Histórico de ${day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}."
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            careKinds.forEach { kind ->
                val count = events.count { it.text("careType") == kind.id }
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("${kind.label}: $count") }
                )
            }
        }

        if (day == today) {
            if (!canRecord) {
                Text(
                    "Conecte o espaço do casal para registrar cuidados compartilhados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Registrar agora", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    careKinds.forEach { kind ->
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = {
                                val occurredAt = System.currentTimeMillis()
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
                                                "occurredAt" to occurredAt,
                                                "date" to LocalDate.now().toString()
                                            )
                                        )
                                    )
                                }
                            }
                        ) { Text(kind.label) }
                    }
                }
            }
        }

        if (events.isEmpty()) {
            Text(
                "Nenhum cuidado registrado neste dia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            HorizontalDivider()
            events.take(12).forEach { event ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(careLabel(event), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${careTime(event)} · ${event.text("actorName", "Conta do casal")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (event.text("actorUid") == session.uid) {
                        TextButton(
                            enabled = !busy,
                            onClick = { vm.action("Registro desfeito") { vm.repo.remove(event) } }
                        ) { Text("Desfazer") }
                    }
                }
            }
            if (events.size > 12) {
                Text(
                    "${events.size - 12} registro(s) anterior(es) neste dia.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
