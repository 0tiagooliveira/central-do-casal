package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test

class FiscalVisionTest {
    private val receipt="""
        MERCADO BOM PRECO LTDA
        CNPJ: 12.345.678/0001-90
        NFC-e
        Emissao: 18/10/2026
        001 7891234567890 ARROZ BRANCO 1 UN X 22,90 22,90
        002 LEITE INTEGRAL 2 UN X 5,49 10,98
        003 BANANA 0,500 KG X 8,00 4,00
        VALOR TOTAL R$ 37,88
    """.trimIndent()

    @Test fun readsMerchantTaxDateAndOnlyWellStructuredItems() {
        val parsed=FiscalVision.parse(receipt)
        assertEquals("MERCADO BOM PRECO LTDA",parsed.merchantName)
        assertEquals("12345678000190",parsed.cnpj)
        assertEquals("2026-10-18",parsed.date)
        assertEquals(3788L,parsed.declaredTotalCents)
        assertEquals(3,parsed.lines.size)
        assertEquals("ARROZ BRANCO",parsed.lines[0].title)
        assertEquals(2290L,parsed.lines[0].unitPriceCents)
        assertEquals(549L,parsed.lines[1].unitPriceCents)
        assertEquals("kg",parsed.lines[2].unit)
        assertEquals(500L,parsed.lines[2].quantityMilli)
        assertEquals(800L,parsed.lines[2].unitPriceCents)
    }

    @Test fun sellerNamesAreGroupedBySameCnpjEvenWhenSpelledDifferently() {
        val a=FiscalVision.merchantId("BOM PREÇO","12.345.678/0001-90")
        val b=FiscalVision.merchantId("Bom Preco S.A.","12345678000190")
        assertEquals(a,b)
        assertEquals(FiscalVision.merchantId("Mercado A",""),
            FiscalVision.merchantId("MERCADO Á",""))
    }

    @Test fun cannotAssumeProductIdentityFromSimilarButDifferentPackSizes() {
        val catalog=listOf(
            Record("rice500","photoProduct",true,mapOf("title" to "Arroz branco 500g","unit" to "un")),
            Record("rice1","photoProduct",true,mapOf("title" to "Arroz branco 1kg","unit" to "un")),
            Record("riceOk","photoProduct",true,mapOf("title" to "Arroz branco","unit" to "un")))
        val item=FiscalLine("Arroz branco 1kg",1000,"un",1200,1200)
        val hits=FiscalVision.match(item,catalog)
        assertFalse(hits.any { it.productId == "rice500" })
        assertEquals("rice1",hits.first().productId)
    }

    @Test fun sameProductAcrossStoresComparesActualPerUnitPrice() {
        val product=Record("milk","photoProduct",true,mapOf("title" to "LEITE INTEGRAL","unit" to "un"))
        val past=Record("prior","photoReceipt",true,mapOf(
            "merchantId" to "merchant-test",
            "merchantName" to "Loja A",
            "date" to "2026-10-05",
            "entries" to listOf(mapOf("productId" to "milk","unit" to "un",
                "unitPriceCents" to 599L,"quantityMilli" to 1000L,"totalCents" to 599L))
        ))
        val now=FiscalLine("Leite integral",1000,"un",549,549,"milk")
        val comparison=FiscalVision.compare(now,"milk",listOf(product,past))
        assertNotNull(comparison)
        assertEquals("Loja A",comparison!!.previous.store)
        assertEquals(-50L,comparison.differenceCents)
    }

    @Test fun comparesByUnitWithoutInventingConversionsBetweenPacketsAndKg() {
        val past=Record("prior","photoReceipt",true,mapOf(
            "merchantName" to "Loja A","date" to "2026-10-05",
            "entries" to listOf(mapOf("productId" to "item","unit" to "kg",
                "unitPriceCents" to 2000L))
        ))
        val today=FiscalLine("Arroz",1000,"pacote",1500,1500,"item")
        assertNull(FiscalVision.compare(today,"item",listOf(past)))
    }

    @Test fun confirmedRecordsCreateMerchantAndNewItemOnlyOnce() {
        val merchantId=FiscalVision.merchantId("MERCADO BOM PRECO LTDA","12345678000190")
        val productId=FiscalVision.productId("ARROZ BRANCO","un")
        val current=listOf(
            Record(merchantId,"photoMerchant",true,mapOf("title" to "MERCADO BOM PRECO LTDA")),
            Record(productId,"photoProduct",true,mapOf("title" to "ARROZ BRANCO","unit" to "un")))
        val draft=FiscalDraft("MERCADO BOM PRECO LTDA","12345678000190","2026-10-18",2290,
            listOf(FiscalLine("ARROZ BRANCO",1000,"un",2290,2290)))
        val records=FiscalVision.recordsForReview(draft,current)
        assertEquals(1,records.size)
        assertEquals("photoReceipt",records.single().kind)
        val entries=records.single().fields["entries"] as List<*>
        assertEquals(productId,(entries.first() as Map<*,*>)["productId"])
    }

    @Test fun rejectsWrongDataInsteadOfGuessingAndDoesNotStoreRawText() {
        assertTrue(FiscalVision.parse("CUPOM\nTOTAL R$ 30,00").lines.isEmpty())
        assertNull(FiscalVision.cents("valor indefinido"))
        assertThrows(IllegalArgumentException::class.java) {
            FiscalVision.recordsForReview(FiscalDraft("","", "2026-10-18",
                0,listOf(FiscalLine("ARROZ",1000,"un",100,100))),emptyList())
        }
        val records=FiscalVision.recordsForReview(FiscalDraft("Loja Nova","","2026-10-18",100,
            listOf(FiscalLine("ARROZ",1000,"un",100,100))),emptyList())
        assertTrue(records.all { it.isPrivate })
        assertFalse(records.last().fields.containsKey("ocrText"))
        assertFalse(records.last().fields.containsKey("photo"))
    }
}
