package com.centraldocasal.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Record
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

private val annualMonths = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
private fun annualMoney(cents: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
private fun Record.documentedTotal(): Long? = (fields["total"] as? Number)?.toLong()
private fun Record.categoryTotals(): List<Pair<String, Long>> = rows("categories").mapNotNull { row ->
    val title = (row["title"] as? String)?.takeIf { it.isNotBlank() }
    val total = (row["total"] as? Number)?.toLong()
    if (title != null && total != null) title to total else null
}

/** Only documented receipts contribute. Missing amounts remain unknown. */
@Composable
internal fun AnnualPurchasesScreen(data: DataState, onSelect: () -> Unit = {}) {
    val documented = data.records.filter { it.kind == "documentedMonth" }.mapNotNull { record ->
        runCatching { YearMonth.parse(record.text("month")) }.getOrNull()?.let { it to record }
    }.toMap()
    val years = documented.keys.map { it.year }.distinct().sortedDescending().ifEmpty { listOf(2026) }
    var year by rememberSaveable { mutableStateOf(years.first()) }
    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var yearsOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    LaunchedEffect(years) { if (year !in years) { year = years.first(); selectedMonth = null; category = null } }
    val months = (1..12).map { documented[YearMonth.of(year, it)] }
    val categories = months.filterNotNull().flatMap { it.categoryTotals().map { entry -> entry.first } }.distinct().sorted()
    LaunchedEffect(categories) { if (category != null && category !in categories) category = null }
    val known = months.mapNotNull { it?.documentedTotal() }
    val total = known.sum()
    val notes = months.filterNotNull().sumOf { it.number("notes") }
    val values = months.map { record -> if (category == null) record?.documentedTotal() else record?.categoryTotals()?.firstOrNull { it.first == category }?.second }
    val scopeRecords = if (selectedMonth == null) months.filterNotNull() else listOfNotNull(months[selectedMonth!! - 1])
    val categorySummary = scopeRecords.flatMap { it.categoryTotals() }.groupBy { it.first }.map { (title, entries) -> title to entries.sumOf { it.second } }.sortedByDescending { it.second }
    fun selectMonth(month: Int) { selectedMonth = month; onSelect() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Um ano de compras", style = MaterialTheme.typography.headlineMedium)
                Text("As notas contam uma parte da história de vocês.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { yearsOpen = true }) { Text("Ano $year") }
                    DropdownMenu(expanded = yearsOpen, onDismissRequest = { yearsOpen = false }) {
                        years.forEach { option -> DropdownMenuItem(text = { Text(option.toString()) }, onClick = { year = option; selectedMonth = null; category = null; yearsOpen = false; onSelect() }) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total documentado • $year", style = MaterialTheme.typography.labelLarge)
                    Text(if (known.isEmpty()) "A confirmar" else annualMoney(total), style = MaterialTheme.typography.headlineLarge)
                    Text("${known.size} de 12 meses com valores • $notes notas", style = MaterialTheme.typography.bodySmall)
                    if (known.isNotEmpty()) {
                        val average = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(known.size.toLong()), 0, RoundingMode.HALF_UP).toLong()
                        Text("Média dos meses documentados: ${annualMoney(average)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Cobertura parcial. Meses sem valores ficam a confirmar. Contas previstas e lançamentos manuais não entram neste total.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("O ritmo do ano", style = MaterialTheme.typography.titleMedium)
                    Box {
                        OutlinedButton(onClick = { categoriesOpen = true }) { Text(category ?: "Todas as categorias") }
                        DropdownMenu(expanded = categoriesOpen, onDismissRequest = { categoriesOpen = false }) {
                            DropdownMenuItem(text = { Text("Todas as categorias") }, onClick = { category = null; categoriesOpen = false; onSelect() })
                            categories.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoriesOpen = false; onSelect() }) }
                        }
                    }
                    AnnualBars(values, selectedMonth, onMonth = ::selectMonth)
                    Text("Toque em uma barra ou escolha um mês abaixo. Traços indicam valores ausentes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FilterChip(selected = selectedMonth == null, onClick = { selectedMonth = null; onSelect() }, label = { Text("Ano inteiro") })
                        annualMonths.forEachIndexed { index, month -> FilterChip(selected = selectedMonth == index + 1, onClick = { selectMonth(index + 1) }, modifier = Modifier.semantics { contentDescription = "$month de $year, ${values[index]?.let(::annualMoney) ?: "valor a confirmar"}" }, label = { Text(month.take(3)) }) }
                    }
                }
            }
        }
        item {
            Text(if (selectedMonth == null) "Categorias do ano" else "Categorias • ${annualMonths[selectedMonth!! - 1]}", style = MaterialTheme.typography.titleMedium)
            Text("Valores documentados com rateio estimado de descontos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val visibleCategories = categorySummary.filter { category == null || it.first == category }
        if (visibleCategories.isEmpty()) item { Text("Ainda sem valores de categorias para esta seleção.", style = MaterialTheme.typography.bodyMedium) }
        items(visibleCategories, key = { it.first }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(annualMoney(entry.second), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("Mês a mês", style = MaterialTheme.typography.titleMedium) }
        items((1..12).toList(), key = { it }) { month ->
            val record = months[month - 1]
            Card(onClick = { selectMonth(month) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selectedMonth == month) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${annualMonths[month - 1]} • $year", style = MaterialTheme.typography.titleMedium)
                    Text(values[month - 1]?.let(::annualMoney) ?: "Valor a confirmar", style = MaterialTheme.typography.titleLarge)
                    Text(if (record == null) "Sem documentos carregados" else "${record.number("notes")} notas documentadas${if (category != null) " • $category" else ""}", style = MaterialTheme.typography.bodySmall, color = if (selectedMonth == month) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AnnualBars(values: List<Long?>, selectedMonth: Int?, onMonth: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val maximum = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1L)
    val description = "Gráfico de valores documentados. " + annualMonths.mapIndexed { index, month -> "$month: ${values[index]?.let(::annualMoney) ?: "a confirmar"}" }.joinToString(". ")
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }.pointerInput(values, onMonth) {
        detectTapGestures { point -> onMonth((point.x / size.width.toFloat() * 12).toInt().coerceIn(0, 11) + 1) }
    }) {
        val slot = size.width / 12f
        val baseY = size.height - 8.dp.toPx()
        drawLine(baseline, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val left = slot * index + slot * .2f
            val width = slot * .6f
            if (value == null) {
                drawLine(outline, Offset(left, baseY - 8.dp.toPx()), Offset(left + width, baseY - 8.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            } else {
                val height = ((value.coerceAtLeast(0L).toDouble() / maximum.toDouble()).toFloat() * (baseY - 12.dp.toPx())).coerceAtLeast(2.dp.toPx())
                drawRoundRect(if (selectedMonth == index + 1) primary else secondary.copy(alpha = .75f), Offset(left, baseY - height), Size(width, height), CornerRadius(4.dp.toPx()))
            }
        }
    }
}
