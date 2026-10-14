package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ShoppingDecisionTest {
    private fun row(date: String, price: String, unit: String = "kg") = mapOf("date" to date, "preco_unitario_reais" to price, "unidade" to unit)
    private fun sku(id: String, vararg history: Map<String, String>) = Record(id, "catalogSku", fields = mapOf("history" to history.toList()))
    private fun linked(skuId: String, status: String = "confirmed") = Record(skuId, "catalogLink", fields = mapOf("skuId" to skuId, "baseItemId" to "base", "status" to status))
    private fun assertDecimal(expected: String, actual: BigDecimal) { assertEquals(0, BigDecimal(expected).compareTo(actual)) }

    @Test fun quickEntryRequiresTypedActualPriceAndConfirmsPositiveQuantityWithoutInventingEstimate() {
        val draft = Entry(unit = "kg", plannedMilli = 0, quantityReviewed = false, estimateKnown = false, sourceSkuId = "sku")
        val ready = prepareQuickCartEntry(draft, "0,5", "12,49")
        assertEquals(1249L, ready.actual)
        assertEquals(500L, ready.actualMilli)
        assertEquals(500L, ready.plannedMilli)
        assertTrue(ready.bought && ready.quantityReviewed)
        assertFalse(ready.estimateKnown)
        assertEquals("sku", ready.sourceSkuId)
        Basket("Mercado", entries = listOf(ready)).validateClose()
    }

    @Test fun quickEntryRejectsUnconfirmedUnitZeroQuantityBlankPriceAndOverprecision() {
        val entry = Entry(unit = "kg")
        for ((item, quantity, price) in listOf(Triple(entry.copy(unit = "Escolher", unitReviewed = false), "1", "5"), Triple(entry.copy(unitReviewed = false), "1", "5"), Triple(entry, "0", "5"), Triple(entry, "1", ""), Triple(entry, "1", "1.234"), Triple(entry, "1", "-1"))) {
            assertThrows(IllegalArgumentException::class.java) { prepareQuickCartEntry(item, quantity, price) }
        }
    }

    @Test fun comparesLatestDocumentedPriceOfOnlySelectedSkuAndSameUnit() {
        val records = listOf(sku("selected", row("2026-02-01", "1.2345"), row("2026-01-01", "9.99"), row("2026-03-01", "0.01", "g")), sku("other", row("2026-04-01", "100")))
        val decision = compareShoppingPrice(Entry(unit = "kg", sourceSkuId = "selected", actual = 123), records)!!
        assertEquals("selected", decision.skuId)
        assertEquals("2026-02-01", decision.documentDate)
        assertDecimal("1.2345", decision.documentedPrice)
        assertDecimal("-0.0045", decision.difference)
        assertEquals(PriceTrend.LOWER, decision.trend)
        assertTrue(decision.percent!! < BigDecimal.ZERO)
    }

    @Test fun explicitSkuOrOneManualConfirmedLinkRequiredInsteadOfProductNameGuessing() {
        val entry = Entry(unit = "kg", sourceBaseId = "base", actual = 200)
        val catalog = listOf(sku("one", row("2026-01-01", "1")), sku("two", row("2026-01-01", "3")))
        assertNull(compareShoppingPrice(entry, catalog))
        assertNull(compareShoppingPrice(entry, catalog + linked("one", "separate")))
        assertNull(compareShoppingPrice(entry, catalog + listOf(linked("one"), linked("two"))))
        assertEquals(PriceTrend.HIGHER, compareShoppingPrice(entry, catalog + linked("one"))!!.trend)
        assertEquals(PriceTrend.LOWER, compareShoppingPrice(entry.copy(sourceSkuId = "two"), catalog + linked("one"))!!.trend)
    }

    @Test fun absentInvalidOrDifferentUnitHistoryNeverBecomesZeroReference() {
        val entry = Entry(unit = "kg", sourceSkuId = "sku", actual = 200)
        for (records in listOf(emptyList(), listOf(sku("sku", row("2026-01-01", "1", "g"))), listOf(sku("sku", row("bad-date", "1"), row("2026-01-01", "not-a-price"), row("2026-02-01", "-1"))))) {
            assertNull(compareShoppingPrice(entry, records))
        }
        assertNull(compareShoppingPrice(entry.copy(unitReviewed = false), listOf(sku("sku", row("2026-01-01", "1")))))
        assertNull(compareShoppingPrice(entry.copy(actual = null), listOf(sku("sku", row("2026-01-01", "1")))))
    }

    @Test fun lastObservationWithinOneDateWinsAndZeroBaselineHasNoPercentage() {
        val records = listOf(sku("sku", row("2026-01-01", "1"), row("2026-01-01", "0")))
        val decision = compareShoppingPrice(Entry(unit = "kg", sourceSkuId = "sku", actual = 0), records)!!
        assertEquals(PriceTrend.SAME, decision.trend)
        assertDecimal("0", decision.difference)
        assertNull(decision.percent)
    }
}
