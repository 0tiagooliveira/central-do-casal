package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.*
import java.time.LocalDate

@Composable
internal fun LifeOverview(data: DataState, session: SessionState, onRoutine: () -> Unit, onPlans: () -> Unit, onMoments: () -> Unit, onShopping: () -> Unit) {
    val day = LocalDate.now()
    val tasks = data.records.filter { it.kind == "task" && occurs(it, day) }
    val remainingTasks = tasks.count { task -> data.records.none { it.kind == "occurrence" && it.id == occurrenceId(task.id, day) && it.flag("done") } }
    val steps = data.records.filter { it.kind == "plan" }.flatMap { (it.fields["steps"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList() }
    val pendingSteps = steps.count { it["done"] == false }
    val unknownSteps = steps.count { it["done"] !is Boolean }
    val pendingProducts = data.baskets.values.sumOf { basket -> basket.entries.count { !it.bought } }
    val moments = data.records.count { it.kind == "moment" && !it.isPrivate }
    val bills = data.records.filter { it.kind == "plannedBill" }
    val unknownBills = bills.count { it.fields["amount"] !is Number }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Vida em andamento", style = MaterialTheme.typography.titleMedium)
            OverviewRow("Executar hoje", "$remainingTasks tarefas pendentes", onRoutine)
            OverviewRow("Dar o próximo passo", "$pendingSteps etapas pendentes${if (unknownSteps > 0) " · $unknownSteps a confirmar" else ""}", onPlans)
            OverviewRow("Preparar a compra", "$pendingProducts produtos pendentes", onShopping)
            OverviewRow("Guardar o que aconteceu", "$moments ${if (session.local) "momentos neste aparelho" else "momentos compartilhados"}", onMoments)
            if (bills.isNotEmpty()) Text("${bills.size} compromissos previstos · $unknownBills valores a confirmar. Pagamentos ainda não conciliados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverviewRow(title: String, detail: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 9.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
