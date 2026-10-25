package com.centraldocasal.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private fun compactMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))

/**
 * Lista primeiro: a área de itens cresce com a tela, enquanto operações de
 * catálogo, orçamento e fechamento ficam no menu contextual.
 *
 * A confirmação de compra abre a ficha para registrar o preço real. Não criamos
 * preços fictícios para simular um checkbox concluído.
 */
@Composable
internal fun SimpleShoppingScreen(
    data: DataState,
    market: String,
    busy: Boolean,
    onMarket: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    onEdit: (Entry) -> Unit,
    onUnmark: (Entry) -> Unit,
    onBudget: () -> Unit,
    onPrepareMonth: () -> Unit,
    onHistory: () -> Unit,
    onCatalog: () -> Unit,
    onReview: () -> Unit,
    onAnnual: () -> Unit,
    onFinish: () -> Unit,
    onOverview: () -> Unit
) {
    val basket = data.baskets[market] ?: Basket(market)
    val pending = basket.entries.filterNot { it.bought }.sortedBy { it.title.lowercase() }
    val purchased = basket.entries.filter { it.bought }
    val spent = purchased.filter { it.actual != null && it.actualMilli > 0 }
        .sumOf { Money.subtotal(requireNotNull(it.actual), it.actualMilli) }
    val missingPrices = purchased.count { it.actual == null }
    var draft by rememberSaveable(market) { mutableStateOf("") }
    var showPurchased by rememberSaveable(market) { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var tripStartedAt by rememberSaveable(market) { mutableStateOf<Long?>(null) }

    fun addItem() {
        val name = draft.trim()
        if (name.isNotEmpty() && !busy) {
            onQuickAdd(name)
            draft = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick=onOverview,modifier=Modifier.size(46.dp)) {
                Icon(Icons.Default.ArrowBack,"Voltar às listas")
            }
            Column(Modifier.weight(1f)) {
                Text(if(tripStartedAt!=null) "No mercado" else "Lista de $market",
                    style = MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
                Text(if(tripStartedAt!=null) "Compra em andamento · ${purchased.size} de ${basket.entries.size} itens" else "Adicione o que falta. O restante fica com a gente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções de compras")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Montar com compras anteriores") }, onClick = { menu = false; onPrepareMonth() })
                    DropdownMenuItem(text = { Text("Orçamento") }, onClick = { menu = false; onBudget() })
                    DropdownMenuItem(text = { Text("Catálogo de produtos") }, onClick = { menu = false; onCatalog() })
                    DropdownMenuItem(text = { Text("Histórico de compras") }, onClick = { menu = false; onHistory() })
                    DropdownMenuItem(text = { Text("Revisar vínculos") }, onClick = { menu = false; onReview() })
                    DropdownMenuItem(text = { Text("Resumo do ano") }, onClick = { menu = false; onAnnual() })
                }
            }
        }
        if (tripStartedAt==null) {
            OutlinedButton(onClick={tripStartedAt=System.currentTimeMillis()},
                modifier=Modifier.fillMaxWidth()) { Text("Iniciar compra no $market") }
        } else {
            ReferenceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compra em andamento",style=MaterialTheme.typography.titleSmall,
                            color=MaterialTheme.colorScheme.primary)
                        Text("${purchased.size} de ${basket.entries.size} itens · ${compactMoney(spent)} confirmados",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick={tripStartedAt=null}) { Text("Encerrar modo") }
                }
                ReferenceProgress(if(basket.entries.isEmpty()) 0f else purchased.size.toFloat()/basket.entries.size)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mercado", "Açougue").forEach { option ->
                FilterChip(
                    selected = market == option,
                    onClick = { onMarket(option) },
                    label = { Text(option) }
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("No carrinho", style = MaterialTheme.typography.labelSmall)
                    Text(compactMoney(spent), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Orçamento", style = MaterialTheme.typography.labelSmall)
                    Text(if (basket.budget > 0) compactMoney(basket.budget) else "Definir",
                        style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onBudget, enabled = !busy) { Text("Editar") }
            }
        }
        if (missingPrices > 0) {
            Text("$missingPrices itens aguardam confirmação de preço.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("shopping-quick-add"),
                label = { Text("O que precisamos comprar?") },
                placeholder = { Text("Ex.: arroz, leite, fralda") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addItem() })
            )
            FilledIconButton(
                onClick = ::addItem,
                enabled = draft.isNotBlank() && !busy,
                modifier = Modifier.size(56.dp).testTag("shopping-add-button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar item")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Falta comprar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${pending.size} ${if (pending.size == 1) "item" else "itens"}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pending.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Text("Sua lista começa por aqui", style = MaterialTheme.typography.titleMedium)
                            Text("Digite um produto acima e toque em +. Sem preencher uma ficha inteira.",
                                style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onPrepareMonth) { Text("Trazer itens de compras anteriores") }
                        }
                    }
                }
            }
            items(pending, key = { it.id }) { entry ->
                CompactSimpleItem(entry, busy, onEdit, false)
            }
            if (purchased.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("No carrinho (${purchased.size})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showPurchased = !showPurchased }) {
                            Text(if (showPurchased) "Ocultar" else "Mostrar")
                        }
                    }
                }
                if (showPurchased) {
                    items(purchased, key = { it.id }) { entry ->
                        CompactSimpleItem(entry, busy, onEdit, true, onUnmark)
                    }
                    item {
                        Button(
                            onClick = onFinish,
                            enabled = !busy && purchased.all { it.actual != null && it.actualMilli > 0 },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Concluir compra")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSimpleItem(
    entry: Entry,
    busy: Boolean,
    onEdit: (Entry) -> Unit,
    purchased: Boolean,
    onUnmark: (Entry) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (purchased) Icons.Default.Check else Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val quantity = BigDecimal.valueOf(entry.plannedMilli, 3).stripTrailingZeros().toPlainString().replace('.', ',')
                Text("$quantity ${entry.unit}${if (purchased && entry.actual != null) " · ${compactMoney(entry.actual)} / ${entry.unit}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (purchased) {
                TextButton(enabled = !busy, onClick = { onUnmark(entry) }) { Text("Voltar") }
            } else {
                TextButton(enabled = !busy, onClick = { onEdit(entry) }) { Text("Comprar") }
            }
            IconButton(enabled = !busy, onClick = { onEdit(entry) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Detalhes de ${entry.title}")
            }
        }
    }
}
