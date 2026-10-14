package com.centraldocasal.app.domain

import java.math.BigDecimal

data class ProductPurchase(
    val id: String,
    val skuId: String,
    val store: String,
    val date: String,
    val unit: String,
    val quantityText: String,
    val unitPriceText: String,
    val netCents: Long?,
    val receiptId: String,
    val title: String
)

private fun catalogId(id: String): String = if (id.isBlank() || id.startsWith("import_2026_")) id else "import_2026_$id"
private fun latestCatalogLink(skuId: String, records: List<Record>): Record? = records
    .filter { it.kind == "catalogLink" && !it.isPrivate && catalogId(it.text("skuId").ifBlank { it.id }) == catalogId(skuId) }
    .maxWithOrNull(compareBy<Record> { it.number("revision") }.thenBy { it.text("updatedAt") })

/** A user's explicit decision overrides the imported suggestion, including choosing no base. */
fun effectiveCatalogBaseId(sku: Record, records: List<Record>): String? {
    if (sku.kind != "catalogSku" || sku.isPrivate) return null
    val link = latestCatalogLink(sku.id, records)
    if (link != null) return if (link.text("status") == "confirmed") catalogId(link.text("baseItemId")).takeIf { it.isNotBlank() } else null
    val original = sku.fields["original"] as? Map<*, *>
    val confirmed = original?.get("grau_confirmacao_vinculo") == "confirmado" || sku.text("grade") == "confirmado"
    if (sku.fields["review"] == true || !(sku.fields["review"] == false || confirmed)) return null
    return catalogId(sku.text("baseId")).takeIf { it.isNotBlank() }
}

fun relatedCatalogSkus(base: Record, records: List<Record>): List<Record> {
    if (base.kind != "catalogItem" || base.isPrivate) return emptyList()
    return records.filter { it.kind == "catalogSku" && !it.isPrivate }
        .distinctBy { it.id }.filter { effectiveCatalogBaseId(it, records) == catalogId(base.id) }
        .sortedWith(compareBy<Record> { it.text("store") }.thenBy { it.text("title") }.thenBy { it.id })
}

/** Imported decimal strings retain fiscal precision. App observations use actual bought values only. */
fun productPurchases(product: Record, records: List<Record>): List<ProductPurchase> {
    if (product.isPrivate || product.kind !in setOf("catalogItem", "catalogSku")) return emptyList()
    val related = if (product.kind == "catalogSku") listOf(product) else relatedCatalogSkus(product, records)
    val relatedIds = related.map { catalogId(it.id) }.toSet()
    val receipts = records.filter { !it.isPrivate && it.kind in setOf("importedReceipt", "receipt") }.distinctBy { it.id }
    val byReceipt = receipts.associateBy { it.id }
    val purchases = mutableListOf<ProductPurchase>()
    related.forEach { sku ->
        val rows = (sku.fields["history"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        rows.forEachIndexed { index, row ->
            val receiptId = catalogId(row["nota_id"] as? String ?: "")
            val receipt = byReceipt[receiptId]
            val rowId = row["id"] as? String
            val identity = rowId?.takeIf { it.isNotBlank() } ?: "${sku.id}:$receiptId:${row["posicao_na_nota"] ?: index}"
            val receiptStore = (receipt?.fields?.get("store") as? Map<*, *>)?.get("nome") as? String
            purchases += ProductPurchase("imported:$identity", sku.id, sku.text("store").ifBlank { receiptStore ?: "Loja não informada" },
                row["date"] as? String ?: receipt?.text("date").orEmpty(), row["unidade"] as? String ?: "",
                row["quantidade"] as? String ?: "", row["preco_unitario_reais"] as? String ?: "",
                (row["valor_liquido_estimado_rateado_centavos"] as? Number)?.toLong(), receiptId,
                (row["descricao_original"] as? String)?.takeIf { it.isNotBlank() } ?: sku.text("title"))
        }
    }
    receipts.filter { it.kind == "receipt" }.forEach { receipt ->
        val entries = (receipt.fields["entries"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        entries.forEachIndexed { index, entry ->
            if (entry["bought"] != true) return@forEachIndexed
            val actual = (entry["actual"] as? Number)?.toLong() ?: return@forEachIndexed
            val milli = (entry["actualMilli"] as? Number)?.toLong() ?: return@forEachIndexed
            if (actual < 0 || milli <= 0) return@forEachIndexed
            val sourceSku = catalogId(entry["sourceSkuId"] as? String ?: "")
            val sourceBase = catalogId(entry["sourceBaseId"] as? String ?: "")
            val belongs = if (product.kind == "catalogSku") sourceSku == catalogId(product.id) else {
                if (sourceSku.isNotBlank()) {
                    val current = records.firstOrNull { it.kind == "catalogSku" && catalogId(it.id) == sourceSku && !it.isPrivate }
                    val explicit = latestCatalogLink(sourceSku, records)
                    when {
                        current != null -> sourceSku in relatedIds
                        explicit != null -> explicit.text("status") == "confirmed" && catalogId(explicit.text("baseItemId")) == catalogId(product.id)
                        else -> sourceBase == catalogId(product.id)
                    }
                } else sourceBase == catalogId(product.id)
            }
            if (!belongs) return@forEachIndexed
            val entryId = (entry["id"] as? String)?.takeIf { it.isNotBlank() } ?: index.toString()
            val net = runCatching { Money.subtotal(actual, milli) }.getOrNull()
            purchases += ProductPurchase("app:${receipt.id}:$entryId", sourceSku,
                receipt.text("market").ifBlank { "Compra registrada no app" }, receipt.text("date"), entry["unit"] as? String ?: "",
                BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString(), BigDecimal.valueOf(actual, 2).toPlainString(), net,
                receipt.id, (entry["title"] as? String).orEmpty())
        }
    }
    return purchases.distinctBy { it.id }.sortedWith(compareByDescending<ProductPurchase> { it.date }.thenBy { it.skuId }.thenBy { it.unit }.thenBy { it.id })
}
