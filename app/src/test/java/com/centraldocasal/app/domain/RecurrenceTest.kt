package com.centraldocasal.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun task(repeat: String, start: String = "2026-01-01", extra: Map<String, Any?> = emptyMap()) =
        Record(kind = "task", fields = mapOf("date" to start, "repeat" to repeat) + extra)

    private fun on(task: Record, date: String) = occurs(task, LocalDate.parse(date))

    @Test fun multipleIsoWeekdaysRepeatAcrossWeeks() {
        val task = task("Dias da semana", "2026-10-12", mapOf("days" to listOf(1L, 3L, 7L)))
        assertFalse(on(task, "2026-10-11")) // Selected Sunday before start.
        assertTrue(on(task, "2026-10-12"))
        assertFalse(on(task, "2026-10-13"))
        assertTrue(on(task, "2026-10-14"))
        assertTrue(on(task, "2026-10-18"))
        assertTrue(on(task, "2026-10-19"))
        assertFalse(on(task, "2026-10-24"))
    }

    @Test fun weekdayListRequiresValidNumericIsoDays() {
        val task = task("Dias da semana", extra = mapOf("days" to listOf(0, 8, "1", 1.5)))
        for (offset in 0L..13L) assertFalse(occurs(task, LocalDate.of(2026, 1, 1).plusDays(offset)))
        assertFalse(on(task("Dias da semana"), "2026-01-05"))
    }

    @Test fun monthlyEighthStaysOnEighth() {
        val task = task("Mensal", "2026-01-08")
        assertFalse(on(task, "2025-12-08"))
        assertTrue(on(task, "2026-01-08"))
        assertTrue(on(task, "2026-02-08"))
        assertFalse(on(task, "2026-02-07"))
        assertFalse(on(task, "2026-02-09"))
        assertTrue(on(task, "2027-01-08"))
    }

    @Test fun monthlyThirtyFirstClampsAndRestoresOriginalDay() {
        val task = task("Mensal", "2026-01-31")
        assertFalse(on(task, "2026-01-30"))
        assertTrue(on(task, "2026-02-28"))
        assertFalse(on(task, "2026-03-28"))
        assertTrue(on(task, "2026-03-31"))
        assertTrue(on(task, "2026-04-30"))
        assertTrue(on(task, "2028-02-29"))
        assertFalse(on(task, "2028-02-28"))
    }

    @Test fun sundayAfterEighthExcludesEighthWhenItIsSunday() {
        val task = task("Primeiro domingo após dia 8")
        assertFalse(on(task, "2026-02-08"))
        assertTrue(on(task, "2026-02-15"))
        assertFalse(on(task, "2026-02-22"))
        assertFalse(on(task, "2026-02-14"))
    }

    @Test fun sundayAfterEighthCanBeNinthAndHonorsStart() {
        val task = task("Primeiro domingo após dia 8", "2026-08-10")
        assertFalse(on(task, "2026-08-09"))
        assertFalse(on(task, "2026-08-16"))
        assertTrue(on(task, "2026-09-13"))
        assertTrue(on(task, "2027-05-09"))
    }

    @Test fun unresolvedReviewNeverBecomesAnActiveOccurrence() {
        for (repeat in listOf("Única", "Diária", "Semanal", "Dias da semana", "Mensal", "Primeiro domingo após dia 8")) {
            val task = task(repeat, "2026-08-09", mapOf("days" to listOf(7), "needsReview" to true))
            assertFalse(on(task, "2026-08-09"))
            assertFalse(on(task, "2026-08-16"))
            assertFalse(on(task, "2027-05-09"))
        }
    }

    @Test fun reminderOptOutDoesNotHideAnOtherwiseValidTask() {
        assertTrue(on(task("Diária", extra = mapOf("skipReminder" to true)), "2026-01-02"))
    }
}
