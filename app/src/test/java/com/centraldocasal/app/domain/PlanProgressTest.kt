package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test

class PlanProgressTest {
    private val plan = Record("plan", "plan", true, mapOf("target" to 10000L))
    private fun contribution(id: String, amount: Long, isPrivate: Boolean = true) = Record(id, "contribution", isPrivate, mapOf("planId" to "plan", "amount" to amount, "date" to "2026-01-01"))
    @Test fun preservesCentsAndIgnoresCrossScopeAndOtherFinancialRecords() {
        val value = planProgress(plan, listOf(contribution("a", 3333), contribution("b", 2000, false), Record("bill", "plannedBill", true, mapOf("planId" to "plan", "amount" to 9999L))))
        assertEquals(3333L, value.registered); assertEquals(6667L, value.remaining); assertEquals(1, value.history.size)
    }
    @Test fun unknownTargetDoesNotInventPercentage() {
        val value = planProgress(plan.copy(fields = emptyMap()), listOf(contribution("a", 500)))
        assertNull(value.target); assertNull(value.remaining); assertEquals(0f, value.fraction, 0f); assertEquals(500L, value.registered)
    }
    @Test fun overTargetKeepsActualTotalAndCapsProgress() {
        val value = planProgress(plan, listOf(contribution("a", 12000)))
        assertEquals(12000L, value.registered); assertEquals(0L, value.remaining); assertEquals(1f, value.fraction, 0f)
    }
    @Test fun duplicateHistoryDoesNotCountTwice() { val entry = contribution("same", 400); assertEquals(400L, planProgress(plan, listOf(entry, entry)).registered) }
    @Test fun importedHousingPreferenceCannotBecomeFinancialGoal() { assertThrows(IllegalArgumentException::class.java) { planProgress(plan.copy(kind = "importedPlan"), emptyList()) } }
}
