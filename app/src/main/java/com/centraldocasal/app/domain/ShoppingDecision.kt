package com.centraldocasal.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

data class ShoppingPriceDecision(
    val skuId: String,
    val documentDate: String,
    val unit: String,
    val documentedPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val difference: BigDecimal,
    val percent: BigDecimal?,
    val trend: PriceTrend
)

fun prepareQuickCartEntry(entry: Entry, quantityText: String, priceText: String): Entry {
    require(entry.unitReviewed && entry.unit in Entry.allowedUnits) { "Confirme a unidade na ficha do produto." }
    val quantity = Money.quantity(quantityText)
    val price = Money.cents(priceText)
    return entry.copy(actualMilli = quantity, plannedMilli = if (entry.plannedMilli > 0) entry.plannedMilli else quantity,
        actual = price, bought = true, quantityReviewed = true)
}

/** Only one explicit SKU or one unambiguous, manually confirmed base link may supply history. */
fun compareShoppingPrice(entry: Entry, records: List<Record>, currentUnitPriceCents: Long? = entry.actual): ShoppingPriceDecision? {
    if (!entry.unitReviewed || entry.unit !in Entry.allowedUnits || currentUnitPriceCents == null || currentUnitPriceCents < 0) return null
    val skuId = entry.sourceSkuId.takeIf { it.isNotBlank() } ?: run {
        if (entry.sourceBaseId.isBlank()) return null
        val links = records.filter { it.kind == "catalogLink" && !it.isPrivate && it.text("status") == "confirmed" && it.text("baseItemId") == entry.sourceBaseId }
            .map { it.text("skuId") }.filter { it.isNotBlank() }.distinct()
        if (links.size != 1) return null
        links.single()
    }
    val sku = records.singleOrNull { it.id == skuId && it.kind == "catalogSku" && !it.isPrivate } ?: return null
    val unit = entry.unit.uppercase(Locale.ROOT)
    val history = (sku.fields["history"] as? List<*>)?.mapNotNull { raw ->
        val row = raw as? Map<*, *> ?: return@mapNotNull null
        val rowUnit = (row["unidade"] as? String)?.trim()?.uppercase(Locale.ROOT)
        if (rowUnit != unit) return@mapNotNull null
        val date = runCatching { LocalDate.parse(row["date"] as? String ?: "") }.getOrNull() ?: return@mapNotNull null
        val price = runCatching { BigDecimal((row["preco_unitario_reais"] as? String ?: "").trim()) }.getOrNull() ?: return@mapNotNull null
        if (price.signum() < 0) return@mapNotNull null
        date to price
    }?.sortedBy { it.first } ?: return null
    val latest = history.lastOrNull() ?: return null
    val current = BigDecimal.valueOf(currentUnitPriceCents, 2)
    val difference = current.subtract(latest.second)
    val percent = if (latest.second.signum() == 0) null else difference.multiply(BigDecimal("100")).divide(latest.second, 8, RoundingMode.HALF_UP)
    val trend = when (difference.signum()) { -1 -> PriceTrend.LOWER; 1 -> PriceTrend.HIGHER; else -> PriceTrend.SAME }
    return ShoppingPriceDecision(skuId, latest.first.toString(), entry.unit, latest.second, current, difference, percent, trend)
}
