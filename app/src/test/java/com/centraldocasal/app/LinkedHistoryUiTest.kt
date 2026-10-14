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
import org.json.JSONArray
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LocalReviewApplication::class, qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkedHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CentralViewModel
    private val base = Record("fixture-base-linked", "catalogItem", fields = mapOf("title" to "Iogurte fixture", "category" to "Laticínios", "quantityText" to "47,125", "previousState" to "Comprado"))
    private val history = listOf(
        mapOf("id" to "fixture-line-linked-1", "nota_id" to "fixture-note-linked-1", "date" to "2026-01-10", "unidade" to "un", "quantidade" to "2", "preco_unitario_reais" to "3.2500", "descricao_original" to "Iogurte natural linha fixture janeiro", "valor_liquido_estimado_rateado_centavos" to 650L),
        mapOf("id" to "fixture-line-linked-2", "nota_id" to "fixture-note-linked-2", "date" to "2026-02-10", "unidade" to "un", "quantidade" to "3", "preco_unitario_reais" to "3.1000", "descricao_original" to "Iogurte natural linha fixture fevereiro", "valor_liquido_estimado_rateado_centavos" to 930L)
    )
    private val sku = Record("fixture-sku-linked", "catalogSku", fields = mapOf("title" to "Iogurte natural fixture", "category" to "Laticínios", "store" to "Loja fixture", "unit" to "un", "review" to true, "referenceText" to "3.1000", "lastDate" to "2026-02-10", "history" to history))
    private val suggestion = Record("fixture-match-linked", "matchSuggestion", fields = mapOf("title" to sku.text("title"), "skuId" to sku.id, "confidence" to "alta", "note" to "Sugestão fictícia: confirmar com decisão humana.", "candidates" to listOf(mapOf("baseItemId" to base.id, "title" to base.text("title"), "reason" to "Candidato fixture, sem associação automática."))))
    private val notes = history.mapIndexed { index, row ->
        Record("import_2026_fixture-note-linked-${index + 1}", "importedReceipt", fields = mapOf("title" to if (index == 0) "Nota fixture janeiro" else "Nota fixture fevereiro", "date" to row["date"], "total" to row["valor_liquido_estimado_rateado_centavos"], "detailed" to true, "lines" to listOf(row), "discount" to 0L, "reconciled" to false))
    }

    @Before fun seedDocumentedCatalogFixture() {
        compose.runOnUiThread {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val root = JSONObject().put("records", JSONArray((listOf(base, sku, suggestion) + notes).map { JSONObject(it.toMap() + ("id" to it.id)) }))
            app.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().putString("data", root.toString()).commit()
            vm = CentralViewModel(app)
            vm.repo.useLocal("Perfil fixture")
        }
    }

    @After fun closeOwnedRepository() { if (::vm.isInitialized) vm.repo.close() }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val file = File("build/ui-review/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.activity.window.decorView.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun humanConfirmationPersistsLinkAndOpensBothOriginalPurchasesFromBaseDetail() {
        val originalRecords = vm.repo.data.value.records.toList()
        val originalHistory = vm.repo.data.value.records.single { it.kind == "catalogSku" && it.id == sku.id }.fields["history"]
        val noteIds = notes.map { it.id }.toSet()
        val originalNotes = vm.repo.data.value.records.filter { it.kind == "importedReceipt" && it.id in noteIds }
        compose.runOnUiThread {
            compose.activity.setContent {
                val data by vm.repo.data.collectAsStateWithLifecycle()
                val busy by vm.busy.collectAsStateWithLifecycle()
                var product by remember { mutableStateOf<Record?>(null) }
                var receipt by remember { mutableStateOf<Record?>(null) }
                CompositionLocalProvider(LocalExperience provides ExperienceOptions(haptics = false, motion = false)) {
                    CentralTheme("Perfil fixture") {
                        Scaffold { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                when {
                                    receipt != null -> PurchaseDocumentScreen(receipt!!) { receipt = null }
                                    product != null -> ProductDetailScreen(product!!, data, { product = null }, {}, { receipt = it })
                                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                        MatchReviewCard(vm, data, suggestion, busy, onLinked = { product = it }, onOpen = { product = it })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertFalse(vm.repo.data.value.records.any { it.kind == "catalogLink" && it.id == sku.id })
        compose.onNodeWithText("Vincular a Iogurte fixture").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar organização").assertIsDisplayed()
        assertFalse(vm.repo.data.value.records.any { it.kind == "catalogLink" && it.id == sku.id })
        compose.onNodeWithText("Confirmar", substring = false).performClick()
        compose.waitUntil(5000) { vm.repo.data.value.records.any { it.kind == "catalogLink" && it.id == sku.id } && !vm.busy.value }
        val link = vm.repo.data.value.records.single { it.kind == "catalogLink" && it.id == sku.id }
        assertEquals("confirmed", link.text("status"))
        assertEquals(base.id, link.text("baseItemId"))
        assertEquals(1L, link.number("revision"))
        assertEquals(originalRecords, vm.repo.data.value.records.filterNot { it.kind == "catalogLink" && it.id == sku.id })
        compose.onNodeWithText("Iogurte fixture").assertIsDisplayed()
        compose.onNodeWithTag("product-purchase-count").assertTextEquals("2")
        compose.onNodeWithText("Iogurte natural fixture").assertIsDisplayed()
        compose.onNodeWithText("Ficou mais barato").assertExists()
        compose.onNodeWithText("47,125", substring = true).assertDoesNotExist()
        capture("021-produto-vinculado-fixture")
        scrollTo(hasTestTag("product-purchase-imported:fixture-line-linked-1"))
        compose.onNodeWithTag("product-purchase-imported:fixture-line-linked-1").assertIsDisplayed()
        compose.onNodeWithText("R$ 3,2500 / un", substring = true).assertExists()
        scrollTo(hasTestTag("product-purchase-imported:fixture-line-linked-2"))
        compose.onNodeWithTag("product-purchase-imported:fixture-line-linked-2").assertIsDisplayed()
        compose.onNodeWithText("R$ 3,1000 / un", substring = true).assertExists()
        scrollTo(hasTestTag("product-receipt-imported:fixture-line-linked-2"))
        compose.onNodeWithTag("product-receipt-imported:fixture-line-linked-2").performClick()
        compose.onNodeWithText("Nota fixture fevereiro").assertIsDisplayed()
        compose.onNodeWithText("Iogurte natural linha fixture fevereiro").assertIsDisplayed()
        capture("021-nota-original-vinculada-fixture")
        compose.onNodeWithText("Voltar ao produto").performClick()
        compose.onNodeWithTag("product-purchase-count").assertTextEquals("2")

        // Restart reads the stored human decision while original SKU/history and note lines survive.
        val restarted = CentralRepository(ApplicationProvider.getApplicationContext<Application>())
        try {
            val restoredLink = restarted.data.value.records.single { it.kind == "catalogLink" && it.id == sku.id }
            assertEquals(base.id, restoredLink.text("baseItemId"))
            assertEquals(1L, restoredLink.number("revision"))
            assertEquals(originalHistory, restarted.data.value.records.single { it.kind == "catalogSku" && it.id == sku.id }.fields["history"])
            assertEquals(originalNotes, restarted.data.value.records.filter { it.kind == "importedReceipt" && it.id in noteIds })
            assertEquals(originalRecords, restarted.data.value.records.filterNot { it.kind == "catalogLink" && it.id == sku.id })
            assertEquals(notes.size, restarted.data.value.records.count { it.kind == "importedReceipt" && it.id in noteIds })
            assertTrue(restarted.data.value.baskets.isEmpty())
        } finally { restarted.close() }
    }

    @Test fun unlinkedBaseDoesNotPresentHistoricalQuantityAsCurrentStockOrInventPurchases() {
        val recordsBefore = vm.repo.data.value.records.toList()
        compose.runOnUiThread {
            compose.activity.setContent {
                val data by vm.repo.data.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalExperience provides ExperienceOptions(haptics = false, motion = false)) {
                    CentralTheme("Perfil fixture") {
                        Scaffold { padding -> Box(Modifier.fillMaxSize().padding(padding)) { ProductDetailScreen(base, data, {}, {}, {}) } }
                    }
                }
            }
        }
        compose.onNodeWithTag("product-purchase-count").assertTextEquals("0")
        compose.onNodeWithText("Ainda não há compras associadas.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("47,125", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Quantidade anterior:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("product-purchase-imported:fixture-line-linked-1").assertDoesNotExist()
        compose.onNodeWithTag("product-purchase-imported:fixture-line-linked-2").assertDoesNotExist()
        assertFalse(vm.repo.data.value.records.any { it.kind == "catalogLink" && it.id == sku.id })
        assertFalse(vm.repo.data.value.records.filterNot { it in recordsBefore }.any { it.kind == "receipt" || it.kind == "transaction" })
        assertTrue(vm.repo.data.value.baskets.isEmpty())
        assertEquals("47,125", vm.repo.data.value.records.single { it.kind == "catalogItem" && it.id == base.id }.text("quantityText"))
        assertEquals(recordsBefore, vm.repo.data.value.records)
    }
}
