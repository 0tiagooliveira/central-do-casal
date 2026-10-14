package com.centraldocasal.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class LocalReviewApplication : CentralApplication() {
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear()
            .putString("activeUid", "local").putString("localName", "Tiago").commit()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LocalReviewApplication::class, qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun clearLocalFixture() {
        compose.runOnUiThread {
            compose.activity.application.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().commit()
            androidx.lifecycle.ViewModelProvider(compose.activity)[CentralViewModel::class.java].repo.useLocal("Tiago")
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val file = File("build/ui-review/020-$name.png"); file.parentFile?.mkdirs()
            file.outputStream().use { compose.activity.window.decorView.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun marketShowsConfirmedCartBalanceAndOpensRealPriceEditor() {
        val basket=Basket("Mercado",budget=10000,entries=listOf(Entry(id="fixture-bought",title="Arroz fixture",estimated=1000,actual=1200,bought=true),Entry(id="fixture-pending",title="Feijão fixture",estimated=700)))
        var edited: Entry?=null
        compose.runOnUiThread { compose.activity.setContent { CentralTheme { ShoppingSessionScreen(DataState(baskets=mapOf("Mercado" to basket)),SessionState(local=true),false,{edited=it},{},{},{},{},{},{},{},{},"Mercado",onQuickSave={edited=it}) } } }
        compose.onAllNodesWithText("R$ 12,00").onFirst().assertIsDisplayed()
        capture("mercado-orcamento-fixture")
        compose.onNodeWithText("No mercado").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Adicionar ao carrinho")); compose.onNodeWithText("Adicionar ao carrinho").performClick()
        compose.onNodeWithText("Preço real / un").performTextReplacement("8,50"); compose.onNodeWithText("Quantidade (un)").performTextReplacement("2"); capture("edicao-rapida-fixture"); compose.onNodeWithText("Adicionar",substring=false).assertIsEnabled().performClick(); compose.waitForIdle(); compose.runOnIdle { org.junit.Assert.assertEquals("A alteração do carrinho deve acionar onQuickSave.", "fixture-pending",edited?.id); org.junit.Assert.assertTrue(edited?.bought==true); org.junit.Assert.assertEquals(850L,edited?.actual); org.junit.Assert.assertEquals(2000L,edited?.actualMilli) }
        capture("modo-mercado-fixture")
    }
    @Test fun skuHistoryShowsIncreaseWithoutCombiningDifferentUnits() {
        val sku=Record("fixture-price","catalogSku",fields=mapOf("history" to listOf(mapOf("date" to "2026-01-01","unidade" to "UN","preco_unitario_reais" to "2.5000"),mapOf("date" to "2026-02-01","unidade" to "UN","preco_unitario_reais" to "3.0000"),mapOf("date" to "2026-03-01","unidade" to "KG","preco_unitario_reais" to "50"))))
        compose.runOnUiThread { compose.activity.setContent { CentralTheme { Column(Modifier.padding(20.dp)) { PriceHistory(sku) } } } }
        compose.onNodeWithText("Ficou mais caro").assertIsDisplayed()
        compose.onNodeWithText("20,00% desde 2026-01-01").assertIsDisplayed()
        capture("evolucao-preco-fixture")
    }
    @Test fun fiveTabsAndSettingsRemainUsableWithNewDesign() {
        compose.onNodeWithText("Olá, Tiago ♡").assertIsDisplayed(); capture("hoje")
        compose.onNodeWithTag("tab-Compras").performClick()
        capture("compras"); compose.onNodeWithText("Montar lista do catálogo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tab-Rotina").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nova tarefa"))
        compose.onNodeWithText("Nova tarefa").assertIsDisplayed(); capture("rotina")
        compose.onNodeWithTag("tab-Planos").performClick()
        compose.onNodeWithText("Criar um plano").assertIsDisplayed(); capture("planos")
        compose.onNodeWithTag("tab-Nós").performClick()
        compose.onNodeWithText("Guardar um registro").performScrollTo().assertIsDisplayed(); capture("nos")
        compose.onNodeWithContentDescription("Configurações e privacidade").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("No seu ritmo")); compose.onNodeWithText("No seu ritmo").assertIsDisplayed(); capture("configuracoes")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Guardar um registro").performScrollTo().assertIsDisplayed()
    }
    @Test fun rhayraTabsAndReminderCenterUseRealLocalState() {
        compose.runOnUiThread { androidx.lifecycle.ViewModelProvider(compose.activity)[CentralViewModel::class.java].repo.useLocal("Rhayra") }
        compose.onNodeWithText("Olá, Rhayra ♡").assertIsDisplayed()
        capture("rhayra-hoje")
        listOf("Compras","Rotina","Planos","Nós").forEach { tab ->
            compose.onNodeWithTag("tab-$tab").performClick()
            capture("rhayra-$tab")
        }
        compose.onNodeWithContentDescription("Abrir lembretes").performClick()
        compose.onNodeWithText("Pendências de hoje").assertIsDisplayed()
        compose.onNodeWithText("Tudo em dia").assertIsDisplayed()
        capture("rhayra-lembretes")
        compose.onNodeWithText("Abrir rotina").performScrollTo().performClick()
        compose.onNodeWithTag("tab-Rotina").assertIsSelected()
    }
    @Test fun reminderNavigationRequestOpensRoutineWithoutChangingRecords() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[CentralViewModel::class.java]
        val before = vm.repo.data.value
        compose.onNodeWithTag("tab-Compras").performClick()
        compose.runOnUiThread {
            // Verify routing independently from the OS delivery/lifecycle, which
            // still needs a device test with an actual notification.
            compose.activity.setContent { CentralTheme { CentralApp(vm, {}, {}, openReminder = true) } }
        }
        compose.onNodeWithTag("tab-Rotina").assertIsSelected()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nova tarefa"))
        compose.onNodeWithText("Nova tarefa").assertIsDisplayed()
        org.junit.Assert.assertEquals(before, vm.repo.data.value)
    }
    @Test fun auxiliaryScreensAndLocalEntryRemainUsable() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[CentralViewModel::class.java]
        listOf("Finanças" to "Novo lançamento", "Saúde" to "Novo registro pessoal").forEach { (destination, action) ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(destination))
            compose.onNodeWithText(destination).performClick()
            compose.onNodeWithText(action).assertIsDisplayed()
            capture("auxiliar-$destination")
            compose.onNodeWithText(action).performClick()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithContentDescription("Voltar").performClick()
        }
        // Set the logged-out fixture directly; this UI test does not initialize WorkManager.
        compose.runOnUiThread { vm.repo.logout() }
        compose.onNodeWithText("Entrar com e-mail").assertIsNotEnabled()
        capture("entrada-conta")
        compose.onNodeWithText("Como quer ser chamado?").performScrollTo().performTextInput("Tiago")
        compose.onNodeWithText("Usar espaço local").performScrollTo().performClick()
        compose.onNodeWithTag("tab-Hoje").assertIsSelected()
    }
    @Test fun invitationFormFitsLargeFontsAndStillScrollsToPrivacy() {
        // Explicit UI fixture only: no real account or household is created.
        val vm = CentralViewModel(ApplicationProvider.getApplicationContext())
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    CentralTheme("Tiago") {
                        Scaffold(topBar = { Text("Configurações", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                SettingsScreen(vm, SessionState(uid="ui-fixture", name="Tiago", email="perfil@example.test", household="ui-fixture", verified=true), DataState(members=listOf("ui-fixture")), {}, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("E-mail do parceiro")); compose.onNodeWithText("E-mail do parceiro").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Criar convite"))
        compose.onNodeWithText("Criar convite").assertIsDisplayed().assertIsNotEnabled()
        capture("configuracoes-convite-fonte-ampliada-fixture")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Exportar meus registros")); compose.onNodeWithText("Exportar meus registros").assertIsDisplayed()
        vm.repo.close()
    }
    @Test fun importedCatalogAndNotesHaveSeparateNavigationAndDoNotChangeManualBalance() = runBlocking {
        val records=listOf(Record("fixture-catalog", "catalogItem", fields=mapOf("title" to "Produto fixture", "market" to "Mercado", "category" to "Despensa", "reference" to 299L)), Record("fixture-note", "importedReceipt", fields=mapOf("title" to "Loja fixture", "market" to "Mercado", "date" to "2026-10-01", "total" to 1234L, "detailed" to false)))
        val context=ApplicationProvider.getApplicationContext<android.app.Application>()
        val json=org.json.JSONObject().put("records",org.json.JSONArray(records.map { org.json.JSONObject(it.toMap()+("id" to it.id)) }))
        context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).edit().putString("data",json.toString()).commit()
        val vm=CentralViewModel(context); vm.repo.useLocal("Perfil fixture")
        compose.runOnUiThread { compose.activity.setContent { CentralTheme { CentralApp(vm, {}, {}) } } }
        compose.onNodeWithTag("tab-Compras").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Catálogo")); compose.onNodeWithText("Catálogo").performClick()
        compose.onNodeWithText("Produto fixture").assertIsDisplayed().performClick()
        compose.onNodeWithText("Preparar item para lista").assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Histórico")); compose.onNodeWithText("Histórico").performClick()
        compose.onNodeWithText("Loja fixture").assertIsDisplayed()
        compose.onNodeWithText("Nota sem detalhamento de produtos").assertIsDisplayed()
        org.junit.Assert.assertFalse(vm.repo.data.value.records.any { it.kind=="transaction" })
        vm.repo.close()
    }
}
