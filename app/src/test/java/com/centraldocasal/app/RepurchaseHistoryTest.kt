package com.centraldocasal.app

import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test

class RepurchaseHistoryTest {
    private fun receipt(id: String, market: String, date: String, items: List<Entry>) =
        Record(id, "receipt", fields = mapOf("market" to market, "date" to date, "entries" to items.map { it.toMap() }))

    @Test fun purchasedItemsAppearButNeverUncheckedOrFromAnotherMarket() {
        val bought=Entry("item1","Arroz", "kg", plannedMilli=1000,actualMilli=1000,
            actual=800,bought=true,estimateKnown=true)
        val pending=Entry("item2","Sabão", "un",bought=false)
        val records=listOf(
            receipt("r1","Mercado","2026-10-20",listOf(bought,pending)),
            receipt("r2","Açougue","2026-10-21",listOf(Entry("meat","Carne","kg",actual=3500,bought=true)))
        )
        val suggestions=repurchaseSuggestions(records,"Mercado")
        assertEquals(1,suggestions.size)
        val suggestion=suggestions.single()
        assertEquals("Arroz",suggestion.title)
        assertEquals("kg",suggestion.unit)
        assertEquals(800L,suggestion.draft.estimated)
        assertTrue(suggestion.draft.estimateKnown)
        assertFalse(suggestion.draft.bought)
        assertNull(suggestion.draft.actual)
        assertNotEquals("item1",suggestion.draft.id)
        assertFalse(alreadyOnShoppingList(suggestion,emptyList()))
        assertTrue(alreadyOnShoppingList(suggestion,listOf(Entry(title="Arroz",unit="kg"))))
    }

    @Test fun sameNameDifferentUnitStaysSeparateAndMostRecentWins() {
        val records=listOf(
            receipt("old","Mercado","2026-10-01",listOf(Entry("o","Alho","un",actual=300,bought=true))),
            receipt("new","Mercado","2026-10-23",listOf(Entry("n","Alho","un",actual=400,bought=true))),
            receipt("grams","Mercado","2026-10-22",listOf(Entry("g","Alho","g",actual=800,bought=true)))
        )
        val results=repurchaseSuggestions(records,"Mercado")
        assertEquals(2,results.size)
        assertEquals("2026-10-23",results.first { it.unit=="un" }.lastDate)
        assertEquals(400L,results.first { it.unit=="un" }.draft.estimated)
        assertEquals("2026-10-22",results.first { it.unit=="g" }.lastDate)
    }

    @Test fun unpurchasedReceiptsAndPrivateReceiptsDoNotGenerateSuggestions() {
        val records=listOf(
            receipt("r1","Mercado","2026-10-22",listOf(Entry(title="Café",bought=false))),
            Record("private","receipt",isPrivate=true,fields=mapOf(
                "market" to "Mercado", "date" to "2026-10-22",
                "entries" to listOf(Entry(title="Leite",bought=true,actual=500).toMap())))
        )
        assertTrue(repurchaseSuggestions(records,"Mercado").isEmpty())
    }
}
