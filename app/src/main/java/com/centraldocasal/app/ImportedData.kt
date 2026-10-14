package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.num
import com.centraldocasal.app.domain.str
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun reais(cents: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(cents).movePointLeft(2))
private fun date(value: String) = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrDefault(value)
private fun decimalPrice(value: String): String = runCatching { "R$ " + BigDecimal(value).stripTrailingZeros().toPlainString().replace('.', ',') }.getOrDefault("Preço a revisar")
@Suppress("UNCHECKED_CAST")
internal fun Record.rows(key: String): List<Map<String, Any?>> = fields[key] as? List<Map<String, Any?>> ?: emptyList()

@Composable
internal fun ImportedReceiptCard(receipt: Record,initialExpanded:Boolean=false,onLine:(Map<String,Any?>)->Unit={}) {
    var expanded by remember(receipt.id) { mutableStateOf(initialExpanded) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(receipt.text("title"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(date(receipt.text("date")), style = MaterialTheme.typography.bodyMedium); Text(reais(receipt.number("total")), fontWeight = FontWeight.Bold) }
            Text(if(receipt.flag("detailed")) "${receipt.rows("lines").size} produtos • nota importada" else "Nota sem detalhamento de produtos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { expanded = !expanded }) { Text(if(expanded) "Recolher nota" else "Ver detalhes") }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                receipt.rows("lines").forEach { line ->
                    OutlinedCard(onClick={onLine(line)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(line.str("descricao_padronizada", line.str("descricao_original")), style = MaterialTheme.typography.bodyMedium)
                        Text("${line.str("quantidade").replace('.', ',')} ${line.str("unidade")} • ${decimalPrice(line.str("preco_unitario_reais"))}/${line.str("unidade")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Valor após rateio estimado: ${reais(line.num("valor_liquido_estimado_rateado_centavos"))}", style = MaterialTheme.typography.bodySmall)
                        Text("Toque para ver histórico e comparação",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    } }
                }
                if (!receipt.flag("detailed")) Text("O arquivo contém somente o total. Nenhum produto foi presumido.", style = MaterialTheme.typography.bodySmall)
                Text("Desconto da nota: ${reais(receipt.number("discount"))}", style = MaterialTheme.typography.bodySmall)
                Text(if(receipt.flag("reconciled")) "Pagamento conciliado no arquivo" else "Pagamento ainda não conciliado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ImportedCatalogCard(record: Record,purchases:Int=0, detail: () -> Unit) {
    Card(onClick = detail, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(record.text("displayName").ifBlank { record.text("title") }, style = MaterialTheme.typography.titleMedium)
            Text(if(record.kind == "catalogSku") record.text("store") else record.text("category"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record.kind == "catalogSku") Text("${decimalPrice(record.text("referenceText"))} / ${record.text("unit")} • ${date(record.text("lastDate"))}", style = MaterialTheme.typography.bodySmall)
            else if(purchases==0) Text((record.fields["reference"] as? Number)?.let { "Referência anterior: ${reais(it.toLong())}" } ?: "Sem referência de preço", style = MaterialTheme.typography.bodySmall)
            if(purchases>0) Text("$purchases compras · abrir histórico",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            else if (record.flag("review")) Text("Identificação a revisar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
internal fun DocumentedMonthCard(record: Record) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Notas documentadas", style = MaterialTheme.typography.titleMedium)
            val total=record.fields["total"] as? Number
            Text(total?.let { reais(it.toLong()) } ?: "Sem documentos para este mês", style = MaterialTheme.typography.headlineMedium)
            Text("${record.number("notes")} notas • histórico compartilhado", style = MaterialTheme.typography.bodySmall)
            Text("Cobertura parcial. Esses valores não representam o gasto total da família nem alteram o saldo manual.", style = MaterialTheme.typography.bodySmall)
            record.rows("categories").sortedByDescending { it.num("total") }.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(row.str("title"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(reais(row.num("total")), style = MaterialTheme.typography.bodySmall) } }
            if(total!=null) Text("Categorias usam rateio estimado de descontos; o total vem das notas.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun PlannedBillCard(record: Record) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(record.text("title"), style = MaterialTheme.typography.titleMedium)
            Text("${record.text("owner")} • ${if(record.isPrivate) "Só minha conta" else "Do casal"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val amount=record.fields["amount"] as? Number
            Text(amount?.let { "Previsto: ${reais(it.toLong())}" } ?: "Valor previsto a confirmar", style = MaterialTheme.typography.bodyMedium)
            Text(if(record.text("date").isBlank()) "Vencimento a confirmar" else "Data registrada: ${date(record.text("date"))}", style = MaterialTheme.typography.bodySmall)
            Text("Pagamento não confirmado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            if(record.text("note").isNotBlank()) Text(record.text("note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ImportedPlanCard(record: Record) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.text("title"), style = MaterialTheme.typography.titleMedium)
            Text("Planejamento • ${record.text("status").replace('_',' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(record.text("location").isNotBlank()) Text(record.text("location"), style = MaterialTheme.typography.bodyMedium)
            (record.fields["target"] as? Number)?.let { Text("Teto desejado: ${reais(it.toLong())}", style = MaterialTheme.typography.bodyMedium); Text("Preferência registrada; orçamento ainda não confirmado.", style = MaterialTheme.typography.bodySmall) }
            if(record.text("note").isNotBlank()) Text(record.text("note"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) { Text(if(expanded) "Recolher detalhes" else "Ver preferências e pendências") }
            if(expanded) {
                @Suppress("UNCHECKED_CAST") val original=record.fields["original"] as? Map<String,Any?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST") val preferences=original["preferencias"] as? Map<String,Any?> ?: emptyMap()
                preferences.forEach { (key,value) -> Text("${key.replace('_',' ').replaceFirstChar { it.uppercase() }}: ${when(value){true->"Sim";false->"Não";else->value?:"A confirmar"}}", style = MaterialTheme.typography.bodySmall) }
                (record.fields["pending"] as? List<*>)?.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if(preferences.isEmpty()&&(record.fields["pending"] as? List<*>)?.isEmpty()!=false) Text("Ainda sem detalhes confirmados.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
