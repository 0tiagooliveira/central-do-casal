package com.centraldocasal.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Money
import com.centraldocasal.app.domain.PriceTrend
import com.centraldocasal.app.domain.compareShoppingPrice
import com.centraldocasal.app.domain.prepareQuickCartEntry
import com.centraldocasal.app.domain.productPurchases
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun sessionMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
private fun sessionQuantity(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString().replace('.', ',')
private fun Entry.reviewedReal(): Long? = if (bought && actual != null && unitReviewed && unit in Entry.allowedUnits && quantityReviewed && plannedMilli > 0 && actualMilli > 0) runCatching { Money.subtotal(requireNotNull(actual), actualMilli) }.getOrNull() else null
private fun Entry.reviewedEstimate(): Long? = if (estimateKnown && unitReviewed && unit in Entry.allowedUnits && quantityReviewed && plannedMilli > 0) runCatching { Money.subtotal(estimated, plannedMilli) }.getOrNull() else null

@Composable
internal fun ShoppingSessionScreen(
    data: DataState,
    session: SessionState,
    busy: Boolean,
    onEdit: (Entry) -> Unit,
    onAdd: () -> Unit,
    onBudget: () -> Unit,
    onCatalog: () -> Unit,
    onAnnual: () -> Unit,
    onHistory: () -> Unit,
    onPrepareMonth: () -> Unit,
    onFinish: () -> Unit,
    onMarket: (String) -> Unit,
    market: String,
    onFeedback: () -> Unit = {},
    onQuickSave: (Entry) -> Unit = {},
    onReview: () -> Unit = {},
    onQuickAdd: (String) -> Unit = {}
) {
    val basket = data.baskets[market] ?: remember(market) { Basket(market) }
    var mode by rememberSaveable { mutableStateOf("Planejar") }
    var search by rememberSaveable { mutableStateOf("") }
    var quickName by rememberSaveable(market) { mutableStateOf("") }
    fun addQuick() {
        val title = quickName.trim()
        if (title.isNotEmpty() && !busy) {
            onQuickAdd(title)
            quickName = ""
        }
    }
    val motion = LocalExperience.current.motion
    val cart = basket.entries.filter { it.bought }
    val pending = basket.entries.filterNot { it.bought }
    val knownReal = cart.mapNotNull { it.reviewedReal() }
    val spent = knownReal.sum()
    val unknownReal = cart.size - knownReal.size
    val balance = basket.budget - spent
    val estimates = basket.entries.mapNotNull { it.reviewedEstimate() }
    val forecast = estimates.sum()
    val plannedPriceSignals = basket.entries.mapNotNull { entry ->
        compareShoppingPrice(entry, data.records, entry.estimated.takeIf { entry.estimateKnown })
    }
    val plannedHigher = plannedPriceSignals.count { it.trend == PriceTrend.HIGHER }
    val plannedLower = plannedPriceSignals.count { it.trend == PriceTrend.LOWER }
    val allCartReviewed = cart.isNotEmpty() && unknownReal == 0
    val currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM 'de' uuuu", Locale.forLanguageTag("pt-BR")))
    val ratio by animateFloatAsState(if (basket.budget > 0) (spent.toDouble() / basket.budget).toFloat().coerceIn(0f, 1f) else 0f, tween(if (motion) 240 else 0), label = "Orçamento do carrinho")
    fun feedback(action: () -> Unit) { onFeedback(); action() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHero("Compras", "$market · $currentMonth", BasketLine)
                Text(if (session.local) "Lista local neste aparelho" else if (session.household.isBlank()) "Lista ativa na sua conta" else "Lista compartilhada no espaço do casal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceStrip(listOf("Mercado", "Açougue"), market, onSelect = { option -> feedback { onMarket(option); search = "" } })
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .36f)),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O que falta comprar?", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            quickName, { quickName = it },
                            placeholder = { Text("Ex.: Chocolate, leite, pão") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addQuick() }),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(onClick = ::addQuick, enabled = quickName.isNotBlank() && !busy,
                            contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Adicionar à lista")
                        }
                    }
                    Text("Adicione pelo nome. Quantidade, preço e categorias podem ser ajustados depois.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("No carrinho", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sessionMoney(spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Orçamento", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (basket.budget > 0) sessionMoney(basket.budget) else "A definir",
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (basket.budget > 0) LinearProgressIndicator(
                        progress = { ratio }, modifier = Modifier.fillMaxWidth().height(5.dp))
                    if (basket.budget > 0 && balance < 0)
                        Text("Acima do orçamento: ${sessionMoney(-balance)}",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${pending.size} faltando · ${cart.size} no carrinho",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { feedback(onBudget) }, enabled = !busy) { Text("Ajustar limite") }
                    }
                }
            }
        }
        item {
            ChoiceStrip(listOf("Planejar", "No mercado", "Carrinho (${cart.size})"),when(mode) { "Modo Mercado" -> "No mercado"; "Carrinho" -> "Carrinho (${cart.size})"; else -> "Planejar" },{ option -> feedback { mode=when { option.startsWith("Carrinho") -> "Carrinho"; option=="No mercado" -> "Modo Mercado"; else -> "Planejar" };search="" } })
        }
        if (mode == "Planejar") item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { feedback(onPrepareMonth) }, enabled = !busy) {
                    Text("Montar lista do catálogo")
                }
                TextButton(onClick = { feedback(onAdd) }, enabled = !busy) { Text("Mais opções") }
            }
        }
        if (mode == "Modo Mercado") item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No mercado · ${pending.size} faltando", style = MaterialTheme.typography.titleMedium)
                Text("Toque no item, confira preço e quantidade e coloque no carrinho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (mode == "Carrinho") item {
            SessionPanel("Confira antes de concluir") {
                Text("${cart.size} itens · ${sessionMoney(spent)} ${if (unknownReal > 0) "confirmados" else "no carrinho"}", style = MaterialTheme.typography.titleMedium)
                Text("Concluir preserva a compra no histórico. Revise os preços e quantidades abaixo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { feedback(onFinish) }, enabled = !busy && allCartReviewed, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Concluir compra") }
                if (cart.isNotEmpty() && !allCartReviewed) Text("Confirme as fichas pendentes para concluir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (cart.isNotEmpty()) item { ShoppingReconciliationCard(basket,cart,forecast,spent,unknownReal) }
        val entries = (when (mode) { "Modo Mercado" -> pending; "Carrinho" -> cart; else -> basket.entries }).filter { search.isBlank() || it.title.contains(search, true) }
        item { OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar nesta lista") }, shape = RoundedCornerShape(14.dp)) }
        if (entries.isEmpty()) item {
            SessionPanel(if (search.isNotBlank()) "Nenhum item encontrado" else when (mode) { "Carrinho" -> "Seu carrinho está vazio"; "Modo Mercado" -> "Nenhum item pendente"; else -> "Sua lista começa aqui" }) {
                Text(if (search.isNotBlank()) "Tente outro nome de produto." else when (mode) { "Carrinho" -> "No Modo Mercado, confirme o preço e a quantidade de cada produto."; "Modo Mercado" -> if (cart.isNotEmpty()) "Os itens confirmados estão no carrinho." else "Prepare a lista antes de começar a compra."; else -> "Toque em “Montar lista do mês” para decidir, por cômodo, o que comprar, o que já tem e o que ficará de fora." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(mode=="Planejar") {
            val priorityOrder=mapOf("Alta" to 0,"Normal" to 1,"Baixa" to 2)
            val groups=entries.groupBy { entry -> "${entry.recipient.ifBlank { "Casa" }} · ${entry.group.ifBlank { "Essenciais" }}" }
            groups.toSortedMap().forEach { (groupLabel,groupEntries) ->
                val ordered=groupEntries.sortedWith(compareBy<Entry> { priorityOrder[it.priority] ?: 1 }.thenBy { it.subgroup }.thenBy { it.title })
                item(key="need-$groupLabel") { Text(groupLabel,style=MaterialTheme.typography.titleMedium);Text("${ordered.size} ${if(ordered.size==1) "item" else "itens"} · alta prioridade primeiro",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                items(ordered,key={it.id}) { entry -> CompactShoppingRow(entry,data,mode,busy,onEdit={feedback { onEdit(it) }},onSave={feedback { onQuickSave(it) }}) }
            }
        } else items(entries, key = { it.id }) { entry ->
            CompactShoppingRow(entry, data, mode, busy,
                onEdit = { feedback { onEdit(it) } },
                onSave = { feedback { onQuickSave(it) } })
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { feedback(onCatalog) }) { Text("Catálogo") }
                TextButton(onClick = { feedback(onHistory) }) { Text("Histórico de compras") }
                TextButton(onClick = { feedback(onAnnual) }) { Text("Ver o ano") }
                TextButton(onClick = { feedback(onReview) }) { Text("Revisar vínculos") }
            }
        }
    }
}

@Composable
private fun ShoppingReconciliationCard(basket: Basket, cart: List<Entry>, forecast: Long, confirmed: Long, unknown: Int) {
    val cartForecast=cart.sumOf { it.expected() }
    val priceImpact=cart.sumOf { it.priceDifference() }
    val quantityImpact=cart.sumOf { it.quantityDifference() }
    val difference=confirmed-cartForecast
    SessionPanel(if(unknown==0) "Planejado e aferido no caixa" else "Conciliação parcial do caixa") {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Column { Text("Planejado",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(sessionMoney(cartForecast),style=MaterialTheme.typography.titleMedium) }
            Column(horizontalAlignment=Alignment.End) { Text("Confirmado",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(sessionMoney(confirmed),style=MaterialTheme.typography.titleMedium) }
        }
        if(unknown==0) {
            val sign=if(difference>0) "a mais" else if(difference<0) "a menos" else "igual ao planejado"
            Text(if(difference==0L) "O valor do caixa ficou igual ao planejado." else "${sessionMoney(kotlin.math.abs(difference))} $sign para os itens do carrinho.",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=if(difference>0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if(priceImpact!=0L) Text("Preço: ${if(priceImpact>0) "+" else "−"}${sessionMoney(kotlin.math.abs(priceImpact))}",style=MaterialTheme.typography.bodySmall)
            if(quantityImpact!=0L) Text("Quantidade: ${if(quantityImpact>0) "+" else "−"}${sessionMoney(kotlin.math.abs(quantityImpact))}",style=MaterialTheme.typography.bodySmall)
            if(basket.budget>0) Text("Depois do caixa, restam ${sessionMoney(basket.budget-confirmed)} do orçamento.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("Faltam $unknown itens com unidade, quantidade ou preço real. A diferença final aparece após conferir a etiqueta ou a nota.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(forecast>cartForecast) Text("A previsão total inclui itens que ainda não foram para o carrinho.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactShoppingRow(entry: Entry, data: DataState, mode: String, busy: Boolean, onEdit: (Entry) -> Unit, onSave: (Entry) -> Unit) {
    val unitConfirmed = entry.unitReviewed && entry.unit in Entry.allowedUnits
    var expanded by rememberSaveable(entry.id, mode) { mutableStateOf(false) }
    var quantity by rememberSaveable(entry.id, entry.actualMilli, entry.plannedMilli, entry.bought) {
        mutableStateOf((if (entry.bought) entry.actualMilli else entry.plannedMilli).takeIf { it > 0 }?.let(::sessionQuantity).orEmpty())
    }
    var price by rememberSaveable(entry.id, entry.actual, entry.bought) {
        mutableStateOf(entry.actual?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }.orEmpty())
    }
    var error by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    val enteredPrice = runCatching { Money.cents(price) }.getOrNull()
    val decision = remember(entry, data.records, enteredPrice) { compareShoppingPrice(entry, data.records, enteredPrice) }
    val historicalProduct=remember(entry.sourceBaseId,entry.sourceSkuId,data.records) { data.records.firstOrNull { it.id==entry.sourceBaseId && it.kind=="catalogItem" } ?: data.records.firstOrNull { it.id==entry.sourceSkuId && it.kind=="catalogSku" } }
    val previous=remember(historicalProduct,data.records) { historicalProduct?.let { productPurchases(it,data.records).firstOrNull() } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize(tween(if (LocalExperience.current.motion) 180 else 0)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(checked = entry.bought, enabled = !busy, onCheckedChange = { checked ->
                    if (!checked) onSave(entry.copy(bought = false, actual = null))
                    else if (unitConfirmed) expanded = true
                    else onEdit(entry)
                })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    val quantityLabel = if (unitConfirmed && entry.quantityReviewed) "${sessionQuantity(if (entry.bought) entry.actualMilli else entry.plannedMilli)} ${entry.unit}" else "Quantidade ou unidade a confirmar"
                    if (mode == "Planejar") Text(listOf(entry.priority.takeIf { it=="Alta" }?.let { "Prioridade alta" }, entry.subgroup, entry.storageRoom.ifBlank { suggestedStorageRoom(entry.title,entry.category) }, entry.category).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if(entry.priority=="Alta") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(quantityLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mode == "Planejar" || expanded) previous?.let { Text("Última compra: ${it.quantityText.replace('.',',')} ${it.unit} · R$ ${it.unitPriceText.replace('.',',')}/${it.unit}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val subtotal = if (entry.bought) entry.reviewedReal() else entry.reviewedEstimate()
                    Text(subtotal?.let(::sessionMoney) ?: "A confirmar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (entry.bought) "real" else "estimativa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(entry) }, enabled = !busy) { Text("Ficha") }
                if (mode != "Planejar" || entry.bought) TextButton(onClick = {
                    if (unitConfirmed) { expanded = !expanded; error = null } else onEdit(entry)
                }, enabled = !busy) { Text(if (!unitConfirmed) "Confirmar unidade" else if (expanded) "Fechar" else if (entry.bought) "Ajustar preço" else "Adicionar ao carrinho") }
            }
            if (expanded && unitConfirmed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it; error = null }, label = { Text("Quantidade (${entry.unit})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy, modifier = Modifier.weight(1f))
                    OutlinedTextField(price, { price = it; error = null }, label = { Text("Preço real / ${entry.unit}") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy, modifier = Modifier.weight(1.2f))
                }
                Text(if (decision == null) "Sem preço documentado comparável para este produto." else {
                    fun decimalMoney(value: BigDecimal) = "R$ ${value.abs().setScale(maxOf(2, value.scale())).toPlainString().replace('.', ',')}"
                    val percent = decision.percent?.abs()?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',')?.let { " ($it%)" }.orEmpty()
                    when (decision.trend) {
                        PriceTrend.LOWER -> "${decimalMoney(decision.difference)} mais barato$percent por ${entry.unit}"
                        PriceTrend.HIGHER -> "${decimalMoney(decision.difference)} mais caro$percent por ${entry.unit}"
                        PriceTrend.SAME -> "Mesmo preço unitário documentado"
                    } + " · ${decision.documentDate}"
                }, style = MaterialTheme.typography.bodySmall, color = if (decision?.trend == PriceTrend.HIGHER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val prepared = runCatching { prepareQuickCartEntry(entry, quantity, price) }
                    prepared.onSuccess { onSave(it); expanded = false }.onFailure { error = it.message ?: "Confira a quantidade e o preço real." }
                }, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text(if (entry.bought) "Salvar ajuste" else "Adicionar") }
            }
        }
    }
}

@Composable
private fun SessionPanel(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Color(0xFFD5DDE8))) {
        Column(Modifier.padding(18.dp).animateContentSize(tween(if (LocalExperience.current.motion) 220 else 0)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body()
        }
    }
}
