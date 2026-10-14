package com.centraldocasal.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.CentralApplication
import com.centraldocasal.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CentralApplication::class)
class LocalRepositoryTest {
    private lateinit var context: Context
    @Before fun clear() { context = ApplicationProvider.getApplicationContext(); context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun recordsSurviveRepositoryRestart() = runBlocking {
        val first = CentralRepository(context); first.useLocal("Perfil de teste")
        first.save(Record("private-task", "task", true, mapOf("title" to "Teste privado", "date" to "2026-10-10", "repeat" to "Diária")))
        first.close()
        val second = CentralRepository(context)
        assertTrue(second.session.value.local)
        assertEquals("Perfil de teste", second.session.value.name)
        assertEquals("Teste privado", second.data.value.records.single().text("title"))
        assertTrue(second.data.value.records.single().isPrivate)
        second.close()
    }
    @Test fun closingTwiceKeepsOneReceiptAndNeverCreatesFinancialExpense() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Perfil de teste")
        repo.editBasket("Mercado", null) { it.copy(budget = 10000, entries = listOf(Entry(title = "Fixture explícita", estimated = 100, actual = 120, bought = true))) }
        val before = repo.data.value.baskets.getValue("Mercado")
        repo.closeBasket("Mercado", before.generation, before.revision)
        repo.closeBasket("Mercado", before.generation, before.revision)
        assertEquals(1, repo.data.value.records.count { it.kind == "receipt" })
        assertEquals(120L, repo.data.value.records.single().number("total"))
        assertFalse(repo.data.value.records.any { it.kind == "transaction" })
        val after = repo.data.value.baskets.getValue("Mercado")
        assertNotEquals(before.generation, after.generation)
        assertEquals(120L, after.entries.single().estimated)
        assertFalse(after.entries.single().bought)
        repo.editBasket("Mercado", after.revision) { it.copy(budget = 20000) }
        assertEquals(10000L, repo.data.value.records.single().number("budget"))
        repo.close()
    }
    @Test fun careEventsPersistAndCanBeUndoneLocally() = runBlocking {
        val first = CentralRepository(context); first.useLocal("Perfil de teste")
        val event = Record("care-local", "careEvent", fields = mapOf(
            "careType" to "diaper",
            "subject" to "Lucca",
            "actorUid" to "local",
            "actorName" to "Perfil de teste",
            "occurredAt" to 123456789L,
            "date" to "2026-10-13"
        ))
        first.save(event)
        first.close()

        val second = CentralRepository(context)
        val stored = second.data.value.records.single { it.id == event.id }
        assertEquals("careEvent", stored.kind)
        assertEquals("diaper", stored.text("careType"))
        assertEquals("Lucca", stored.text("subject"))
        assertEquals("local", stored.text("actorUid"))
        assertEquals(123456789L, stored.number("occurredAt"))
        assertEquals("2026-10-13", stored.text("date"))
        second.remove(stored)
        assertFalse(second.data.value.records.any { it.id == event.id })
        second.close()
    }

    @Test fun planStepShoppingLinkIsIdempotentAndKeepsOrigin() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Perfil de teste")
        repo.linkPlanStepToBasket("plan-a", "step-a", "Comprar papel de parede", "Mercado")
        repo.linkPlanStepToBasket("plan-a", "step-a", "Comprar papel de parede", "Mercado")
        val entries = repo.data.value.baskets.getValue("Mercado").entries
        assertEquals(1, entries.size)
        val linked = entries.single()
        assertEquals("plan-a", linked.sourcePlanId)
        assertEquals("step-a", linked.sourceStepId)
        assertFalse(linked.estimateKnown)
        assertFalse(linked.unitReviewed)
        assertFalse(linked.quantityReviewed)
        repo.close()
    }

    @Test fun planStepRoutineLinkCreatesOnlyOneTaskAndKeepsOrigin() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Perfil de teste")
        val task = repo.createPlanStepTask("plan-a", "step-a", "Medir a janela", "2026-10-20", "Ambos", "Casa", "", true)
        assertEquals("plan-a", task.text("sourcePlanId"))
        assertEquals("step-a", task.text("sourceStepId"))
        assertTrue(task.isPrivate)
        try {
            repo.createPlanStepTask("plan-a", "step-a", "Medir a janela", "2026-10-20", "Ambos", "", true)
            fail("A mesma etapa não pode criar duas tarefas.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(1, repo.data.value.records.count { it.kind == "task" && it.text("sourcePlanId") == "plan-a" && it.text("sourceStepId") == "step-a" })
        }
        repo.close()
    }

    @Test fun staleEditDoesNotOverwriteNewerList() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Perfil de teste")
        repo.editBasket("Açougue", null) { it.copy(budget = 10000) }
        val revision = repo.data.value.baskets.getValue("Açougue").revision
        repo.editBasket("Açougue", revision) { it.copy(budget = 20000) }
        try { repo.editBasket("Açougue", revision) { it.copy(budget = 30000) }; fail("A edição desatualizada deveria ser rejeitada") } catch (expected: IllegalArgumentException) { assertEquals(20000L, repo.data.value.baskets.getValue("Açougue").budget) }
        repo.close()
    }
}
