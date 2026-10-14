package com.centraldocasal.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

private fun precisePrice(value: BigDecimal) = "R$ " + value.stripTrailingZeros().toPlainString().replace('.', ',')

@Composable
internal fun PriceHistory(record: Record) {
    val groups = remember(record) { comparePriceGroups(record.rows("history").map { ObservedUnitPrice(it.str("date"),it.str("unidade"),it.str("preco_unitario_reais")) }) }
    var unit by remember(record.id) { mutableStateOf(groups.keys.firstOrNull().orEmpty()) }
    val comparison=groups[unit]
    if(comparison==null) { Text("Ainda não há duas observações comparáveis de preço.",style=MaterialTheme.typography.bodySmall); return }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { groups.keys.forEach { label -> FilterChip(unit==label,{unit=label},label={Text("Por $label")}) } }
    val color=when(comparison.trend) { PriceTrend.LOWER -> Color(0xFF147549); PriceTrend.HIGHER -> MaterialTheme.colorScheme.error; PriceTrend.SAME -> MaterialTheme.colorScheme.primary }
    Text(when(comparison.trend) { PriceTrend.LOWER -> "Ficou mais barato"; PriceTrend.HIGHER -> "Ficou mais caro"; PriceTrend.SAME -> "Preço estável" },color=color,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
    Text("${precisePrice(comparison.first.price)} → ${precisePrice(comparison.last.price)} por ${comparison.unit}")
    comparison.percent?.let { Text("${it.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')}% desde ${comparison.first.date}",color=color) }
    val points=comparison.points
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val low=points.minOf { it.price }.toDouble(); val high=points.maxOf { it.price }.toDouble(); val range=(high-low).takeIf { it>0 } ?: 1.0
        val offsets=points.mapIndexed { index,p -> Offset(8f+(size.width-16f)*index/(points.size-1), size.height-10f-((p.price.toDouble()-low)/range*(size.height-20f)).toFloat()) }
        offsets.zipWithNext().forEach { (a,b) -> drawLine(color,a,b,4f) }; offsets.forEach { drawCircle(color,5f,it) }
    }
    Text("Mesmo código e loja; unidades comparadas separadamente. Preço unitário da nota, antes do rateio estimado de descontos.",style=MaterialTheme.typography.bodySmall)
    points.forEach { Text("${it.date} · ${precisePrice(it.price)}/${it.unit}",style=MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun MatchReviewCard(vm: CentralViewModel, data: DataState, suggestion: Record, busy: Boolean,onLinked:(Record)->Unit={},onOpen:(Record)->Unit={}) {
    val skuId=suggestion.text("skuId")
    val link=data.records.firstOrNull { it.kind=="catalogLink" && it.id==skuId }
    var choosing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Record?>(null) }
    var separate by remember { mutableStateOf(false) }
    var why by rememberSaveable(suggestion.id) { mutableStateOf(false) }
    val sourceSku=data.records.firstOrNull { it.kind=="catalogSku"&&it.id==skuId }
    val fiscalName=sourceSku?.text("title").orEmpty().ifBlank { suggestion.text("title") }
    var displayName by rememberSaveable(skuId, fiscalName) { mutableStateOf(sourceSku?.text("displayName").orEmpty().ifBlank { suggestedFiscalName(fiscalName) }) }
    val linkedBase=data.records.firstOrNull { it.kind=="catalogItem" && it.id==link?.text("baseItemId") }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(displayName,style=MaterialTheme.typography.titleMedium)
        Text("Sugestão · confiança ${suggestion.text("confidence")}",style=MaterialTheme.typography.labelSmall)
        sourceSku?.let { sku ->
            Text("Na nota: $fiscalName",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(displayName,{ displayName=it },label={Text("Nome sugerido para organizar")},supportingText={Text(fiscalNameSupport(fiscalName))},singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=!busy,onClick={ displayName=suggestedFiscalName(fiscalName) }) { Text("Melhorar sugestão") }
                TextButton(enabled=!busy && displayName.trim()!=sku.text("displayName"),onClick={ vm.action("Nome organizado") { vm.repo.saveCatalogDisplayName(sku,displayName) } }) { Text("Salvar nome") }
                TextButton(onClick={onOpen(sku)}) { Text("Ver compras da nota") }
            }
            Text(sku.text("store"),style=MaterialTheme.typography.bodySmall)
        }
        if(link!=null) Text(if(link.text("status")=="separate") "Mantido separado por vocês" else "Vinculado: ${data.records.firstOrNull { it.id==link.text("baseItemId") && it.kind=="catalogItem" }?.text("title") ?: "produto da base"}",color=MaterialTheme.colorScheme.primary)
        if(linkedBase!=null) {
            Text("${productPurchases(linkedBase,data.records).size} compras na ficha do produto",style=MaterialTheme.typography.bodySmall)
            Button(onClick={onOpen(linkedBase)}) { Text("Ver histórico de compras") }
        }
        if(link==null&&suggestion.text("note").isNotBlank()) { TextButton(onClick={why=!why}) { Text(if(why) "Recolher explicação" else "Por que esta sugestão?") };if(why) Text(suggestion.text("note"),style=MaterialTheme.typography.bodySmall) }
        if(link==null) suggestion.rows("candidates").distinctBy { it.str("baseItemId") }.filter { candidate -> !isGenericCatalogBase(candidate.str("title")) }.forEach { candidate ->
            OutlinedButton(enabled=!busy,onClick={pending=data.records.firstOrNull { it.kind=="catalogItem" && it.id==candidate.str("baseItemId") }}) { Text("Vincular a ${candidate.str("title")}") }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(link==null && sourceSku!=null) TextButton(enabled=!busy,onClick={
                val name=displayName
                vm.action("Produto específico criado") {
                    val base=vm.repo.createCatalogBaseFromSku(sourceSku,name)
                    vm.repo.saveCatalogLink(skuId,base.id,"confirmed")
                    onLinked(base)
                }
            }) { Text("Criar produto específico") }
            TextButton(onClick={choosing=true},enabled=!busy) { Text(if(link==null) "Escolher outro produto" else "Alterar associação") }
            if(link?.text("status")!="separate") TextButton(onClick={separate=true},enabled=!busy) { Text("Manter separado") }
        }
    } }
    if(choosing) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={choosing=false},title={Text("Escolher produto da base")},text={Column { OutlinedTextField(search,{search=it},label={Text("Buscar")}); LazyColumn(Modifier.heightIn(max=320.dp)) { items(data.records.filter { it.kind=="catalogItem" && !isGenericCatalogBase(it.text("title")) && it.text("title").contains(search,true) }.sortedBy { it.text("title") },key={it.id}) { record -> TextButton(onClick={pending=record;choosing=false}) { Text(record.text("title")) } } } }},confirmButton={TextButton(onClick={choosing=false}) { Text("Voltar") }})
    }
    if(pending!=null||separate) AlertDialog(onDismissRequest={pending=null;separate=false},title={Text("Confirmar organização")},text={Text(if(separate) "Manter este produto separado da base?" else "Associar ${suggestion.text("title")} a ${pending?.text("title")}? Essa escolha não altera as notas originais nem mistura preços de códigos diferentes.")},confirmButton={Button(enabled=!busy,onClick={val target=pending;val base=target?.id.orEmpty(); val status=if(separate) "separate" else "confirmed"; vm.action("Produto organizado") { vm.repo.saveCatalogLink(skuId,base,status,link?.number("revision")); pending=null;separate=false;if(status=="confirmed"&&target!=null) onLinked(target) }}) { Text("Confirmar") }},dismissButton={TextButton(onClick={pending=null;separate=false}) { Text("Voltar") }})
}
