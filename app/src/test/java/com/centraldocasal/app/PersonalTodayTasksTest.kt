package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalTodayTasksTest {
    @Test fun onlyOwnOrBothTasksAppearInPersonalToday() {
        val day = LocalDate.of(2026, 10, 14)
        fun task(id: String, owner: String, date: String = day.toString()): Record =
            Record(id, "task", fields = mapOf(
                "title" to id,
                "owner" to owner,
                "date" to date,
                "repeat" to "Única",
                "time" to "09:00"
            ))
        val records = listOf(
            task("rhayra-morning", "Rhayra"),
            task("tiago-evening", "Tiago"),
            task("shared", "Ambos"),
            task("tomorrow", "Tiago", day.plusDays(1).toString())
        )
        assertEquals(listOf("tiago-evening", "shared"),
            personalTodayTasks(records, day, "Tiago Oliveira").map { it.id })
        assertEquals(listOf("rhayra-morning", "shared"),
            personalTodayTasks(records, day, "Rhayra").map { it.id })
    }
}
