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

/** Personal timeline counters are distinct from the household's historical totals. */
internal data class LifeOverviewSummary(
    val pendingTasks: Int,
    val pendingSteps: Int,
    val unreviewedSteps: Int,
    val pendingProducts: Int,
    val moments: Int,
    val plannedBills: Int,
    val unconfirmedBills: Int
)

internal fun lifeOverviewSummary(
    data: DataState, name: String, local: Boolean, day: LocalDate
): LifeOverviewSummary {
    val tasks = data.records.filter { it.kind == "task" && occurs(it,day) && taskBelongsTo(it,name) }
    val pendingTasks = tasks.count { task ->
        data.records.none {
            it.kind == "occurrence" && it.id == occurrenceId(task.id,day) && it.flag("done")
        }
    }
    val steps = data.records.filter { it.kind == "plan" }
        .flatMap { (it.fields["steps"] as? List<*>)?.filterIsInstance<Map<*,*>>().orEmpty() }
    val bills = data.records.filter {
        it.kind == "plannedBill" && it.text("month") == java.time.YearMonth.from(day).toString()
    }
    return LifeOverviewSummary(
        pendingTasks = pendingTasks,
        pendingSteps = steps.count { it["done"] == false },
        unreviewedSteps = steps.count { it["done"] !is Boolean },
        pendingProducts = data.baskets.values.sumOf { basket -> basket.entries.count { !it.bought } },
        moments = data.records.count { it.kind == "moment" && (local || !it.isPrivate) },
        plannedBills = bills.size,
        unconfirmedBills = bills.count { it.fields["amount"] !is Number }
    )
}

@Composable
internal fun LifeOverview(data: DataState, session: SessionState, onRoutine: () -> Unit, onPlans: () -> Unit, onMoments: () -> Unit, onShopping: () -> Unit) {
    val summary = lifeOverviewSummary(data,session.name,session.local,LocalDate.now())
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Vida em andamento", style = MaterialTheme.typography.titleMedium)
            OverviewRow("Executar hoje", "${summary.pendingTasks} tarefas pendentes", onRoutine)
            OverviewRow("Dar o próximo passo", "${summary.pendingSteps} etapas pendentes${if (summary.unreviewedSteps > 0) " · ${summary.unreviewedSteps} a confirmar" else ""}", onPlans)
            OverviewRow("Preparar a compra", "${summary.pendingProducts} produtos pendentes", onShopping)
            OverviewRow("Guardar o que aconteceu", "${summary.moments} ${if (session.local) "momentos neste aparelho" else "momentos compartilhados"}", onMoments)
            if (summary.plannedBills > 0) Text("${summary.plannedBills} compromissos previstos neste mês · ${summary.unconfirmedBills} valores a confirmar. Pagamentos ainda não conciliados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
