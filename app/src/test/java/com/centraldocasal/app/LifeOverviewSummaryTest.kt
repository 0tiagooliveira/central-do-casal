package com.centraldocasal.app

import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LifeOverviewSummaryTest {
    private val day = LocalDate.of(2026,10,18)
    private fun task(id:String, owner:String) = Record(id,"task",fields=mapOf(
        "title" to id,"date" to "2026-10-18","repeat" to "Única","owner" to owner))

    @Test fun homeTimelineExcludesOtherPersonsTasksAndCompletedOnes() {
        val done = Record(occurrenceId("shared",day),"occurrence",fields=mapOf("done" to true))
        val data = DataState(records=listOf(task("mine","Tiago"),task("partner","Rhayra"),
            task("shared","Ambos"),done))
        val summary = lifeOverviewSummary(data,"Tiago",false,day)
        assertEquals(1,summary.pendingTasks)
    }

    @Test fun sharedMomentAndOnlyCurrentMonthBillsAreShown() {
        val records=listOf(
            Record("private","moment",true, mapOf("title" to "Privado")),
            Record("shared","moment",false, mapOf("title" to "Compartilhado")),
            Record("now","plannedBill",fields=mapOf("month" to "2026-10")),
            Record("old","plannedBill",fields=mapOf("month" to "2026-09","amount" to 3000L)),
            Record("plan","plan",fields=mapOf("steps" to listOf(
                mapOf("done" to false),mapOf("done" to true),mapOf("title" to "Revisar"))))
        )
        val shared = lifeOverviewSummary(DataState(records=records),"Rhayra",false,day)
        assertEquals(1,shared.moments)
        assertEquals(1,shared.plannedBills)
        assertEquals(1,shared.unconfirmedBills)
        assertEquals(1,shared.pendingSteps)
        assertEquals(1,shared.unreviewedSteps)
        assertEquals(2,lifeOverviewSummary(DataState(records=records),"Rhayra",true,day).moments)
    }
}
