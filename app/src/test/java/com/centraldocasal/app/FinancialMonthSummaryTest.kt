package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class FinancialMonthSummaryTest {
    private fun tx(id: String, date: String, type: String, amount: Long) =
        Record(id, "transaction", true,
            mapOf("date" to date, "type" to type, "amount" to amount))

    @Test fun excludesInvalidDatesAndOtherMonths() {
        val records = listOf(
            tx("income", "2026-10-01", "Receita", 350000),
            tx("expense", "2026-10-15", "Despesa", 12345),
            tx("future", "2026-11-01", "Despesa", 2000),
            tx("malformed", "2026-10-99", "Despesa", 2000),
            Record("imported", "importedReceipt", fields = mapOf("date" to "2026-10-15")))
        val month = financialMonthRecords(records, YearMonth.of(2026, 10))
        assertEquals(setOf("income", "expense"), month.map { it.id }.toSet())
        val result = financialMonthSummary(month)
        assertEquals(350000L, result.incomeCents)
        assertEquals(12345L, result.expenseCents)
        assertEquals(337655L, result.resultCents)
        assertEquals(2, result.entryCount)
    }

    @Test fun emptyMonthIsZeroAndNotEstimatedFromBills() {
        val empty = financialMonthSummary(
            financialMonthRecords(listOf(Record("bill", "plannedBill",
                fields = mapOf("month" to "2026-10", "amount" to 500000L))),
                YearMonth.of(2026,10)))
        assertEquals(0L, empty.resultCents)
        assertEquals(0, empty.entryCount)
    }
}
