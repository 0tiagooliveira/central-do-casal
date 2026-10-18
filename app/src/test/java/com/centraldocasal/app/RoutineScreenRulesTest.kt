package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RoutineScreenRulesTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun task(time: String, reviewTime: Boolean = false, reviewTask: Boolean = false) =
        Record("task-$time", "task", fields = mapOf(
            "title" to "Atividade",
            "time" to time,
            "owner" to "Tiago",
            "needsTimeReview" to reviewTime,
            "needsReview" to reviewTask,
            "date" to today.toString(),
            "repeat" to "Única"
        ))

    @Test fun deepLinkToOtherDayOpensWeekPicker() {
        assertEquals("Semana", initialRoutineSection("2026-10-19", today))
        assertEquals("Semana", initialRoutineSection("2026-10-17", today))
        assertEquals("Hoje", initialRoutineSection("2026-10-18", today))
        assertEquals("Hoje", initialRoutineSection(null, today))
        assertEquals("Hoje", initialRoutineSection("invalid", today))
    }

    @Test fun midnightAndEarlyHoursAreSeparateFromNight() {
        assertEquals("Madrugada", routineTaskPeriod(task("00:00")))
        assertEquals("Madrugada", routineTaskPeriod(task("04:59")))
        assertEquals("Manhã", routineTaskPeriod(task("05:00")))
        assertEquals("Manhã", routineTaskPeriod(task("11:59")))
        assertEquals("Tarde", routineTaskPeriod(task("12:00")))
        assertEquals("Tarde", routineTaskPeriod(task("17:59")))
        assertEquals("Noite", routineTaskPeriod(task("18:00")))
        assertEquals("Noite", routineTaskPeriod(task("23:59")))
    }

    @Test fun invalidOrUnconfirmedHourIsNeverScheduled() {
        assertEquals("Sem horário", routineTaskPeriod(task("")))
        assertEquals("Sem horário", routineTaskPeriod(task("25:70")))
        assertEquals("Sem horário", routineTaskPeriod(task("18:00", reviewTime=true)))
        assertEquals("Sem horário", routineTaskPeriod(task("18:00", reviewTask=true)))
    }

    @Test fun eachOrdinaryTaskIsAssignedToExactlyOnePeriod() {
        val periods = listOf("Madrugada", "Manhã", "Tarde", "Noite", "Sem horário")
        val tasks = listOf("00:00", "07:50", "12:00", "15:30", "22:00", "")
            .map(::task)
        tasks.forEach { item ->
            assertEquals(1, periods.count { it == routineTaskPeriod(item) })
        }
    }
}
