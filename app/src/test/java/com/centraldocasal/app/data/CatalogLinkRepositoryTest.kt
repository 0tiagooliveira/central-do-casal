package com.centraldocasal.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.CentralApplication
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Record
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CentralApplication::class)
class CatalogLinkRepositoryTest {
    private lateinit var context: Context
    private val originals = listOf(
        Record("fixture-sku", "catalogSku", fields = mapOf("title" to "SKU fixture", "history" to listOf(mapOf("date" to "2026-01-01", "price" to "1.2345")))),
        Record("fixture-base", "catalogItem", fields = mapOf("title" to "Item-base fixture")),
        Record("fixture-other", "catalogItem", fields = mapOf("title" to "Outro item-base fixture"))
    )

    @Before fun seedLocalCatalogFixture() {
        context = ApplicationProvider.getApplicationContext()
        val fixture = JSONObject().put("records", JSONArray(originals.map { JSONObject(it.toMap() + ("id" to it.id)) }))
        context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).edit().clear().putString("data", fixture.toString()).commit()
    }

    private fun repository() = CentralRepository(context).also { it.useLocal("Perfil fixture") }

    @Test fun confirmationAndCorrectionPersistSeparatelyWithoutChangingCatalogOrHistory() = runBlocking {
        val repo = repository()
        val first = repo.saveCatalogLink("fixture-sku", "fixture-base", "confirmed", 0)
        assertEquals("fixture-sku", first.id)
        assertFalse(first.isPrivate)
        assertEquals(1L, first.number("revision"))
        assertEquals("local", first.text("updatedBy"))
        val corrected = repo.saveCatalogLink("fixture-sku", "fixture-other", "confirmed", first.number("revision"))
        assertEquals(2L, corrected.number("revision"))
        assertEquals(originals, repo.data.value.records.filter { it.kind != "catalogLink" })
        assertEquals(1, repo.data.value.records.count { it.kind == "catalogLink" })
        val exported = JSONObject(repo.export()).getJSONArray("records")
        assertEquals(4, exported.length())
        repo.close()
        val restarted = CentralRepository(context)
        val saved = restarted.data.value.records.single { it.kind == "catalogLink" }
        assertEquals("fixture-other", saved.text("baseItemId"))
        assertEquals(2L, saved.number("revision"))
        assertEquals(originals, restarted.data.value.records.filter { it.kind != "catalogLink" })
        restarted.close()
    }

    @Test fun keepingSeparateHasEmptyBaseAndDoesNotEraseOriginalReferences() = runBlocking {
        val repo = repository()
        repo.saveCatalogLink("fixture-sku", "fixture-base", "confirmed")
        val separate = repo.saveCatalogLink("fixture-sku", "", "separate", 1)
        assertEquals("separate", separate.text("status"))
        assertEquals("", separate.text("baseItemId"))
        assertEquals(2L, separate.number("revision"))
        assertEquals(originals, repo.data.value.records.filter { it.kind != "catalogLink" })
        repo.close()
    }

    @Test fun invalidReferencesOrStatusesNeverCreateLinks() = runBlocking {
        val repo = repository()
        for ((sku, base, status) in listOf(Triple("missing", "fixture-base", "confirmed"), Triple("fixture-base", "fixture-other", "confirmed"), Triple("fixture-sku", "missing", "confirmed"), Triple("fixture-sku", "fixture-sku", "confirmed"), Triple("fixture-sku", "", "confirmed"), Triple("fixture-sku", "fixture-base", "separate"), Triple("fixture-sku", "fixture-base", "suggested"))) {
            try { repo.saveCatalogLink(sku, base, status); fail("Vínculo inválido deveria ser recusado") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(originals, repo.data.value.records)
        repo.close()
    }

    @Test fun staleRevisionCannotReplaceAnotherDecision() = runBlocking {
        val repo = repository()
        repo.saveCatalogLink("fixture-sku", "fixture-base", "confirmed", 0)
        try { repo.saveCatalogLink("fixture-sku", "", "separate", 0); fail("A revisão antiga deveria ser recusada") } catch (_: IllegalArgumentException) { }
        assertEquals("fixture-base", repo.data.value.records.single { it.kind == "catalogLink" }.text("baseItemId"))
        repo.close()
    }

    @Test fun genericSaveRoutesThroughValidationAndDiscardsForgedAuthor() = runBlocking {
        val repo = repository()
        repo.save(Record("fixture-sku", "catalogLink", fields = mapOf("skuId" to "fixture-sku", "baseItemId" to "fixture-base", "status" to "confirmed", "updatedBy" to "forged")))
        assertEquals("local", repo.data.value.records.single { it.kind == "catalogLink" }.text("updatedBy"))
        assertEquals(originals, repo.data.value.records.filter { it.kind != "catalogLink" })
        try { repo.save(Record("fixture-sku", "catalogLink", true, mapOf("skuId" to "fixture-sku", "status" to "separate"))); fail("Vínculo privado deveria ser recusado") } catch (_: IllegalArgumentException) { }
        try { repo.save(Record("fixture-sku", "catalogLink", fields = mapOf("skuId" to "fixture-sku", "status" to "separate", "history" to emptyList<String>()))); fail("Campos externos deveriam ser recusados") } catch (_: IllegalArgumentException) { }
        repo.close()
    }

    @Test fun purchasedUnknownEstimateBecomesKnownAfterCloseWhileReceiptRetainsOriginalFlags() = runBlocking {
        val repo = repository()
        repo.editBasket("Mercado", null) { it.copy(entries = listOf(Entry(title = "Produto fixture", actual = 149, bought = true, estimateKnown = false, sourceSkuId = "fixture-sku", sourceBaseId = "fixture-base"))) }
        val old = repo.data.value.baskets.getValue("Mercado")
        repo.closeBasket("Mercado", old.generation, old.revision)
        val next = repo.data.value.baskets.getValue("Mercado").entries.single()
        assertEquals(149L, next.estimated)
        assertTrue(next.estimateKnown && next.unitReviewed && next.quantityReviewed)
        assertEquals(149L, next.expected())
        assertEquals("fixture-sku", next.sourceSkuId)
        val receiptEntries = repo.data.value.records.single { it.kind == "receipt" }.fields["entries"] as List<*>
        assertEquals(false, (receiptEntries.single() as Map<*, *>)["estimateKnown"])
        assertEquals(originals, repo.data.value.records.filter { it.kind !in setOf("catalogLink", "receipt") })
        repo.close()
    }
}
