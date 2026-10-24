package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.*
import com.centraldocasal.app.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private fun hubMoney(value: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(value, 2))

internal fun suggestedStorageRoom(title: String, category: String): String {
    val words=(title+" "+category).lowercase()
    return when {
        listOf("sabão","amaciante","alvejante","limpeza","desinfetante").any(words::contains) -> "Lavanderia"
        listOf("shampoo","sabonete","pasta","escova","absorvente","papel higiênico").any(words::contains) -> "Banheiro"
        listOf("ração","areia","pet").any(words::contains) -> "Pets"
        listOf("arroz","leite","café","biscoito","alimento","bebida","tempero","despensa").any(words::contains) -> "Cozinha"
        else -> "Casa"
    }
}

internal fun suggestedRecipient(title: String, category: String): String {
    val words=(title+" "+category).lowercase()
    return when {
        listOf("fralda", "bebê", "infantil", "lucca", "fórmula").any(words::contains) -> "Lucca"
        listOf("rhayra", "maquiagem", "absorvente").any(words::contains) -> "Rhayra"
        listOf("tiago", "barba").any(words::contains) -> "Tiago"
        else -> "Casa"
    }
}

internal fun suggestedNeedGroup(title: String, category: String, recipient: String): String {
    val words=(title+" "+category).lowercase()
    return when {
        recipient=="Lucca" && listOf("fralda", "fórmula", "leite", "papinha").any(words::contains) -> "Cuidados do Lucca"
        listOf("comida", "arroz", "leite", "café", "biscoito", "tempero", "bebida").any(words::contains) -> "Alimentação"
        listOf("sabão", "limpeza", "desinfetante", "papel", "amaciante").any(words::contains) -> "Casa e limpeza"
        listOf("shampoo", "sabonete", "pasta", "absorvente", "higiene").any(words::contains) -> "Higiene e cuidados"
        listOf("ração", "areia", "pet").any(words::contains) -> "Pets"
        else -> "Essenciais"
    }
}

private fun linkingWords(value: String) = value.lowercase().split(Regex("[^a-záéíóúâêôãõç0-9]+"))
    .filter { it.length > 2 && it !in setOf("com","para","sem","tipo") }.toSet()

/** Groups help navigation but must never receive the price history of a specific product. */
internal fun isGenericCatalogBase(title: String): Boolean = title.trim().lowercase(Locale.forLanguageTag("pt-BR")) in setOf(
    "temperos", "bebidas", "sucos", "suco", "limpeza", "higiene", "alimentos", "congelados", "laticínios", "carnes", "frutas", "verduras"
)

/** Local, explainable cleanup for receipt labels. The original fiscal description is never replaced. */
private fun fiscalLookupKey(raw: String) = raw.uppercase(Locale.forLanguageTag("pt-BR")).replace(Regex("\\s+"), " ").trim()

