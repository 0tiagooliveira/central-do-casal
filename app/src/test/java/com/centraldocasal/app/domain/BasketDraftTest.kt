package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test

class BasketDraftTest {
    @Test fun partialForecastIncludesOnlyConfirmedEstimates() {
        val confirmed = Entry(title = "Confirmado", estimated = 350, plannedMilli = 2000)
        val unknown = Entry(title = "Sem preço", estimated = 9999, estimateKnown = false)
        val draftUnit = Entry(title = "Rascunho", unit = "Escolher", estimated = 9999, unitReviewed = false)
        val draftQuantity = Entry(title = "Sem quantidade", estimated = 9999, quantityReviewed = false)
        val basket = Basket("Mercado", entries = listOf(confirmed, unknown, draftUnit, draftQuantity))
        assertEquals(700L, basket.expected())
        assertEquals(0L, unknown.expected())
        assertEquals(2, basket.unreviewedCount())
        assertEquals(1, basket.unknownEstimateCount())
        assertEquals(10699L, basket.copy(entries = listOf(confirmed, unknown.copy(estimateKnown = true))).expected())
    }

    @Test fun checkoutRejectsPlaceholderOrUnconfirmedUnits() {
        val purchased = Entry(title = "Produto fixture", actual = 200, bought = true)
        for (draft in listOf(purchased.copy(unit = "Escolher"), purchased.copy(unitReviewed = false), purchased.copy(unit = "caixa"))) {
            val error = assertThrows(IllegalArgumentException::class.java) { Basket("Mercado", entries = listOf(draft)).validateClose() }
            assertTrue(error.message.orEmpty().contains("unidade"))
        }
        for (unit in Entry.allowedUnits) Basket("Mercado", entries = listOf(purchased.copy(unit = unit))).validateClose()
    }

    @Test fun checkoutRejectsUnconfirmedOrNonpositiveQuantities() {
        val purchased = Entry(title = "Produto fixture", actual = 200, bought = true)
        for (draft in listOf(purchased.copy(quantityReviewed = false), purchased.copy(actualMilli = 0), purchased.copy(plannedMilli = -1000))) {
            val error = assertThrows(IllegalArgumentException::class.java) { Basket("Mercado", entries = listOf(draft)).validateClose() }
            assertTrue(error.message.orEmpty().contains("quantidade"))
        }
    }

    @Test fun unknownEstimateCanCloseWithRealPriceWithoutInventingVariance() {
        val purchased = Entry(actual = 249, bought = true, actualMilli = 2000, estimateKnown = false)
        val unpurchasedDraft = Entry(unit = "Escolher", unitReviewed = false, quantityReviewed = false, estimateKnown = false)
        val basket = Basket("Mercado", entries = listOf(purchased, unpurchasedDraft))
        basket.validateClose()
        assertEquals(498L, basket.real())
        assertEquals(0L, basket.expected())
        assertEquals(0L, purchased.priceDifference())
        assertEquals(0L, purchased.quantityDifference())
        assertEquals(0L, purchased.copy(unitReviewed = false).priceDifference())
        assertEquals(0L, purchased.copy(quantityReviewed = false).quantityDifference())
    }

    @Test fun legacySnapshotKeepsConfirmedDefaultsAndOriginalPositionalConstructor() {
        val legacy = mapOf<String, Any?>("id" to "legacy", "title" to "Produto fixture", "unit" to "kg", "plannedMilli" to 1500L, "actualMilli" to 2000L, "estimated" to 500L, "actual" to 400L, "bought" to true)
        val entry = Entry.from(legacy)
        assertEquals(Entry("legacy", "Produto fixture", "kg", 1500, 2000, 500, 400, true), entry)
        assertTrue(entry.estimateKnown && entry.unitReviewed && entry.quantityReviewed)
        assertEquals(750L, entry.expected())
        Basket("Mercado", entries = listOf(entry)).validateClose()
    }

    @Test fun savedBasketPreservesDraftFlagsAndSourceReferencesInSnapshot() {
        val draft = Entry(id = "draft", title = "Produto fixture", unit = "Escolher", estimateKnown = false, unitReviewed = false, quantityReviewed = false, sourceBaseId = "fixture-base", sourceSkuId = "fixture-sku", category = "Despensa")
        val original = Basket("Mercado", generation = "fixture-generation", budget = 10000, revision = 4, entries = listOf(draft))
        val restored = Basket.from(original.toMap())
        assertEquals(original, restored)
        val edited = restored.copy(entries = restored.entries.map { it.copy(unit = "un", unitReviewed = true, quantityReviewed = true) })
        assertEquals("fixture-base", edited.entries.single().sourceBaseId)
        assertEquals("fixture-sku", edited.entries.single().sourceSkuId)
        assertEquals("Despensa", edited.entries.single().category)
        assertFalse(restored.entries.single().unitReviewed)
        assertEquals(0L, edited.expected()) // Unit confirmation does not invent an estimated price.
    }
}
