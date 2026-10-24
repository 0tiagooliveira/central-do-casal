package com.centraldocasal.app

import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.productPurchases
import java.util.Locale
import java.util.UUID

/**
 * Real purchases only: checked-out in-app receipts and items with documented fiscal history.
 * Suggestions never infer an unrecorded purchase or merge two different units.
 */
internal data class RepurchaseSuggestion(
    val key: String,
    val title: String,
    val unit: String,
    val lastDate: String,
    val store: String,
    val purchaseCount: Int,
    val draft: Entry
)

private fun reuseKey(title: String, unit: String): String =
    title.trim().lowercase(Locale.forLanguageTag("pt-BR")).replace(Regex("\\s+"), " ") +
        "|" + unit.trim().lowercase(Locale.ROOT)

private fun validUnit(unit: String): String = when(unit.trim().uppercase(Locale.ROOT)) {
    "UN", "UND", "UNIDADE" -> "un"
    "KG" -> "kg"
    "G" -> "g"
    "L" -> "L"
    "ML" -> "mL"
    "PACOTE" -> "pacote"
    else -> "Escolher"
}

internal fun repurchaseSuggestions(records: List<Record>, market: String): List<RepurchaseSuggestion> {
    if (market !in setOf("Mercado", "Açougue")) return emptyList()
    val candidates = mutableListOf<RepurchaseSuggestion>()
    records.asSequence()
        .filter { !it.isPrivate && it.kind in setOf("catalogItem", "catalogSku") && it.text("market") == market }
        .forEach { product ->
            val purchases = productPurchases(product, records)
            val latest = purchases.firstOrNull() ?: return@forEach
            val name = product.text("displayName").ifBlank { product.text("title") }.trim()
            if(name.isBlank()) return@forEach
            val draft = catalogDraft(product, records).copy(title = name)
            candidates += RepurchaseSuggestion(
                "catalog:${product.id}", name, draft.unit, latest.date,
                latest.store, purchases.size, draft
            )
        }

    // Previously closed app baskets may not have been mapped into the catalog yet.
    records.asSequence().filter { !it.isPrivate && it.kind == "receipt" && it.text("market") == market }
        .forEach { receipt ->
            val purchased = receipt.rows("entries").map(Entry::from).filter { it.bought && it.title.isNotBlank() }
            purchased.forEach { original ->
                val unit = validUnit(original.unit)
                val knownPrice = original.actual != null && original.actual!! >= 0
                val hasQuantity = original.actualMilli > 0
                val draft = original.copy(
                    id = UUID.randomUUID().toString(),
                    bought = false,
                    unit = unit,
                    plannedMilli = original.actualMilli.takeIf { it > 0 } ?: 1000,
                    actualMilli = original.actualMilli.takeIf { it > 0 } ?: 1000,
                    actual = null,
                    estimated = original.actual?.takeIf { it >= 0 } ?: 0,
                    estimateKnown = knownPrice && unit != "Escolher",
                    unitReviewed = unit != "Escolher",
                    quantityReviewed = hasQuantity
                )
                candidates += RepurchaseSuggestion(
                    "receipt:${receipt.id}:${original.id}",
                    original.title.trim(), draft.unit, receipt.text("date"),
                    market, 1, draft
                )
            }
        }

    // Exact title + unit is the only automatic grouping. No fuzzy product identity.
    return candidates.sortedWith(compareByDescending<RepurchaseSuggestion> { it.lastDate }.thenBy { it.title })
        .distinctBy { reuseKey(it.title, it.unit) }
        .sortedWith(compareByDescending<RepurchaseSuggestion> { it.lastDate }.thenBy { it.title })
}

internal fun alreadyOnShoppingList(suggestion: RepurchaseSuggestion, entries: List<Entry>): Boolean =
    entries.any { entry ->
        (suggestion.draft.sourceSkuId.isNotBlank() && entry.sourceSkuId == suggestion.draft.sourceSkuId) ||
            (suggestion.draft.sourceBaseId.isNotBlank() && entry.sourceBaseId == suggestion.draft.sourceBaseId) ||
            reuseKey(entry.title,entry.unit) == reuseKey(suggestion.title,suggestion.unit)
    }
