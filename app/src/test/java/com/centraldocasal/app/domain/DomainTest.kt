package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    @Test fun exactBrazilianCurrency() { assertEquals(123456L, Money.cents("R$ 1.234,56")); assertEquals(10L, Money.cents("0,10")) }
    @Test fun fractionalMeat() { assertEquals(500L, Money.quantity("0,5")); assertEquals(1995L, Money.subtotal(3990, 500)); assertEquals(2L, Money.subtotal(3, 500)) }
    @Test fun gramsAndKilogramsAgree() { assertEquals(Money.subtotal(4000, Money.quantity("0,5")), Money.subtotal(4, Money.quantity("500"))) }
    @Test(expected = IllegalArgumentException::class) fun rejectOverprecisionPrice() { Money.cents("1,234") }
    @Test(expected = IllegalArgumentException::class) fun rejectZeroQuantity() { Money.quantity("0") }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativeMoney() { Money.cents("-10") }
    @Test fun separatePriceAndQuantityVariance() { val e = Entry(title = "Carne", unit = "kg", plannedMilli = 1000, actualMilli = 1500, estimated = 2000, actual = 1800, bought = true); assertEquals(-300L, e.priceDifference()); assertEquals(1000L, e.quantityDifference()); assertEquals(e.real() - e.expected(), e.priceDifference() + e.quantityDifference()) }
    @Test fun marketBudgetsRemainSeparate() { val a = Basket("Mercado", budget = 10000, entries = listOf(Entry(estimated = 500))); val b = Basket("Açougue", budget = 20000, entries = listOf(Entry(estimated = 1500))); assertEquals(500L, a.expected()); assertEquals(1500L, b.expected()); assertEquals(10000L, a.budget); assertEquals(20000L, b.budget) }
    @Test(expected = IllegalArgumentException::class) fun noCloseWithoutRealPrice() { Basket("Mercado", entries = listOf(Entry(title = "Pão", bought = true))).validateClose() }
    @Test(expected = IllegalArgumentException::class) fun noCloseWithNoPurchasedItems() { Basket("Mercado").validateClose() }
    @Test fun onlyBoughtItemsCountAsReal() { val b = Basket("Mercado", entries = listOf(Entry(estimated = 500, actual = 600, bought = true), Entry(estimated = 800))); assertEquals(1300L, b.expected()); assertEquals(600L, b.real()); b.validateClose() }
    @Test fun completedOccurrenceDoesNotCompleteTomorrow() { val today = LocalDate.of(2026, 10, 10); val task = Record(id = "task-1", kind = "task", fields = mapOf("date" to today.toString(), "repeat" to "Diária")); assertTrue(occurs(task, today)); assertTrue(occurs(task, today.plusDays(1))); assertNotEquals(occurrenceId(task.id, today), occurrenceId(task.id, today.plusDays(1))) }
    @Test fun weeklyOnlySameWeekdayAfterStart() { val day = LocalDate.of(2026, 10, 10); val task = Record(kind = "task", fields = mapOf("date" to day.toString(), "repeat" to "Semanal")); assertFalse(occurs(task, day.minusDays(7))); assertTrue(occurs(task, day.plusDays(7))); assertFalse(occurs(task, day.plusDays(1))) }
    @Test fun quietWindowAndOvernight() { assertFalse(quietAt(0, 4)); assertTrue(quietAt(0, 5)); assertTrue(quietAt(5, 39)); assertFalse(quietAt(5, 40)); assertTrue(quietAt(23, 0, "22:00", "08:00")); assertTrue(quietAt(7, 59, "22:00", "08:00")); assertFalse(quietAt(12, 0, "22:00", "08:00")) }

    @Test fun shoppingEntryRoundTripKeepsPlanOrigin() {
        val original = Entry(
            id = "linked",
            title = "Material do projeto",
            sourcePlanId = "plan-1",
            sourceStepId = "step-2",
            estimateKnown = false,
            unitReviewed = false,
            quantityReviewed = false
        )
        val restored = Entry.from(original.toMap())
        assertEquals("plan-1", restored.sourcePlanId)
        assertEquals("step-2", restored.sourceStepId)
        assertFalse(restored.estimateKnown)
        assertFalse(restored.unitReviewed)
        assertFalse(restored.quantityReviewed)
    }

}
