package com.centraldocasal.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private fun shoppingMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents,2))

internal data class ShoppingOverview(
    val budget:Long, val spent:Long, val planned:Long, val knownEstimated:Int,
    val missingPrices:Int, val count:Int, val purchased:Int
)

/** Não converte orçamento por lista em orçamento mensal nem apresenta preço desconhecido como zero. */
internal fun shoppingOverview(baskets:Collection<Basket>):ShoppingOverview {
    val entries=baskets.flatMap { it.entries }
    val bought=entries.filter { it.bought }
    return ShoppingOverview(
        budget=baskets.sumOf { it.budget },
        spent=bought.filter { it.actual!=null && it.actualMilli>0 }
            .sumOf { Money.subtotal(requireNotNull(it.actual),it.actualMilli) },
        planned=entries.filter { it.estimateReady() }.sumOf { it.expected() },
        knownEstimated=entries.count { it.estimateReady() },
        missingPrices=bought.count { it.actual==null },
        count=entries.size,
        purchased=bought.size
    )
}

@Composable
internal fun ShoppingReferenceDashboard(
    data:DataState,
    onMarket:(String)->Unit,
    onBudget:(String)->Unit,
    onHistory:()->Unit,
    onSuggestions:()->Unit
) {
    var selected by rememberSaveable { mutableStateOf("Listas") }
    val baskets=listOf("Açougue","Mercado").map { data.baskets[it] ?: Basket(it) }
    val overview=shoppingOverview(baskets)
    val purchases=baskets.flatMap { it.entries }
        .filter { it.bought && it.actual!=null && it.actualMilli>0 }
    val categories=purchases.groupBy { it.category.ifBlank {
        if(it.recipient.contains("Lucca",true)||it.recipient.contains("Mel",true)) "Cuidados (Lucca e Mel)"
        else "Outros"
    } }.mapValues { (_,list) -> list.sumOf { Money.subtotal(requireNotNull(it.actual),it.actualMilli) } }
        .entries.sortedByDescending { it.value }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item { ReferenceHeading("Compras", "Listas, gastos e histórico de vocês") }
        item { ReferenceTabs(listOf("Listas","Orçamento","Histórico","Sugestões"),selected) { tab ->
            selected=tab
            if(tab=="Histórico") onHistory()
            if(tab=="Sugestões") onSuggestions()
        } }
        if(selected=="Listas" || selected=="Orçamento") {
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxWidth()) {
                    baskets.forEach { basket ->
                        val entries=basket.entries
                        val total=entries.filter { it.bought && it.actual!=null }.sumOf {
                            Money.subtotal(requireNotNull(it.actual),it.actualMilli)
                        }
                        Surface(onClick={onMarket(basket.market)},modifier=Modifier.weight(1f).heightIn(min=114.dp),
                            shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.48f)) {
                            Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.ShoppingCart,null,tint=MaterialTheme.colorScheme.primary,
                                    modifier=Modifier.size(23.dp))
                                Text(basket.market,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text("${entries.size} itens · ${entries.count{it.bought}} comprados",
                                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(shoppingMoney(total),style=MaterialTheme.typography.titleSmall,
                                    fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item {
                ReferenceCard(Modifier.fillMaxWidth()) {
                    Text("Orçamento das listas",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold)
                    if(overview.budget>0) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(shoppingMoney(overview.spent),style=MaterialTheme.typography.headlineMedium,
                                    fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                                Text("de ${shoppingMoney(overview.budget)} configurados",
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${overview.spent*100/overview.budget}%",
                                style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                        }
                        ReferenceProgress(overview.spent.toFloat()/overview.budget)
                    } else {
                        Text("Defina um orçamento em Mercado ou Açougue para acompanhar os gastos.",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${overview.purchased} de ${overview.count} itens comprados · ${overview.missingPrices} sem preço real",
                        style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={onBudget("Mercado")}) { Text("Editar orçamento do mercado") }
                }
            }
            item {
                Text("Categorias",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
            if(categories.isEmpty()) item { ReferenceEmpty("Histórico de categorias ainda vazio",
                "Os valores surgem depois que vocês registrarem compras com preço real e categoria.") }
            else items(categories.size) { index ->
                val (category,total)=categories[index]
                ReferenceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(category,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                            fontWeight=FontWeight.SemiBold)
                        Text(shoppingMoney(total),style=MaterialTheme.typography.bodyMedium)
                    }
                    if(overview.spent>0) ReferenceProgress(total.toFloat()/overview.spent)
                }
            }
            item {
                Text("Total previsto confirmado: ${shoppingMoney(overview.planned)} em ${overview.knownEstimated} itens. Estimativas ausentes não entram na soma.",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
