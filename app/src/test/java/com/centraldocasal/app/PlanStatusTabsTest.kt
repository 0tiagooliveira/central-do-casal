package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test

class PlanStatusTabsTest {
    private fun plan(id:String,steps: List<Map<String, Any?>> = emptyList()) =
        Record(id,"plan",fields=mapOf("title" to id,"steps" to steps))
    private val next=plan("next")
    private val started=plan("started",listOf(mapOf("id" to "1","title" to "Etapa 1","done" to true),
        mapOf("id" to "2","title" to "Etapa 2","done" to false)))
    private val finished=plan("finished",listOf(mapOf("id" to "1","done" to true)))
    private val contribution=Record("c","contribution",fields=mapOf("planId" to "next","amount" to 500L))

    @Test fun tabStagesAreMutuallyExclusive() {
        val plans=listOf(next,started,finished)
        val states=plans.associate { it.id to listOf("Ativos","Próximos","Concluídos").filter { status ->
            planMatchesStatus(it,plans,status)
        } }
        assertEquals(listOf("Próximos"),states.getValue("next"))
        assertEquals(listOf("Ativos"),states.getValue("started"))
        assertEquals(listOf("Concluídos"),states.getValue("finished"))
    }

    @Test fun recordedProgressMovesPlanOutOfNextTabWithoutFakeSteps() {
        val records=listOf(next,contribution)
        assertTrue(planMatchesStatus(next,records,"Ativos"))
        assertFalse(planMatchesStatus(next,records,"Próximos"))
    }

    @Test fun importedReferenceIsNotAnActualPlan() {
        val reference=Record("ref","importedPlan",fields=mapOf("title" to "Ideia"))
        assertFalse(planMatchesStatus(reference,listOf(reference),"Ativos"))
        assertFalse(planMatchesStatus(reference,listOf(reference),"Próximos"))
    }
}
