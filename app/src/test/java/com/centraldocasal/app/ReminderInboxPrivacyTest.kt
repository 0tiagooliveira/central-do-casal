package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReminderInboxPrivacyTest {
    private val today=LocalDate.of(2026,10,18)
    private fun task(id:String,owner:String,time:String="12:00")=Record(
        id,"task",fields=mapOf("title" to id,"owner" to owner,
            "date" to "2026-10-18","repeat" to "Única","time" to time)
    )

    @Test fun personalInboxExcludesOtherMembersAssignments() {
        val all=listOf(task("t","Tiago"),task("r","Rhayra"),task("both","Ambos"))
        assertEquals(setOf("t","both"),reminderVisibleTasks(all,"Tiago",today).map{it.id}.toSet())
        assertEquals(setOf("r","both"),reminderVisibleTasks(all,"Rhayra",today).map{it.id}.toSet())
    }

    @Test fun completedAndOtherDateItemsAreNotPending() {
        val t=task("done","Tiago")
        val other=Record("other","task",fields=mapOf("owner" to "Tiago",
            "date" to "2026-10-19","time" to "09:00","repeat" to "Única"))
        val done=Record(occurrenceId(t.id,today),"occurrence",fields=mapOf("done" to true))
        assertTrue(reminderVisibleTasks(listOf(t,other,done),"Tiago",today).isEmpty())
    }

    @Test fun untimedTasksStillAppearForManualReviewWithoutFakeAlarm() {
        val tasks=reminderVisibleTasks(listOf(task("review","Tiago","")),"Tiago",today)
        assertEquals(listOf("review"),tasks.map{it.id})
        assertTrue(tasks.single().text("time").isBlank())
    }
}
