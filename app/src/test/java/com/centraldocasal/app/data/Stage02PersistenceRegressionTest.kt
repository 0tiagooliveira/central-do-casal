package com.centraldocasal.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.CentralApplication
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Record
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CentralApplication::class)
class Stage02PersistenceRegressionTest {
    private lateinit var context: Context

    @Before fun clearLocalData() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun receiptReplayAfterRepositoryRestartDoesNotDuplicateHistory() = runBlocking {
        val first = CentralRepository(context)
        first.useLocal("Perfil de teste")
        first.editBasket("Mercado", null) {
            it.copy(budget = 15000, entries = listOf(
                Entry(id = "item-um", title = "Produto de teste", estimated = 300, actual = 350, bought = true)
            ))
        }
        val original = first.data.value.baskets.getValue("Mercado")
        first.closeBasket("Mercado", original.generation, original.revision)
        first.close()

        val second = CentralRepository(context)
        val nextGeneration = second.data.value.baskets.getValue("Mercado").generation
        second.closeBasket("Mercado", original.generation, original.revision)

        assertEquals(1, second.data.value.records.count {
            it.kind == "receipt" && it.id == original.generation
        })
        assertEquals(nextGeneration, second.data.value.baskets.getValue("Mercado").generation)
        assertFalse(second.data.value.records.any { it.kind == "transaction" })
        second.close()
    }

    @Test fun budgetsRemainIndependentAcrossMarketAndButcher() = runBlocking {
        val repo = CentralRepository(context)
        repo.useLocal("Perfil de teste")
        repo.editBasket("Mercado", null) { it.copy(budget = 30000) }
        repo.editBasket("Açougue", null) { it.copy(budget = 8000) }
        val butcherBefore = repo.data.value.baskets.getValue("Açougue")

        val marketRevision = repo.data.value.baskets.getValue("Mercado").revision
        repo.editBasket("Mercado", marketRevision) { it.copy(budget = 25000) }

        assertEquals(25000L, repo.data.value.baskets.getValue("Mercado").budget)
        assertEquals(butcherBefore, repo.data.value.baskets.getValue("Açougue"))
        repo.close()
    }

    @Test fun careSubjectsAndOriginalAuthorsSurviveRepositoryRestart() = runBlocking {
        val first = CentralRepository(context)
        first.useLocal("Perfil de teste")
        val care = listOf(
            Record("fixture-diaper", "careEvent", fields = mapOf(
                "careType" to "diaper", "subject" to "Lucca",
                "actorUid" to "actor-a", "actorName" to "A",
                "occurredAt" to 1760000000000L, "date" to "2026-10-09"
            )),
            Record("fixture-pet", "careEvent", fields = mapOf(
                "careType" to "petFood", "subject" to "Mel",
                "actorUid" to "actor-b", "actorName" to "B",
                "occurredAt" to 1760000001000L, "date" to "2026-10-09"
            ))
        )
        care.forEach { first.save(it) }
        first.close()

        val second = CentralRepository(context)
        val restored = second.data.value.records.filter { it.kind == "careEvent" }
            .associateBy { it.id }

        assertEquals(2, restored.size)
        assertEquals("Lucca", restored.getValue("fixture-diaper").text("subject"))
        assertEquals("actor-a", restored.getValue("fixture-diaper").text("actorUid"))
        assertEquals("Mel", restored.getValue("fixture-pet").text("subject"))
        assertEquals("actor-b", restored.getValue("fixture-pet").text("actorUid"))
        assertEquals(1760000001000L, restored.getValue("fixture-pet").number("occurredAt"))
        second.close()
    }
}
