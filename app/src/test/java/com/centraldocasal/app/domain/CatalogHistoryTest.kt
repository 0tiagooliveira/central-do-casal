package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test

class CatalogHistoryTest {
    private val base = Record("import_2026_item_a", "catalogItem", fields = mapOf("title" to "Fixture"))
    private val other = base.copy(id = "import_2026_item_b")
    private fun sku(id: String = "sku_a", review: Boolean = true) = Record("import_2026_$id", "catalogSku", fields = mapOf("baseId" to "item_a", "review" to review, "title" to "Produto fixture", "store" to "Loja fixture", "history" to listOf(mapOf("id" to "line1", "nota_id" to "note1", "date" to "2026-01-01", "unidade" to "kg", "quantidade" to "0.1234", "preco_unitario_reais" to "1.2345", "valor_liquido_estimado_rateado_centavos" to 15L))))
    private fun link(sku: Record, baseId: String, revision: Long = 1, status: String = "confirmed") = Record(sku.id, "catalogLink", fields = mapOf("skuId" to sku.id, "baseItemId" to baseId, "status" to status, "revision" to revision))
    @Test fun manualConfirmationShowsTheActualSkuAndReassignmentMovesItsHistory() {
        val s = sku(); val records = listOf(base, other, s, link(s, base.id), link(s, other.id, 2))
        assertTrue(relatedCatalogSkus(base, records).isEmpty()); assertEquals(listOf(s), relatedCatalogSkus(other, records)); assertEquals(1, productPurchases(other, records).size)
    }
    @Test fun separateOverridesPreviouslyConfirmedOrTrustedOriginal() {
        val s = sku(review = false); val records = listOf(base, s, link(s, base.id), link(s, "", 2, "separate"))
        assertTrue(relatedCatalogSkus(base, records).isEmpty()); assertTrue(productPurchases(base, records).isEmpty()); assertEquals(1, productPurchases(s, records).size)
    }
    @Test fun rawOriginalIdsAreNormalizedButUnreviewedSuggestionsAreNotTrusted() {
        val trusted = sku("trusted", false); val uncertain = sku("uncertain", true)
        assertEquals(listOf(trusted), relatedCatalogSkus(base, listOf(base, trusted, uncertain))); assertEquals(base.id, effectiveCatalogBaseId(trusted, emptyList()))
    }
    @Test fun confirmedOriginalWithMissingReviewFlagIsAnAllowedFallback() {
        val s = sku().copy(fields = sku().fields - "review" + mapOf("original" to mapOf("grau_confirmacao_vinculo" to "confirmado")))
        assertEquals(listOf(s), relatedCatalogSkus(base, listOf(base, s)))
    }
    @Test fun duplicateLineIdsAreNotCountedTwiceAndFiscalDecimalsArePreserved() {
        val s = sku(review = false); val line = (s.fields["history"] as List<*>).single()
        val duplicate = s.copy(fields = s.fields + mapOf("history" to listOf(line, line)))
        val rows = productPurchases(base, listOf(base, duplicate, duplicate)); assertEquals(1, rows.size); assertEquals("1.2345", rows.single().unitPriceText); assertEquals("0.1234", rows.single().quantityText); assertEquals("import_2026_note1", rows.single().receiptId)
    }
    @Test fun appReceiptsUseActualValuesAndSeparateEntryIdentities() {
        val entry = Entry("entry1", "Fixture", unit = "kg", actualMilli = 1250, estimated = 9999, actual = 200, bought = true, sourceBaseId = base.id)
        val receipt = Record("closed-list", "receipt", fields = mapOf("market" to "Mercado", "date" to "2026-02-01", "entries" to listOf(entry.toMap(), entry.copy(id = "entry2", unit = "un", bought = false).toMap())))
        val rows = productPurchases(base, listOf(base, receipt, receipt)); assertEquals(1, rows.size); assertEquals("2.00", rows.single().unitPriceText); assertEquals("1.25", rows.single().quantityText); assertEquals(250L, rows.single().netCents); assertEquals("closed-list", rows.single().receiptId)
    }
    @Test fun historyKeepsSkuStoreAndUnitDistinctWithoutCombiningPrices() {
        val a = sku("a", false); val b = sku("b", false).copy(fields = sku("b", false).fields + mapOf("store" to "Outra loja", "history" to listOf(mapOf("id" to "line2", "nota_id" to "note2", "unidade" to "un", "preco_unitario_reais" to "9.99"))))
        val rows = productPurchases(base, listOf(base, a, b)); assertEquals(2, rows.size); assertEquals(2, rows.map { it.skuId }.toSet().size); assertEquals(setOf("kg", "un"), rows.map { it.unit }.toSet()); assertEquals(2, rows.map { it.store }.toSet().size)
    }
    @Test fun appReceiptSkuDoesNotLeakBackToAnExplicitlySeparatedBase() {
        val s = sku(review = false); val entry = Entry("entry", "Fixture", actual = 100, bought = true, sourceBaseId = base.id, sourceSkuId = s.id)
        val receipt = Record("receipt", "receipt", fields = mapOf("entries" to listOf(entry.toMap())))
        assertTrue(productPurchases(base, listOf(base, s, receipt, link(s, "", status = "separate"))).isEmpty()); assertEquals(2, productPurchases(s, listOf(s, receipt)).size)
    }
    @Test fun currentSkuReassignmentMovesAppObservationWithoutDuplicatingTheOldBase() {
        val s = sku(review = false); val entry = Entry("entry", "Fixture", actual = 100, bought = true, sourceBaseId = base.id, sourceSkuId = s.id)
        val receipt = Record("receipt", "receipt", fields = mapOf("entries" to listOf(entry.toMap())))
        val records = listOf(base, other, s, receipt, link(s, other.id))
        assertTrue(productPurchases(base, records).isEmpty()); assertEquals(2, productPurchases(other, records).size)
    }
}
