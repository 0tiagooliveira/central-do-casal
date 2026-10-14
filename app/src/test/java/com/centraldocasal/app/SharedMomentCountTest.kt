package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedMomentCountTest {
    @Test fun homeCountsSavedSharedMomentsNotCompletedQuests() {
        val records = listOf(
            Record("sushi", "moment", false, mapOf("title" to "Noite do sushi")),
            Record("private-memory", "moment", true, mapOf("title" to "Registro pessoal")),
            Record("finished-quest", "quest", false, mapOf("completed" to listOf("one", "two"))),
            Record("to-do", "task", false, mapOf("title" to "Fazer compras"))
        )
        assertEquals(1, sharedMomentCount(records))
        assertEquals(0, sharedMomentCount(records.filterNot { it.kind == "moment" }))
    }
}
