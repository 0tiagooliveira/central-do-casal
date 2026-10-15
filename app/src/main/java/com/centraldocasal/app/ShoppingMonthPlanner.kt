package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Money
import com.centraldocasal.app.domain.Record
import java.math.BigDecimal

/** A full-screen, two-step planner. No nested scrollable list inside an AlertDialog. */
@Composable
internal fun ShoppingMonthPlannerScreen(
    data: DataState,
    basket: Basket,
    market: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>, Map<String, String>) -> Unit
) {
    val products = remember(data.records, market) {
        data.records.filter { it.kind == "catalogItem" && it.text("market") == market }
            .sortedBy { it.text("title") }
    }
    val ids = remember(products) { products.map { it.id }.toSet() }
    val alreadySelected = remember(basket.entries) { basket.entries.map { it.sourceBaseId }.toSet() }
    // Keep the draft throughout navigation between the two screens, even when filters change.
    val decisions = remember(market, basket.generation, basket.revision, ids) {
        mutableStateMapOf<String, String>().apply {
            products.forEach { product ->
                put(product.id, when {
                    product.id in alreadySelected -> "Comprar"
                    (product.flag("hasAtHome") || (product.flag("stockKnown") && product.number("stockMilli") > 0)) -> "Tenho"
                    else -> "Pular"
                })
            }
        }
    }
    val quantities = remember(market, ids) {
        mutableStateMapOf<String, String>().apply {
            products.forEach { product ->
                if (product.flag("stockKnown")) {
                    put(product.id, BigDecimal.valueOf(product.number("stockMilli"), 3)
                        .stripTrailingZeros().toPlainString().replace('.', ','))
                }
            }
        }
    }
    var isReview by rememberSaveable(market) { mutableStateOf(false) }
    var search by rememberSaveable(market) { mutableStateOf("") }
    var room by rememberSaveable(market) { mutableStateOf("Todos") }
    var filter by rememberSaveable(market) { mutableStateOf("Todos") }
    var openedMenu by remember { mutableStateOf<String?>(null) }
    var inventoryExpanded by remember { mutableStateOf(false) }
    val selected = products.filter { decisions[it.id] == "Comprar" }
    val have = products.filter { decisions[it.id] == "Tenho" }
    val rooms = remember(products) { listOf("Todos") + products.map(::plannerRoom).distinct().sorted() }
    val visible = products.filter { product ->
        product.text("title").contains(search.trim(), ignoreCase = true) &&
            (room == "Todos" || plannerRoom(product) == room) &&
            (filter == "Todos" || (filter == "Na lista" && decisions[product.id] == "Comprar") ||
                (filter == "Em casa" && decisions[product.id] == "Tenho"))
    }
    val invalidQuantity = have.any { product ->
        quantities[product.id]?.takeIf { it.isNotBlank() }?.let {
            runCatching { Money.quantity(it) }.isFailure
        } == true
    }
    val retained = basket.entries.count {
        it.sourceBaseId.isBlank() || it.sourceBaseId !in ids || decisions[it.sourceBaseId] == "Comprar"
    }
    val newEntries = selected.count { it.id !in alreadySelected }
    val finalCount = retained + newEntries

    Scaffold(
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (isReview) isReview = false else onDismiss()
                    }) { Text(if (isReview) "‹ Selecionar" else "‹ Voltar") }
                    Spacer(Modifier.weight(1f))
                    Text(if (isReview) "Etapa 2 de 2" else "Etapa 1 de 2",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Surface(shadowElevation = 10.dp, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (invalidQuantity && isReview) {
                        Text("Corrija as quantidades em casa antes de salvar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    } else if (isReview) {
                        Text("${selected.size} para comprar · ${have.size} em casa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("${selected.size} selecionados · ${have.size} marcados como já tenho",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            if (isReview) onSave(decisions.toMap(), quantities.toMap())
                            else isReview = true
                        },
                        enabled = !busy && (!isReview || (!invalidQuantity && finalCount <= 200)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (isReview) "Atualizar lista (${selected.size})"
                             else "Revisar ${selected.size} itens")
                    }
                }
            }
        }
    ) { padding ->
        if (!isReview) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escolha o que comprar", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("Toque no produto para adicioná-lo. Use ⋮ para marcar o que já tem em casa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Buscar produto") }
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rooms.forEach { option ->
                            FilterChip(selected = room == option, onClick = { room = option },
                                label = { Text(option) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Na lista", "Em casa").forEach { option ->
                            FilterChip(selected = filter == option, onClick = { filter = option },
                                label = { Text(option) })
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (room != "Todos" && search.isBlank() && filter == "Todos" &&
                        visible.any { decisions[it.id] != "Comprar" }) {
                        item {
                            TextButton(onClick = {
                                visible.forEach { decisions[it.id] = "Comprar" }
                            }) { Text("Adicionar todos de ${room}") }
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(if (products.isEmpty()) "Nenhum produto cadastrado para $market."
                                else "Nada encontrado. Tente outra busca ou categoria.",
                                modifier = Modifier.padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(visible, key = { it.id }) { product ->
                        val state = decisions[product.id] ?: "Pular"
                        val checked = state == "Comprar"
                        val inHouse = state == "Tenho"
                        val selectedColor = MaterialTheme.colorScheme.primary
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, if (checked) selectedColor
                                else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 70.dp)
                                    .padding(start = 14.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier.weight(1f).clickable {
                                        decisions[product.id] = if (checked) "Pular" else "Comprar"
                                    }.padding(vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(product.text("title"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(if (inHouse) "Já tenho · ${plannerRoom(product)}"
                                         else plannerRoom(product),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    decisions[product.id] = if (checked) "Pular" else "Comprar"
                                }) {
                                    Icon(if (checked) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (checked)
                                            "Remover ${product.text("title")} da lista"
                                        else "Adicionar ${product.text("title")} à lista",
                                        tint = if (checked) selectedColor
                                            else MaterialTheme.colorScheme.onSurface)
                                }
                                Box {
                                    IconButton(onClick = { openedMenu = product.id }) {
                                        Icon(Icons.Default.MoreVert,
                                            contentDescription = "Outras opções para ${product.text("title")}")
                                    }
                                    DropdownMenu(expanded = openedMenu == product.id,
                                        onDismissRequest = { openedMenu = null }) {
                                        listOf(
                                            "Comprar" to "Adicionar à lista",
                                            "Tenho" to "Já tenho em casa",
                                            "Pular" to "Não comprar neste mês"
                                        ).forEach { (decision, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                decisions[product.id] = decision
                                                openedMenu = null
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Revise sua lista", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("${selected.size} produtos selecionados para $market.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected.isEmpty()) {
                    item {
                        Text("Nenhum produto selecionado. Isso retirará os itens do catálogo da lista atual. Itens adicionados manualmente serão mantidos.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(selected, key = { it.id }) { product ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.text("title"), style = MaterialTheme.typography.bodyLarge)
                                Text(plannerRoom(product),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { decisions[product.id] = "Pular" }) {
                                Text("Remover")
                            }
                        }
                    }
                }
                if (have.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        TextButton(onClick = { inventoryExpanded = !inventoryExpanded }) {
                            Text("Já tenho em casa (${have.size}) ${if (inventoryExpanded) "▴" else "▾"}")
                        }
                        Text("Opcional: registre quanto ainda tem em casa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (inventoryExpanded) {
                        items(have, key = { "stock-${it.id}" }) { product ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(product.text("title"), style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    val unit = product.text("unit").ifBlank { "un" }
                                    OutlinedTextField(
                                        value = quantities[product.id].orEmpty(),
                                        onValueChange = { quantities[product.id] = it },
                                        label = { Text("Quantidade em casa ($unit)") },
                                        placeholder = { Text("Se souber") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        isError = quantities[product.id]?.takeIf { it.isNotBlank() }
                                            ?.let { runCatching { Money.quantity(it) }.isFailure } == true
                                    )
                                    TextButton(onClick = { decisions[product.id] = "Comprar" }) {
                                        Text("Preciso comprar")
                                    }
                                }
                            }
                        }
                    }
                }
                if (finalCount > 200) {
                    item {
                        Text("O limite é de 200 itens por lista. Remova alguns produtos para continuar.",
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Text("Produtos adicionados manualmente e itens sem correspondência no catálogo serão preservados. Você poderá editar quantidades e preços depois.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun plannerRoom(product: Record): String =
    product.text("storageRoom").ifBlank {
        suggestedStorageRoom(product.text("title"), product.text("category"))
    }
