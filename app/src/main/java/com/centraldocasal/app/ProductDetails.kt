package com.centraldocasal.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun productDate(value:String)=runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrDefault(value)
private fun productPrice(value:String)=runCatching { "R$ "+BigDecimal(value).let { it.setScale(maxOf(2,it.scale())).toPlainString().replace('.',',') } }.getOrDefault("Preço não informado")
private fun productMoney(cents:Long)=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents,2))

@Composable
internal fun ProductDetailScreen(record:Record,data:DataState,onDismiss:()->Unit,onAdd:(Record)->Unit,onReceipt:(Record)->Unit) {
    ProductDetailScreen(null, false, record, data, onDismiss, onAdd, onReceipt)
}

@Composable
internal fun ProductDetailScreen(vm: CentralViewModel?, busy: Boolean, record:Record,data:DataState,onDismiss:()->Unit,onAdd:(Record)->Unit,onReceipt:(Record)->Unit) {
    BackHandler { onDismiss() }
    val variants=if(record.kind=="catalogSku") listOf(record) else relatedCatalogSkus(record,data.records)
    val bases=data.records.filter { it.kind=="catalogItem" && it.id!=record.id && !isGenericCatalogBase(it.text("title")) }.sortedBy { it.text("title") }
    val links=data.records.filter { it.kind=="catalogLink" }.associateBy { it.id }
    var selectedSku by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    var fixingSku by remember { mutableStateOf<Record?>(null) }
    val selected=variants.firstOrNull { it.id==selectedSku } ?: variants.singleOrNull()
    val purchases=productPurchases(record,data.records).filter { selected==null||it.skuId==selected.id }.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick=onDismiss) { Icon(Icons.Default.ArrowBack,null);Spacer(Modifier.width(8.dp));Text("Fechar") }; Text(record.text("title"),style=MaterialTheme.typography.headlineMedium); Text(record.text("category"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) { Column { Text("Compras documentadas",style=MaterialTheme.typography.labelMedium);Text(purchases.size.toString(),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.testTag("product-purchase-count")) }; Column { Text("Produtos vinculados",style=MaterialTheme.typography.labelMedium);Text(variants.size.toString(),style=MaterialTheme.typography.headlineSmall) } } }
        if(variants.size>1) item { Text("Escolha o produto para comparar preços",style=MaterialTheme.typography.titleSmall); FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(selected==null,{selectedSku=null},label={Text("Todos")}); variants.forEach { sku -> FilterChip(selected?.id==sku.id,{selectedSku=sku.id},label={Text("${sku.text("title")} · ${sku.text("store")}")}) } } }
        if(record.kind=="catalogItem" && vm!=null && variants.isNotEmpty()) item {
            Text("Vínculos desta ficha",style=MaterialTheme.typography.titleLarge)
            Text("Se uma compra caiu no produto errado, corrija aqui. Ao separar, ela deixa de entrar no histórico e no preço deste produto.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(record.kind=="catalogItem" && vm!=null) items(variants,key={"link-"+it.id}) { sku ->
            OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(sku.text("displayName").ifBlank { sku.text("title") },style=MaterialTheme.typography.titleSmall);Text("${sku.text("store")} · ${sku.text("title")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick={fixingSku=sku},enabled=!busy && vm!=null) { Text("Corrigir") }
            } }
        }
        selected?.let { sku -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(sku.text("title"),style=MaterialTheme.typography.titleMedium);Text(sku.text("store"),style=MaterialTheme.typography.bodySmall);PriceHistory(sku) } } } }
        item { Button(onClick={onAdd(selected ?: record)},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.ShoppingCart,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Preparar item para lista") } }
        item { Text("Histórico de compras",style=MaterialTheme.typography.titleLarge) }
        if(purchases.isEmpty()) item { Text(if(variants.isEmpty()) "Ainda não há compras associadas. Confirme a identificação no catálogo para trazer as notas para esta ficha." else "O vínculo foi encontrado. Este produto ainda não tem linhas de compra documentadas.",style=MaterialTheme.typography.bodyMedium) }
        items(purchases,key={it.id}) { purchase ->
            OutlinedCard(Modifier.fillMaxWidth().testTag("product-purchase-${purchase.id}")) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("${productDate(purchase.date)} · ${purchase.store}",style=MaterialTheme.typography.titleSmall)
                Text(purchase.title,style=MaterialTheme.typography.bodyMedium)
                Text("${purchase.quantityText.replace('.',',')} ${purchase.unit} · ${productPrice(purchase.unitPriceText)} / ${purchase.unit}",style=MaterialTheme.typography.bodyMedium)
                purchase.netCents?.let { Text("Valor da linha: ${productMoney(it)}",style=MaterialTheme.typography.labelMedium) }
                val receipt=data.records.firstOrNull { it.id==purchase.receiptId && it.kind in setOf("receipt","importedReceipt") }
                if(receipt!=null) TextButton(onClick={onReceipt(receipt)},modifier=Modifier.testTag("product-receipt-${purchase.id}")) { Text("Abrir nota da compra") }
            } }
        }
        item { Text("Preços são comparados por código, loja e unidade. Vincular organiza as notas existentes; não altera os documentos nem confirma estoque atual.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    fixingSku?.let { sku ->
        val link=links[sku.id]
        AlertDialog(onDismissRequest={fixingSku=null},title={Text("Corrigir vínculo")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(sku.text("displayName").ifBlank { sku.text("title") })
            Text("Escolha o produto correto ou mantenha este item separado. A nota original e os valores serão preservados.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy && vm!=null,onClick={ val model=vm ?: return@TextButton; model.action("Item removido desta ficha") { model.repo.saveCatalogLink(sku.id,"","separate",link?.number("revision")); fixingSku=null } }) { Text("Manter separado") }
            if(bases.isNotEmpty()) Text("Mover para",style=MaterialTheme.typography.labelLarge)
            bases.forEach { base -> TextButton(enabled=!busy && vm!=null,onClick={ val model=vm ?: return@TextButton; model.action("Vínculo corrigido") { model.repo.saveCatalogLink(sku.id,base.id,"confirmed",link?.number("revision")); fixingSku=null } },modifier=Modifier.fillMaxWidth()) { Text(base.text("title"),modifier=Modifier.weight(1f));Text("Vincular") } }
            if(bases.isEmpty()) Text("Cadastre antes um produto específico no catálogo para mover este item.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={fixingSku=null}) { Text("Fechar") }})
    }
}

@Composable
internal fun PurchaseDocumentScreen(record:Record,onDismiss:()->Unit) {
    BackHandler { onDismiss() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onDismiss) { Icon(Icons.Default.ArrowBack,null);Text("Voltar ao produto") } }
        item { if(record.kind=="importedReceipt") ImportedReceiptCard(record,initialExpanded=true) else OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text("Compra de ${productDate(record.text("date"))}",style=MaterialTheme.typography.titleLarge);Text(productMoney(record.number("total")),style=MaterialTheme.typography.headlineMedium);record.rows("entries").map(Entry::from).filter { it.bought }.forEach { Text("${it.title} · ${BigDecimal.valueOf(it.actualMilli,3).stripTrailingZeros().toPlainString().replace('.',',')} ${it.unit} · ${productMoney(it.real())}") } } } }
    }
}
