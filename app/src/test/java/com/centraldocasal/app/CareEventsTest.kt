package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CareEventsTest {
    @Test fun selectedDayUsesOnlyPersistedCareEventsAndOrdersNewestFirst() {
        val day = LocalDate.of(2026, 10, 13)
        val records = listOf(
            Record("old", "careEvent", fields = mapOf(
                "careType" to "diaper", "subject" to "Lucca", "actorUid" to "a",
                "actorName" to "A", "occurredAt" to 100L, "date" to "2026-10-13"
            )),
            Record("other-day", "careEvent", fields = mapOf(
                "careType" to "bath", "subject" to "Lucca", "actorUid" to "b",
                "actorName" to "B", "occurredAt" to 300L, "date" to "2026-10-12"
            )),
            Record("task", "task", fields = mapOf("date" to "2026-10-13", "title" to "Não é cuidado")),
            Record("new", "careEvent", fields = mapOf(
                "careType" to "petFood", "subject" to "Mel", "actorUid" to "b",
                "actorName" to "B", "occurredAt" to 200L, "date" to "2026-10-13"
            ))
        )

        val result = careEventsForDay(records, day)

        assertEquals(listOf("new", "old"), result.map { it.id })
        assertEquals(1, result.count { it.text("careType") == "diaper" })
        assertEquals(1, result.count { it.text("careType") == "petFood" })
        assertEquals(0, result.count { it.text("careType") == "bath" })
    }
}