/** Curated corrections are applied only to exact receipt patterns previously checked in public catalogues. */
internal fun suggestedFiscalName(raw: String): String {
    val ignored = setOf("UN", "UND", "UNID", "CX", "PCT", "PC", "COD", "ITEM", "NCM")
    val unitTokens = setOf("KG", "G", "L", "ML")
    val corrections = mapOf("SABAO" to "Sabão", "PO" to "Pó", "ACUCAR" to "Açúcar", "CAFE" to "Café", "LEITE" to "Leite", "INTEGRAL" to "Integral", "DESNAT" to "Desnatado", "BISC" to "Biscoito", "RACAO" to "Ração")
    val compact = raw.trim().replace(Regex("\\s+"), " ").replace(Regex("^\\d{4,}\\s*[-.]?\\s*"), "")
    if (compact.isBlank()) return "Produto a nomear"
    val lookup = fiscalLookupKey(compact)
    when {
        lookup.contains("QJO RAL PARMITAL") && Regex("40\\s*G").containsMatchIn(lookup) -> return "Queijo ralado Parmital 40 g"
        lookup.contains("COPO") && lookup.contains("TAMPA") && lookup.contains("BOLHA") && Regex("150\\s*ML").containsMatchIn(lookup) && Regex("\\bC\\s*50\\b").containsMatchIn(lookup) -> return "Copo 150 mL com tampa bolha — pacote com 50 unidades"
        lookup.contains("BATATA RAPMIX") && Regex("2\\s*KG").containsMatchIn(lookup) -> return "Batata palito pré-frita congelada Rapmix 2 kg"
        lookup.contains("TEMP KITANO MIX") && Regex("40\\s*G").containsMatchIn(lookup) -> return "Tempero natural Mix Kitano 40 g — variante a confirmar"
    }
    return compact.split(' ').filterNot { it.uppercase() in ignored }.joinToString(" ") { token ->
        val clean = token.trim('.', ',', ';', ':')
        val upper = clean.uppercase(Locale.forLanguageTag("pt-BR"))
        when {
            upper in corrections -> corrections.getValue(upper)
            upper in unitTokens || upper.matches(Regex("\\d+(?:[.,]\\d+)?(?:KG|G|ML|L)")) -> upper
            upper.matches(Regex("\\d+")) -> upper
            else -> clean.lowercase(Locale.forLanguageTag("pt-BR")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
        }
    }.ifBlank { "Produto a nomear" }
}

internal fun fiscalNameSupport(raw: String): String {
    val lookup = fiscalLookupKey(raw)
    val curated = (lookup.contains("QJO RAL PARMITAL") && Regex("40\\s*G").containsMatchIn(lookup)) ||
        (lookup.contains("COPO") && lookup.contains("TAMPA") && lookup.contains("BOLHA") && Regex("150\\s*ML").containsMatchIn(lookup) && Regex("\\bC\\s*50\\b").containsMatchIn(lookup)) ||
        (lookup.contains("BATATA RAPMIX") && Regex("2\\s*KG").containsMatchIn(lookup)) ||
        (lookup.contains("TEMP KITANO MIX") && Regex("40\\s*G").containsMatchIn(lookup))
    return if (curated) "Sugestão revisada em catálogos públicos; confirme antes de salvar." else "Sugestão local por abreviações; confirme ou ajuste antes de salvar."
}

/** Suggestions only organize the review queue; a user action is still required to save a link. */
private fun assistedLinkSuggestions(data: DataState, market: String): List<Record> {
    val imported=data.records.filter { it.kind=="matchSuggestion" }
    val suggestedSkuIds=imported.map { it.text("skuId") }.toSet()
    val bases=data.records.filter { it.kind=="catalogItem" && it.text("market")==market && !isGenericCatalogBase(it.text("title")) }
    val generated=data.records.filter { it.kind=="catalogSku" && it.text("market")==market && it.id !in suggestedSkuIds }
        .filter { sku -> data.records.none { it.kind=="catalogLink" && it.id==sku.id } }
        .map { sku ->
            val words=linkingWords(sku.text("title"))
            val candidates=bases.map { base ->
                val nameScore=words.intersect(linkingWords(base.text("title"))).size
                val sameUnit=sku.text("unit").isNotBlank() && sku.text("unit").equals(base.text("unit"),true)
                Triple(base,nameScore+(if(sameUnit) 2 else 0),sameUnit)
            }.filter { it.second>0 }.sortedByDescending { it.second }.take(3)
            Record("assist-$market-${sku.id}","matchSuggestion",fields=mapOf(
                "title" to sku.text("title"), "skuId" to sku.id, "confidence" to "revisar",
                "note" to if(candidates.isEmpty()) "Nenhum nome parecido foi encontrado. Escolha manualmente um produto da lista ou mantenha separado." else "Sugestão assistida por palavras do nome. Compare marca, unidade e valor antes de confirmar.",
                "candidates" to candidates.map { (base,score,sameUnit) -> mapOf("baseItemId" to base.id,"title" to base.text("title"),"reason" to "$score ponto(s) de compatibilidade${if(sameUnit) "; unidade compatível" else ""}; confirmação humana necessária.") }
            ))
        }
    return (imported+generated).filter { suggestion ->
        data.records.firstOrNull { sku -> sku.kind=="catalogSku" && sku.id==suggestion.text("skuId") }?.text("market")==market
    }
}

/**
 * Matches only receipt products whose planned name is contained in the fiscal name and whose
 * unit does not conflict. This is deliberately conservative: close alternatives remain in the
 * review queue instead of corrupting price history.
 */
internal data class ReliableCatalogLink(val sku: Record, val base: Record)

internal fun reliableCatalogLinks(data: DataState, market: String): List<ReliableCatalogLink> {
    val linkedSkuIds = data.records.filter { it.kind == "catalogLink" }.map { it.id }.toSet()
    val bases = data.records.filter { it.kind == "catalogItem" && it.text("market") == market && !isGenericCatalogBase(it.text("title")) }
    return data.records.asSequence()
        .filter { it.kind == "catalogSku" && it.text("market") == market && it.id !in linkedSkuIds }
        .mapNotNull { sku ->
            val skuWords = linkingWords(sku.text("title"))
            val compatible = bases.mapNotNull { base ->
                val baseWords = linkingWords(base.text("title"))
                val skuUnit = sku.text("unit")
                val baseUnit = base.text("unit")
                val unitsConflict = skuUnit.isNotBlank() && baseUnit.isNotBlank() && !skuUnit.equals(baseUnit, true)
                if (baseWords.isEmpty() || unitsConflict || !skuWords.containsAll(baseWords)) null
                else base to (baseWords.size * 10 + if (skuUnit.isNotBlank() && skuUnit.equals(baseUnit, true)) 3 else 0)
            }.sortedByDescending { it.second }
            val best = compatible.firstOrNull() ?: return@mapNotNull null
            val tied = compatible.drop(1).any { it.second == best.second }
            if (tied) null else ReliableCatalogLink(sku, best.first)
        }.toList()
}

internal fun catalogDraft(record: Record, records: List<Record> = emptyList()): Entry {
    val historic=productPurchases(record,records).firstOrNull()
    val sourceUnit=historic?.unit?.ifBlank { record.text("unit") } ?: record.text("unit")
    val unit = when(sourceUnit.uppercase()) { "UN" -> "un"; "KG" -> "kg"; "G" -> "g"; "L" -> "L"; "ML" -> "mL"; "PACOTE" -> "pacote"; else -> "Escolher" }
    val priceText=historic?.unitPriceText?.takeIf { it.isNotBlank() } ?: record.text("referenceText")
    val quantityText=historic?.quantityText?.takeIf { it.isNotBlank() } ?: record.text("quantityText")
    val price = if(unit in Entry.allowedUnits) runCatching { Money.cents(priceText) }.getOrNull() else null
    val quantity = runCatching { Money.quantity(quantityText) }.getOrNull()
    val recipient=record.text("recipient").ifBlank { suggestedRecipient(record.text("title"),record.text("category")) }
    val group=record.text("group").ifBlank { suggestedNeedGroup(record.text("title"),record.text("category"),recipient) }
    return Entry(title=record.text("title"), unit=unit, plannedMilli=quantity ?: 1000, actualMilli=quantity ?: 1000, estimated=price ?: 0,
        estimateKnown=price != null, unitReviewed=unit in Entry.allowedUnits, quantityReviewed=quantity != null,
        sourceBaseId=if(record.kind=="catalogItem") record.id else "", sourceSkuId=if(record.kind=="catalogSku") record.id else "", category=record.text("category"),storageRoom=suggestedStorageRoom(record.text("title"),record.text("category")),recipient=recipient,group=group,subgroup=record.text("subgroup"),priority=record.text("priority").ifBlank { "Normal" })
}

/** A compact signal for list preparation; comparisons stay within the same unit. */
internal fun planningPriceSignal(record: Record, records: List<Record>): String? {
    val purchases = productPurchases(record, records)
    val latest = purchases.firstOrNull { it.unitPriceText.toBigDecimalOrNull() != null } ?: return null
    val latestPrice = latest.unitPriceText.toBigDecimalOrNull() ?: return null
    val previous = purchases.dropWhile { it.id == latest.id }.firstOrNull { it.unit.equals(latest.unit, true) && it.unitPriceText.toBigDecimalOrNull() != null } ?: return "Última referência: R$ ${latestPrice.stripTrailingZeros().toPlainString().replace('.', ',')}/${latest.unit}"
    val old = previous.unitPriceText.toBigDecimalOrNull() ?: return null
    val movement = when { latestPrice > old -> "mais caro"; latestPrice < old -> "mais barato"; else -> "no mesmo preço" }
    return "Última referência: R$ ${latestPrice.stripTrailingZeros().toPlainString().replace('.', ',')}/${latest.unit} · $movement que a compra anterior"
}

@Composable
internal fun ShoppingHubScreen(vm: CentralViewModel, data: DataState, session: SessionState, busy: Boolean, annual: () -> Unit, requestedMarket: String? = null) {
    var market by rememberSaveable { mutableStateOf(requestedMarket?.takeIf { it in listOf("Mercado","Açougue") } ?: "Mercado") }
    LaunchedEffect(requestedMarket) { if(requestedMarket in listOf("Mercado","Açougue")) market = requestedMarket!! }
    var section by rememberSaveable { mutableStateOf("Lista") }
    var search by rememberSaveable { mutableStateOf("") }
    var byStore by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Record?>(null) }
    var receiptDetail by remember { mutableStateOf<Record?>(null) }
    var completedReviews by rememberSaveable { mutableStateOf(false) }
    var confirmReliableLinks by rememberSaveable { mutableStateOf(false) }
    val basket = data.baskets[market] ?: remember(market) { Basket(market) }
    val view = LocalView.current
    val experience = LocalExperience.current
    fun feedback() { AndroidInteractions.haptic(view, experience.haptics); AndroidInteractions.clickSound(view, experience.sounds) }
    if(receiptDetail!=null) { PurchaseDocumentScreen(receiptDetail!!) { receiptDetail=null };return }
    if(detail!=null) {
        val product=detail!!
        ProductDetailScreen(vm,busy,product,data,onDismiss={detail=null},onAdd={ selected -> editing=catalogDraft(selected,data.records).let { if(product.kind=="catalogItem") it.copy(sourceBaseId=product.id) else it };detail=null },onReceipt={receiptDetail=it})
        return
    }
    if(section=="Lista") ShoppingSessionScreen(data,session,busy,{editing=it},{adding=true},{budget=true},{section="Catálogo"},annual,{section="Histórico"},{preparing=true},{closing=true},{market=it},market,::feedback,onQuickSave={ entry -> vm.action { vm.repo.editBasket(market,basket.revision) { current -> require(current.entries.any { it.id==entry.id }) { "Este item mudou. Atualize a lista." }; current.copy(entries=current.entries.map { if(it.id==entry.id) entry else it }) }; feedback() } },onReview={ section="Revisar" },onRepurchase={section="Já comprei"})
    else LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            val title=when(section) { "Já comprei" -> "Comprar novamente"; "Catálogo" -> "Produtos para a sua lista"; "Histórico" -> "Histórico que ajuda a decidir"; else -> "Revisar vínculos" }
            val subtitle=when(section) { "Já comprei" -> "Produtos realmente comprados antes, prontos para reutilizar sem perder o histórico."; "Catálogo" -> "Padronize nome, unidade e onde guardar antes de planejar."; "Histórico" -> "Compare preços reais, use a última compra e entenda cada diferença."; else -> "Confira sugestões antes de unir um item da nota a um produto planejado." }
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                SectionHero(title, subtitle, BasketLine)
                ChoiceStrip(if(section=="Já comprei") listOf("Lista","Já comprei","Histórico") else listOf("Lista","Catálogo","Histórico","Revisar"),section,onSelect = { label -> section=label;search="";feedback() })
                ChoiceStrip(listOf("Mercado","Açougue"),market,onSelect = { label -> market=label;search="";feedback() })
            }
        }
        if(section=="Já comprei") {
            val previous=repurchaseSuggestions(data.records,market)
            val filtered=previous.filter { item -> item.title.contains(search,true) || item.store.contains(search,true) }
            item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(search,{search=it},label={Text("Buscar produto que já comprei")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Text("${previous.size} produto(s) com compra registrada · Mercado e Açougue separados",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("O último preço é apenas referência. Confira valor, unidade e quantidade antes de adicionar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(filtered.isEmpty()) item {
                QuietCard(if(search.isBlank()) "Nenhum produto comprado encontrado" else "Nenhum resultado para esta busca",
                    if(search.isBlank()) "Este espaço ainda não contém recibos finalizados ou histórico fiscal vinculado a este mercado. Confira a conta ativa, a sincronização e as notas importadas." else "Tente outro nome ou confira Histórico e Catálogo.") {
                    OutlinedButton(onClick={section="Histórico"}) { Text("Ver histórico disponível") }
                }
            }
            items(filtered,key={it.key}) { suggestion ->
                val linked=alreadyOnShoppingList(suggestion,basket.entries)
                OutlinedCard(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(suggestion.title,style=MaterialTheme.typography.titleMedium)
                        Text("${suggestion.lastDate.ifBlank { "Data não informada" }} · ${suggestion.store} · ${suggestion.unit.ifBlank { "Unidade a confirmar" }}",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(if(suggestion.draft.estimateKnown) "Último preço unitário: ${hubMoney(suggestion.draft.estimated)}" else "Preço a confirmar",
                                style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
                            TextButton(enabled=!busy && !linked,onClick={editing=suggestion.draft;section="Lista"}) {
                                Text(if(linked) "Já está na lista" else "Reutilizar")
                            }
                        }
                    }
                }
            }
        } else if(section=="Catálogo") {
            item {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(!byStore,{byStore=false},label={Text("Minha base")}); FilterChip(byStore,{byStore=true},label={Text("Por loja")}) }
                OutlinedTextField(search,{search=it},label={Text("Buscar no catálogo")},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={section="Revisar"}) { Text("Revisar sugestões assistidas") }
            }
            val catalog = data.records.filter { it.kind==if(byStore) "catalogSku" else "catalogItem" }.filter { it.text("market")==market && (it.text("title").contains(search,true)||it.text("category").contains(search,true)) }.sortedBy { it.text("title") }
            val purchaseCounts=catalog.associate { it.id to productPurchases(it,data.records).size }
            item { Text("${catalog.size} produtos · referências e preços documentados",style=MaterialTheme.typography.bodySmall) }
            items(catalog,key={it.id}) { record -> ImportedCatalogCard(record,purchases=purchaseCounts[record.id] ?: 0) { detail=record } }
        } else if(section=="Histórico") {
            val products=data.records.filter { it.kind=="catalogItem" && it.text("market")==market }
                .map { product -> product to productPurchases(product,data.records) }
                .filter { it.second.isNotEmpty() }
                .sortedWith(compareByDescending<Pair<Record,List<ProductPurchase>>> { it.second.maxOfOrNull { purchase -> purchase.date }.orEmpty() }.thenBy { it.first.text("title") })
            val waitingReview=assistedLinkSuggestions(data,market).count { suggestion -> data.records.none { link -> link.kind=="catalogLink" && link.id==suggestion.text("skuId") } }
            item {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Produtos organizados por vocês, preços reais e referências das notas. Valores ausentes continuam a confirmar.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick=annual) { Text("Ver ano de compras") }
                        if(waitingReview>0) FilledTonalButton(onClick={section="Revisar"}) { Text("Organizar $waitingReview sugestões") }
                    }
                }
            }
            if(products.isNotEmpty()) item { QuietCard("Produtos para planejar", "Toque em um item para ver as compras, unidade e evolução de preço.") { } }
            items(products,key={it.first.id}) { (product,purchases) ->
                UsefulProductHistoryCard(product,purchases) { detail=product }
            }
            if(products.isEmpty()) item { QuietCard("Ainda estamos organizando as notas", "Quando um produto estiver identificado, ele aparecerá aqui com unidade, preço e histórico útil para a próxima lista.") { if(waitingReview>0) TextButton(onClick={section="Revisar"}) { Text("Revisar identificações") } } }
            item { Text("Notas e compras concluídas",style=MaterialTheme.typography.titleMedium) }
            val receipts=data.records.filter { it.kind in listOf("receipt","importedReceipt") && it.text("market")==market }.sortedByDescending { it.text("date") }
            if(receipts.isEmpty()) item { Text("As compras concluídas aparecerão aqui.") }
            items(receipts,key={it.id}) { receipt -> if(receipt.kind=="importedReceipt") ImportedReceiptCard(receipt,onLine={ line ->
                val sku=data.records.firstOrNull { candidate -> candidate.kind=="catalogSku" && candidate.rows("history").any { history -> history.str("id")==line.str("id") } }
                if(sku!=null) detail=sku else receiptDetail=receipt
            }) else AppReceiptHistoryCard(receipt) }
        } else {
            val reliable = reliableCatalogLinks(data, market)
            item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Os vínculos confiáveis podem ser organizados de uma vez. Casos com mais de uma interpretação continuam para revisão, para não misturar seu histórico de preços.",style=MaterialTheme.typography.bodyMedium)
                    if (reliable.isNotEmpty()) {
                        FilledTonalButton(onClick={confirmReliableLinks=true},enabled=!busy) { Text("Consolidar ${reliable.size} vínculos confiáveis") }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(!completedReviews,{completedReviews=false},label={Text("Para revisar")});FilterChip(completedReviews,{completedReviews=true},label={Text("Organizados")}) }
                }
            }
            val suggestions=assistedLinkSuggestions(data,market).filter { suggestion -> data.records.any { it.kind=="catalogLink"&&it.id==suggestion.text("skuId") }==completedReviews }
            if(suggestions.isEmpty()) item { Text("As sugestões aparecem após a sincronização.") }
            items(suggestions,key={it.id}) { suggestion -> MatchReviewCard(vm,data,suggestion,busy,onLinked={detail=it},onOpen={detail=it}) }
        }
    }
    if(adding||editing!=null) ShoppingEditor(vm,basket,editing) { adding=false;editing=null }
    if(budget) {
        var amount by remember { mutableStateOf(BigDecimal.valueOf(basket.budget,2).toPlainString().replace('.',',')) }
        EditDialog("Orçamento de $market",{budget=false},{vm.action { val value=Money.cents(amount); vm.repo.editBasket(market,basket.revision.takeIf { data.baskets.containsKey(market) }) { it.copy(budget=value) };budget=false }},busy) { OutlinedTextField(amount,{amount=it},label={Text("Valor em R$")}) }
    }
    if(preparing) {
        var query by remember { mutableStateOf("") }
        var room by rememberSaveable { mutableStateOf("Todos") }
        val choices=data.records.filter { it.kind=="catalogItem" && it.text("market")==market }.sortedBy { it.text("title") }
        val selectedBaseIds=basket.entries.map { it.sourceBaseId }.filter { it.isNotBlank() }.toSet()
        val choice=remember(choices.map { it.id }, selectedBaseIds) { mutableStateMapOf<String,String>().apply { choices.forEach { record -> put(record.id,if(record.id in selectedBaseIds) "Comprar" else if(record.flag("stockKnown") && record.number("stockMilli")>0) "Tenho" else "Pular") } } }
        val stock=remember(choices.map { it.id to it.number("stockMilli") }) { mutableStateMapOf<String,String>().apply { choices.forEach { record -> if(record.flag("stockKnown")) put(record.id,BigDecimal.valueOf(record.number("stockMilli"),3).stripTrailingZeros().toPlainString().replace('.',',')) } } }
        val invalidStock=choices.any { record -> choice[record.id]=="Tenho" && stock[record.id]?.takeIf { it.isNotBlank() }?.let { runCatching { Money.quantity(it) }.isFailure } == true }
        val buyCount=choice.values.count { it=="Comprar" }; val haveCount=choice.values.count { it=="Tenho" }
        AlertDialog(onDismissRequest={preparing=false},title={Text("Pré-lista do mês")},text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Todos os produtos da base aparecem aqui por cômodo. Escolha o que entra, o que já existe em casa e o que fica fora desta compra.")
            OutlinedTextField(query,{query=it},label={Text("Buscar produto")},singleLine=true)
            val rooms=listOf("Todos")+(choices.map { it.text("storageRoom").ifBlank { suggestedStorageRoom(it.text("title"),it.text("category")) } }.distinct().sorted())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { rooms.forEach { option -> FilterChip(room==option,{room=option},label={Text(option)}) } }
            Text("$buyCount na lista · $haveCount já tenho · ${choices.size-buyCount-haveCount} fora este mês",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val visible=choices.filter { it.text("title").contains(query,true) && (room=="Todos" || it.text("storageRoom").ifBlank { suggestedStorageRoom(it.text("title"),it.text("category")) }==room) }
            LazyColumn(Modifier.heightIn(max=360.dp)) { items(visible,key={it.id}) { record ->
                val previous=productPurchases(record,data.records).firstOrNull(); val unit=record.text("unit").ifBlank { previous?.unit ?: "un" }; val state=choice[record.id] ?: "Pular"
                Column(Modifier.padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(record.text("title"),style=MaterialTheme.typography.titleSmall)
                    Text("${record.text("storageRoom").ifBlank { suggestedStorageRoom(record.text("title"),record.text("category")) }} · ${record.text("category")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    planningPriceSignal(record,data.records)?.let { signal -> Text(signal,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Comprar","Tenho","Pular").forEach { option -> FilterChip(state==option,{choice[record.id]=option},label={Text(if(option=="Pular") "Não este mês" else option)}) } }
                    if(state=="Tenho") OutlinedTextField(stock[record.id].orEmpty(),{ value -> stock[record.id]=value },label={Text("Quantidade em casa ($unit)")},placeholder={Text("Informe se souber")},singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            } }
            if(invalidStock) Text("Use até três casas decimais na quantidade em casa.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        }},confirmButton={Button(enabled=!busy&&!invalidStock,onClick={vm.action { choices.filter { choice[it.id]=="Tenho" }.forEach { record -> val value=stock[record.id]?.trim().orEmpty(); vm.repo.saveCatalogInventory(record,if(value.isBlank()) null else Money.quantity(value),record.text("storageRoom").ifBlank { suggestedStorageRoom(record.text("title"),record.text("category")) }) }; val existing=basket.entries.map { it.sourceBaseId }.toSet(); val kept=basket.entries.filter { entry -> entry.sourceBaseId.isBlank() || choice[entry.sourceBaseId]=="Comprar" }; val drafts=choices.filter { choice[it.id]=="Comprar" && it.id !in existing }.map { catalogDraft(it,data.records) }; vm.repo.editBasket(market,basket.revision.takeIf { data.baskets.containsKey(market) }) { require(kept.size+drafts.size<=200) { "Use até 200 itens por lista." }; it.copy(entries=kept+drafts) }; preparing=false }}) { Text(if(buyCount==0) "Atualizar sem itens" else "Atualizar lista com $buyCount itens") }},dismissButton={TextButton(onClick={preparing=false}) { Text("Voltar") }})
    }
    if(closing) AlertDialog(onDismissRequest={closing=false},title={Text("Concluir esta compra?")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("${basket.entries.count { it.bought }} itens · ${hubMoney(basket.real())}"); Text("Os itens pendentes continuam na lista. Preços e quantidades ficam preservados no histórico.") }},confirmButton={Button(enabled=!busy,onClick={vm.action { vm.repo.closeBasket(market,basket.generation,basket.revision);closing=false;section="Histórico";feedback() }}) { Text("Confirmar fechamento") }},dismissButton={TextButton(onClick={closing=false}) { Text("Revisar") }})
    if(confirmReliableLinks) {
        val reliable = reliableCatalogLinks(data, market)
        AlertDialog(
            onDismissRequest={confirmReliableLinks=false},
            title={Text("Consolidar vínculos? ")},
            text={Text("${reliable.size} itens de nota serão associados aos produtos planejados com nome compatível e sem conflito de unidade. Esta ação preserva a descrição original da nota e passa a usar o histórico na sua lista.")},
            confirmButton={Button(enabled=!busy,onClick={
                vm.action("${reliable.size} vínculos organizados", timeoutMs=120000) {
                    reliable.forEach { link -> vm.repo.saveCatalogLink(link.sku.id, link.base.id, "confirmed") }
                    confirmReliableLinks=false
                }
            }) { Text("Consolidar agora") }},
            dismissButton={TextButton(onClick={confirmReliableLinks=false}) { Text("Voltar") }}
        )
    }
}

@Composable
private fun UsefulProductHistoryCard(product: Record, purchases: List<ProductPurchase>, open: () -> Unit) {
    val latest=purchases.maxByOrNull { it.date } ?: return
    val known=purchases.mapNotNull { purchase -> purchase.unitPriceText.toBigDecimalOrNull() }
    val lowest=known.minOrNull()
    val unit=latest.unit.ifBlank { product.text("unit").ifBlank { "unidade a confirmar" } }
    val lastPrice=latest.unitPriceText.toBigDecimalOrNull()
    val comparable=purchases.filter { it.unit.equals(unit,true) }.mapNotNull { purchase -> purchase.unitPriceText.toBigDecimalOrNull()?.let { purchase.date to it } }.sortedBy { it.first }
    val first=comparable.firstOrNull()?.second
    val current=comparable.lastOrNull()?.second
    val movement=when {
        first==null||current==null||comparable.size<2 -> "Ainda não há duas compras comparáveis"
        current>first -> "Ficou mais caro desde a primeira compra comparável"
        current<first -> "Ficou mais barato desde a primeira compra comparável"
        else -> "Preço estável nas compras comparáveis"
    }
    val movementColor=when {
        first==null||current==null||current==first -> MaterialTheme.colorScheme.primary
        current>first -> MaterialTheme.colorScheme.error
        else -> Color(0xFF147549)
    }
    OutlinedCard(onClick=open,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(product.text("title"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("Nome planejado · $unit",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${purchases.size} compras",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("Último preço",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(lastPrice?.let { "R$ ${it.stripTrailingZeros().toPlainString().replace('.',',')}" } ?: "A confirmar",style=MaterialTheme.typography.titleMedium) }
                Column { Text("Melhor referência",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(lowest?.let { "R$ ${it.stripTrailingZeros().toPlainString().replace('.',',')}" } ?: "A confirmar",style=MaterialTheme.typography.titleMedium) }
            }
            Text("${latest.quantityText.replace('.',',')} $unit · ${latest.store} · ${latest.date}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(comparable.size>1) PurchaseTrendChart(comparable,unit,movement,movementColor)
            else Text(movement,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ver evolução, notas e preparar para a lista",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PurchaseTrendChart(points: List<Pair<String,BigDecimal>>, unit: String, label: String, color: Color) {
    val values=points.map { it.second }
    val min=values.minOrNull() ?: return
    val max=values.maxOrNull() ?: return
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=color,fontWeight=FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().height(38.dp).semantics { contentDescription="$label. ${points.size} preços por $unit." }) {
            val range=(max-min).toFloat().takeIf { it>0f } ?: 1f
            val plotted=values.mapIndexed { index,value -> Offset(5.dp.toPx()+(size.width-10.dp.toPx())*index/(values.size-1),size.height-5.dp.toPx()-((value-min).toFloat()/range*(size.height-10.dp.toPx()))) }
            plotted.zipWithNext().forEach { (from,to) -> drawLine(color,from,to,2.dp.toPx()) }
            plotted.forEach { drawCircle(color,3.dp.toPx(),it) }
        }
    }
}

@Composable
private fun AppReceiptHistoryCard(receipt: Record) {
    val entries=receipt.rows("entries").map(Entry::from).filter { it.bought }
    val planned=entries.sumOf { it.expected() }
    val cashier=entries.sumOf { it.real() }
    val difference=cashier-planned
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Compra de ${receipt.text("date")}",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column { Text("Previsão",style=MaterialTheme.typography.labelSmall);Text(hubMoney(planned),style=MaterialTheme.typography.titleMedium) };Column(horizontalAlignment=Alignment.End) { Text("Caixa",style=MaterialTheme.typography.labelSmall);Text(hubMoney(cashier),style=MaterialTheme.typography.titleMedium) } }
            Text(if(difference==0L) "Caixa igual ao planejado" else "${hubMoney(kotlin.math.abs(difference))} ${if(difference>0) "acima" else "abaixo"} da previsão",style=MaterialTheme.typography.labelMedium,color=if(difference>0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("${entries.size} ${if(entries.size==1) "produto confirmado" else "produtos confirmados"} · toque nos produtos do Histórico para ver a evolução.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
