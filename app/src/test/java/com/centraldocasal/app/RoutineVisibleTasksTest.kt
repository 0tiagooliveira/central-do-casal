package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RoutineVisibleTasksTest {
    private val date=LocalDate.of(2026,10,18)
    private fun task(id:String, owner:String, start:String="2026-10-18",repeat:String="Única",review:Boolean=false) =
        Record(id,"task",fields=mapOf("title" to id,"owner" to owner,"date" to start,
            "time" to "12:00","repeat" to repeat,"needsReview" to review))

    @Test fun doesNotMixPartnerExclusiveTasksIntoMyDay() {
        val records=listOf(task("mine","Tiago"),task("partner","Rhayra"),task("both","Ambos"))
        assertEquals(setOf("mine","both"),routineVisibleTasks(records,date,"Tiago").map{it.id}.toSet())
        assertEquals(setOf("mine"),routineVisibleTasks(records,date,"Tiago","Para mim").map{it.id}.toSet())
        assertEquals(setOf("both"),routineVisibleTasks(records,date,"Tiago","Compartilhadas").map{it.id}.toSet())
    }

    @Test fun deepLinkCannotSwitchDayIntoAllDates() {
        val records=listOf(task("today","Tiago"),task("later","Tiago",start="2026-10-20"))
        assertEquals(listOf("today"),routineVisibleTasks(records,date,"Tiago").map{it.id})
    }

    @Test fun sharedItemNeedingReviewIsAccessibleInReviewScreen() {
        val records=listOf(task("shared","Ambos",review=true),
            task("partner","Rhayra",review=true),task("normal","Tiago"))
        assertEquals(listOf("shared"),routineVisibleTasks(records,date,"Tiago",reviewOnly=true).map{it.id})
    }

    @Test fun habitsOnlyUsesRealRecurringTasks() {
        val records=listOf(task("daily","Tiago",repeat="Diária"),task("once","Tiago"))
        assertEquals(listOf("daily"),routineVisibleTasks(records,date,"Tiago",habitsOnly=true).map{it.id})
    }
}
