package com.centraldocasal.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.CentralApplication
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
class PlanStepsRepositoryTest {
    private lateinit var context: Context
    private val plan = Record("goal", "plan", true, mapOf("title" to "Fixture", "target" to 10000L, "note" to "Próximo passo", "extraMetadata" to "preservar"))
    @Before fun clear() { context = ApplicationProvider.getApplicationContext(); context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun addsAndCompletesOnlyExplicitStepsWithoutFinancialEffects() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Fixture"); repo.save(plan)
        val added = repo.savePlanStep(plan, "step", title = "Pesquisar", expectedRevision = 0)
        val completed = repo.savePlanStep(added, "step", completed = true, expectedRevision = 1)
        assertEquals(2L, completed.number("stageRevision")); assertTrue(completed.isPrivate)
        assertEquals(true, ((completed.fields["steps"] as List<*>).single() as Map<*, *>)["done"])
        assertEquals("preservar", completed.text("extraMetadata")); assertEquals(1, repo.data.value.records.size); repo.close()
    }
    @Test fun staleRevisionCannotOverwriteACompletedStep() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Fixture"); repo.save(plan)
        val added = repo.savePlanStep(plan, "step", title = "Pesquisar", expectedRevision = 0)
        repo.savePlanStep(added, "step", completed = true, expectedRevision = 1)
        try { repo.savePlanStep(added, "step", completed = false, expectedRevision = 1); fail("Expected stale revision rejection") } catch (_: IllegalArgumentException) { }
        val current = repo.data.value.records.single(); assertEquals(2L, current.number("stageRevision")); assertEquals(true, ((current.fields["steps"] as List<*>).single() as Map<*, *>)["done"]); repo.close()
    }
    @Test fun editingStaleDetailsPreservesLatestStepsAndMetadata() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Fixture"); repo.save(plan)
        repo.savePlanStep(plan, "step", title = "Pesquisar", expectedRevision = 0)
        val edited = repo.savePlanDetails(plan.copy(fields = plan.fields + mapOf("title" to "Novo título", "steps" to emptyList<Any>(), "stageRevision" to 0L)))
        assertEquals("Novo título", edited.text("title")); assertEquals(1L, edited.number("stageRevision")); assertEquals(1, (edited.fields["steps"] as List<*>).size); assertEquals("preservar", edited.text("extraMetadata")); repo.close()
    }
    @Test fun importedReferencesCannotBeEditedOrTurnIntoChapters() = runBlocking {
        val repo = CentralRepository(context); repo.useLocal("Fixture")
        try { repo.savePlanStep(plan.copy(kind = "importedPlan"), "step", title = "Pesquisar", expectedRevision = 0); fail("Expected imported plan rejection") } catch (_: IllegalArgumentException) { }
        try { repo.savePlanDetails(plan.copy(kind = "importedPlan")); fail("Expected imported plan rejection") } catch (_: IllegalArgumentException) { }
        assertTrue(repo.data.value.records.isEmpty()); repo.close()
    }
}
