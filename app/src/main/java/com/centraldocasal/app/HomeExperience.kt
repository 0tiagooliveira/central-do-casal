package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun homeMoney(value: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(value, 2))

@Composable
internal fun HomeExperienceScreen(vm: CentralViewModel, session: SessionState, data: DataState, navigate: (String) -> Unit, selectTab: (String) -> Unit, requestLocationPermission: () -> Unit = {}) {
    val day = LocalDate.now()
    val tasks = data.records.filter { it.kind == "task" && occurs(it, day) }.sortedBy { if (it.flag("needsTimeReview")) "99:99" else it.text("time").ifBlank { "99:99" } }
    fun completed(task: Record) = data.records.any { it.kind == "occurrence" && it.id == occurrenceId(task.id, day) && it.flag("done") }
    val completedCount = tasks.count(::completed)
    val reviews = data.records.count { it.kind == "task" && (it.flag("needsReview") || it.flag("needsTimeReview")) }
    val unmatchedPurchases=data.records.count { it.kind=="matchSuggestion" }
    val pendingPurchases=data.baskets.values.sumOf { basket -> basket.entries.count { !it.bought } }
    val careToday = careEventsForDay(data.records, day)
    val memories = data.records.count { it.kind == "quest" && !it.flag("cancelled") && (it.fields["completed"] as? List<*>)?.distinct()?.size == 2 }
    val converted=data.records.filter { it.kind=="plan" }.map { it.text("sourcePlanId") }.filter { it.isNotBlank() }.toSet()
    val projectCount=data.records.count { it.kind=="plan" || (it.kind=="importedPlan"&&it.id !in converted) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val view = LocalView.current
    val options = LocalExperience.current
    fun touch(action: () -> Unit) { AndroidInteractions.haptic(view, options.haptics); AndroidInteractions.clickSound(view, options.sounds); action() }
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("NOSSO DIA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Olá, ${session.name.substringBefore(' ').ifBlank { "vocês" }} ♡", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Uma vida mais leve, organizada a dois.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { WeatherCard(requestLocationPermission) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("", "${tasks.size}", "Tarefas de hoje · $completedCount concluídas", CalendarLine, accent, { touch { selectTab("Rotina") } }, Modifier.weight(1f))
                SummaryTile("", "$pendingPurchases", "Itens nas compras", BasketLine, accent, { touch { selectTab("Compras") } }, Modifier.weight(1f))
            }
        }
        if (careToday.isNotEmpty()) item {
            HomePanel("Cuidados registrados hoje") {
                Text("Registros do dia, sem metas ou pontuação.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    careKinds.forEach { kind ->
                        val count = careToday.count { it.text("careType") == kind.id }
                        if (count > 0) SuggestionChip(
                            onClick = { touch { selectTab("Rotina") } },
                            label = { Text("${kind.label}: $count") }
                        )
                    }
                }
                TextButton(onClick = { touch { selectTab("Rotina") } }, contentPadding=PaddingValues(0.dp)) {
                    Text("Ver registros na Rotina")
                }
            }
        }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={ touch { navigate("Segurança") } },colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("SOS rápido") }
                OutlinedButton(onClick={ touch { navigate("Casa") } }) { Text("Casa conectada") }
                OutlinedButton(onClick={ touch { navigate("Lembretes") } }) { Text("Lembretes") }
            }
        }
        if(reviews>0 || unmatchedPurchases>0 || pendingPurchases>0) item {
            HomePanel("Ajuda para decidir") {
                Text("A Central encontrou ações que evitam esquecer, gastar sem referência ou deixar dados incompletos.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(reviews>0) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("$reviews rotina(s) precisam de horário ou revisão",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={touch { navigate("Revisar rotinas")}}) { Text("Resolver") } }
                if(unmatchedPurchases>0) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("$unmatchedPurchases item(ns) de nota aguardam vínculo",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={touch { selectTab("Compras")}}) { Text("Organizar") } }
                if(pendingPurchases>0) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("$pendingPurchases item(ns) aguardam decisão de compra",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={touch { selectTab("Compras")}}) { Text("Ver lista") } }
            }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Próximas atividades", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { touch { selectTab("Rotina") } }) { Text("Ver meu dia") } } }
        if (tasks.isEmpty()) item { HomePanel("Seu dia está livre") { Text("Nenhuma tarefa ativa para hoje.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items((tasks.filterNot(::completed) + tasks.filter(::completed)).take(4), key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE0E5ED))) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(completed(task), onCheckedChange = { value -> touch { vm.complete(task, day, value) } }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Concluir hoje: ${task.text("title")}" })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(task.text("title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${if (task.flag("needsTimeReview")) "Horário a confirmar" else task.text("time").ifBlank { "Sem horário" }} · ${task.text("owner", "Ambos")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("Projetos", "$projectCount", "planos registrados", PlanLine, accent, { touch { selectTab("Planos") } }, Modifier.weight(1f))
                SummaryTile("Nossa jornada", "$memories", "lembranças a dois", HeartLine, accent, { touch { selectTab("Nós") } }, Modifier.weight(1f))
            }
        }
        if (reviews > 0) item { HomePanel("$reviews rotinas para revisar") { Text("Confirme os dados e horários antes dos lembretes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { touch { navigate("Revisar rotinas") } }, contentPadding = PaddingValues(0.dp)) { Text("Revisar rotinas") } } }
        val nextSteps=data.records.filter { it.kind=="plan" }.flatMap { plan -> plan.rows("steps").filter { it["done"]==false }.map { plan to it } }.take(3)
        if(nextSteps.isNotEmpty()) item { Text("Próximos passos dos planos",style=MaterialTheme.typography.titleMedium) }
        items(nextSteps,key={it.first.id+":"+it.second.str("id")}) { (plan,step) -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) { Checkbox(false,{value -> touch { vm.action { vm.repo.savePlanStep(plan,step.str("id"),completed=value,expectedRevision=plan.number("stageRevision")) } } },enabled=!busy,modifier=Modifier.semantics { contentDescription="Concluir etapa: ${step.str("title")}" });Column(Modifier.weight(1f)) { Text(step.str("title"),style=MaterialTheme.typography.bodyMedium);Text(plan.text("title"),style=MaterialTheme.typography.bodySmall) } } } }
        data.baskets.values.filter { it.budget > 0 && it.entries.isNotEmpty() }.forEach { basket ->
            item(key = "budget-${basket.market}") {
                val bought = basket.entries.filter { it.bought }
                val amounts = bought.mapNotNull { e -> if (e.actual != null && e.unitReviewed && e.quantityReviewed && e.unit in Entry.allowedUnits) runCatching { Money.subtotal(e.actual, e.actualMilli) }.getOrNull() else null }
                val remaining = basket.budget - amounts.sum()
                HomePanel("${basket.market} · orçamento") {
                    Text(if (remaining < 0) "Excedido em ${homeMoney(-remaining)}" else "${if (amounts.size < bought.size) "Saldo parcial" else "Disponível"}: ${homeMoney(remaining)}", style = MaterialTheme.typography.titleMedium, color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(if (amounts.size < bought.size) "Há fichas com valores a confirmar." else "Considera os preços reais confirmados no carrinho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { touch { selectTab("Compras") } }, contentPadding = PaddingValues(0.dp)) { Text("Abrir compras") }
                }
            }
        }
        data.records.firstOrNull { it.kind == "importSummary" }?.let { summary -> item {
            HomePanel("Seu histórico de compras") {
                Text("${summary.number("notes")} notas · ${(summary.fields["total"] as? Number)?.let { homeMoney(it.toLong()) } ?: "total a confirmar"}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { touch { navigate("Ano de compras") } }, contentPadding = PaddingValues(0.dp)) { Text("Explorar o ano") }
            }
        } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Nossa vida","Finanças", "Saúde", "Configurações").forEach { destination -> OutlinedButton(onClick = { touch { navigate(destination) } }) { Text(destination) } } } }
    }
}

@Composable
private fun HomePanel(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0E5ED))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); body() }
    }
}
