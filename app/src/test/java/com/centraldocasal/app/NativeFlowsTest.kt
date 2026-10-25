package com.centraldocasal.app

import android.content.Context
import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.data.CentralRepository
import com.centraldocasal.app.domain.Record
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LocalReviewApplication::class, qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CentralViewModel

    @Before fun prepareLocalFixture() {
        compose.runOnUiThread {
            val app = ApplicationProvider.getApplicationContext<Application>()
            app.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().commit()
            vm = CentralViewModel(app)
            vm.repo.useLocal("Perfil fixture")
        }
    }

    @After fun closeOwnedRepository() { if (::vm.isInitialized) vm.repo.close() }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val file = File("build/ui-review/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.activity.window.decorView.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun planDetailSavesChapterCompletionAndGoalEditPreservesStoredChapters() = runBlocking {
        val plan = Record("fixture-plan-native", "plan", fields = mapOf("title" to "Nossa viagem fixture", "target" to 200000L, "date" to "2027-04-15", "horizon" to "Médio prazo", "note" to "Escolher juntos o destino"))
        val contribution = Record("fixture-contribution-native", "contribution", fields = mapOf("planId" to plan.id, "amount" to 45000L, "date" to "2026-10-01", "note" to "Primeiro avanço fixture"))
        vm.repo.save(plan)
        vm.repo.save(contribution)
        compose.runOnUiThread {
            compose.activity.setContent {
                val data by vm.repo.data.collectAsStateWithLifecycle()
                var editing by remember { mutableStateOf<Record?>(null) }
                CompositionLocalProvider(LocalExperience provides ExperienceOptions(haptics = false, motion = false)) {
                    CentralTheme("Perfil fixture") {
                        Scaffold { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                if (editing != null) GoalEditor(vm, editing) { editing = null }
                                else PlansExperienceScreen(vm, data, {}, { editing = it }, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Nossa viagem fixture").assertIsDisplayed().performClick()
        compose.onNodeWithText("Detalhes do plano").assertIsDisplayed()
        scrollTo(hasText("Uma próxima etapa"))
        compose.onNodeWithText("Uma próxima etapa").performTextInput("Escolher destino fixture")
        scrollTo(hasText("Adicionar etapa"))
        compose.onNodeWithText("Adicionar etapa").performClick()
        compose.waitUntil(5000) {
            (vm.repo.data.value.records.single { it.id == plan.id }.fields["steps"] as? List<*>)?.size == 1 && !vm.busy.value
        }
        scrollTo(hasText("Escolher destino fixture"))
        compose.onNodeWithText("Escolher destino fixture").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.waitUntil(5000) {
            val stages = vm.repo.data.value.records.single { it.id == plan.id }.fields["steps"] as? List<*>
            (stages?.singleOrNull() as? Map<*, *>)?.get("done") == true && !vm.busy.value
        }
        val storedBeforeEdit = vm.repo.data.value.records.single { it.id == plan.id }
        val recordsBeforeEdit = vm.repo.data.value.records.toList()
        assertEquals(2L, storedBeforeEdit.number("stageRevision"))

        // Dispatch the real Android back action through the screen's BackHandler.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Detalhes do plano").assertDoesNotExist()
        compose.onNodeWithText("Concluídos").performClick()
        scrollTo(hasText("Nossa viagem fixture"))
        compose.onNodeWithText("Nossa viagem fixture").assertIsDisplayed()
        scrollTo(hasText("Nossa viagem fixture"))
        compose.onNodeWithText("Nossa viagem fixture").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Editar plano").assertIsDisplayed()
        compose.onNodeWithText("Nome da meta").performTextReplacement("Nossa próxima viagem fixture")
        scrollTo(hasText("Continuar"))
        compose.onNodeWithText("Continuar").performClick()
        scrollTo(hasText("Revisar plano"))
        compose.onNodeWithText("Revisar plano").performClick()
        scrollTo(hasText("Salvar meta"))
        compose.onNodeWithText("Salvar meta").performClick()
        compose.waitUntil(5000) { vm.repo.data.value.records.single { it.id == plan.id }.text("title") == "Nossa próxima viagem fixture" && !vm.busy.value }
        val afterEdit = vm.repo.data.value.records.single { it.id == plan.id }
        assertEquals(storedBeforeEdit.fields["steps"], afterEdit.fields["steps"])
        assertEquals(2L, afterEdit.number("stageRevision"))
        assertEquals(contribution, vm.repo.data.value.records.single { it.id == contribution.id })
        assertEquals(recordsBeforeEdit.filter { it.id != plan.id }, vm.repo.data.value.records.filter { it.id != plan.id })

        val persistedRecords = org.json.JSONObject(vm.repo.export()).getJSONArray("records").toString()
        val restarted = CentralRepository(ApplicationProvider.getApplicationContext<Application>())
        try {
            val persisted = restarted.data.value.records.single { it.id == plan.id }
            assertEquals("Nossa próxima viagem fixture", persisted.text("title"))
            assertEquals(afterEdit.fields["steps"], persisted.fields["steps"])
            assertEquals(2L, persisted.number("stageRevision"))
            assertEquals(persistedRecords, org.json.JSONObject(restarted.export()).getJSONArray("records").toString())
        } finally { restarted.close() }
        // The fixture remounts the list after the editor; show completed plans again.
        compose.onNodeWithText("Concluídos").performClick()
        scrollTo(hasText("Nossa próxima viagem fixture"))
        compose.onNodeWithText("Nossa próxima viagem fixture").performClick()
        compose.onNodeWithText("Valor registrado por vocês").assertIsDisplayed()
        capture("020-plano-preenchido-fixture")
    }

    @Test fun coupleConstellationExcludesPrivateAndIncompleteRecordsAndOpensOriginalMoment() = runBlocking {
        val publicMoment = Record("fixture-public-native", "moment", fields = mapOf("title" to "Nosso passeio fixture", "date" to "2026-10-01", "note" to "Uma lembrança original guardada por nós."))
        val privateMoment = Record("fixture-private-native", "moment", true, mapOf("title" to "Registro reservado fixture", "date" to "2026-10-02", "note" to "Nota privada fixture"))
        val incomplete = Record("fixture-incomplete-native", "quest", fields = mapOf("title" to "Aventura ainda em aberto fixture", "date" to "2026-10-03", "completed" to listOf("member-a-fixture"), "cancelled" to false))
        val confirmed = Record("fixture-confirmed-native", "quest", fields = mapOf("title" to "Aventura confirmada fixture", "date" to "2026-09-20", "completed" to listOf("member-a-fixture", "member-b-fixture"), "cancelled" to false))
        for (record in listOf(publicMoment, privateMoment, incomplete, confirmed)) vm.repo.save(record)
        val storedBeforeNavigation = vm.repo.export(); val recordsBeforeNavigation = vm.repo.data.value.records.toList()
        val albumOpens = AtomicInteger(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                val data by vm.repo.data.collectAsStateWithLifecycle()
                val session by vm.repo.session.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalExperience provides ExperienceOptions(haptics = false, motion = false)) {
                    CentralTheme("Perfil fixture") {
                        Scaffold { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                CoupleSummary(data, session, { albumOpens.incrementAndGet() }, {}, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("constellation-star-${privateMoment.id}").assertDoesNotExist()
        compose.onNodeWithTag("constellation-star-${incomplete.id}").assertDoesNotExist()
        compose.onNodeWithTag("constellation-star-${confirmed.id}").assertExists()
        compose.onNodeWithText("Registro reservado fixture").assertDoesNotExist()
        compose.onNodeWithTag("constellation-star-${publicMoment.id}").performScrollTo().performClick()
        compose.onNodeWithText(publicMoment.text("note")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abrir álbum").performScrollTo().performClick()
        assertEquals(1, albumOpens.get())
        // Selection/navigation reads the original records without saving inferred experiences.
        assertEquals(recordsBeforeNavigation, vm.repo.data.value.records)
        assertEquals(org.json.JSONObject(storedBeforeNavigation).getJSONArray("records").toString(), org.json.JSONObject(vm.repo.export()).getJSONArray("records").toString())
        capture("020-nos-preenchido-fixture")
    }
}
