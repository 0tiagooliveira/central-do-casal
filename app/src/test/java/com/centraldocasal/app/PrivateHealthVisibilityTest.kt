package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test

class PrivateHealthVisibilityTest {
    @Test fun onlyExplicitlyPrivateHealthRecordsReachThePersonalHealthScreen() {
        val own=Record("private-health","health",true,mapOf("title" to "Registro pessoal"))
        val invalidShared=Record("shared-health","health",false,mapOf("title" to "Registro incorreto"))
        val other=Record("plan","plan",true,mapOf("title" to "Plano privado"))
        assertEquals(listOf(own),visibleHealthRecords(listOf(own,invalidShared,other)))
    }
}
