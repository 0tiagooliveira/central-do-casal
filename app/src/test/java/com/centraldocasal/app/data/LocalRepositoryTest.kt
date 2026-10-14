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
    @Test fun staleEditDoesNotOverwriteNewerList() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Perfil de teste")
        repo.editBasket("Açougue", null) { it.copy(budget = 10000) }
        val revision = repo.data.value.baskets.getValue("Açougue").revision
        repo.editBasket("Açougue", revision) { it.copy(budget = 20000) }
        try { repo.editBasket("Açougue", revision) { it.copy(budget = 30000) }; fail("A edição desatualizada deveria ser rejeitada") } catch (expected: IllegalArgumentException) { assertEquals(20000L, repo.data.value.baskets.getValue("Açougue").budget) }
        repo.close()
    }
}
