package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test

class CoupleQuestStateTest {
    private val members = listOf("uid-tiago", "uid-rhayra")
    private fun quest(accepted: List<String>, completed: List<String>) = Record(
        "q", "quest", fields = mapOf("title" to "Um passeio",
            "accepted" to accepted, "completed" to completed)
    )

    @Test fun requiresTwoDistinctActualMembersToConfirm() {
        val duplicate = coupleQuestState(
            quest(listOf("uid-tiago", "uid-tiago"), listOf("uid-tiago", "uid-tiago")), members)
        assertFalse(duplicate.confirmedByBoth)
        assertFalse(duplicate.readyToComplete)
        assertEquals(0.5f, duplicate.progress, 0.0001f)

        val valid = coupleQuestState(
            quest(members, listOf("uid-rhayra", "uid-tiago")), members)
        assertTrue(valid.confirmedByBoth)
        assertEquals(1.0f, valid.progress, 0.0001f)
    }

    @Test fun unknownIdDoesNotReplaceOnePartner() {
        val state = coupleQuestState(
            quest(listOf("uid-tiago", "stranger"), listOf("uid-tiago", "stranger")), members)
        assertFalse(state.readyToComplete)
        assertFalse(state.confirmedByBoth)
        assertEquals(setOf("uid-tiago"), state.completed)
    }

    @Test fun noVerifiedHouseholdMeansNoCompletion() {
        val state = coupleQuestState(quest(members, members), listOf("uid-tiago"))
        assertFalse(state.confirmedByBoth)
        assertEquals(0f, state.progress, 0.0001f)
    }

    @Test fun completionCannotCountBeforeAcceptance() {
        val state = coupleQuestState(
            quest(listOf("uid-tiago"), members), members)
        assertEquals(setOf("uid-tiago"), state.completed)
        assertFalse(state.confirmedByBoth)
    }
}
