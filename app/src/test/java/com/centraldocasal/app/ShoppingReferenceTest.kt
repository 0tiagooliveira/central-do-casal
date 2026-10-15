package com.centraldocasal.app

import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingReferenceTest {
    @Test fun verifiedActualPricesOnlyCountPurchasedItems() {
        val market=Basket("Mercado",budget=20000,entries=listOf(
            Entry(id="milk",title="Leite",bought=true,actual=429,actualMilli=2000,
                estimated=499,estimateKnown=true),
            Entry(id="rice",title="Arroz",bought=false,actual=null,estimated=2300),
            Entry(id="bread",title="Pão",bought=true,actual=null,estimateKnown=false)
        ))
        val overview=shoppingOverview(listOf(market))
        assertEquals(858L,overview.spent)
        assertEquals(2,overview.purchased)
        assertEquals(1,overview.missingPrices)
        assertEquals(3,overview.count)
        assertEquals(20000L,overview.budget)
        assertEquals(3298L,overview.planned)
    }
    @Test fun absentBudgetAndPricesRemainUnknownInsteadOfFabricated() {
        val overview=shoppingOverview(listOf(Basket("Mercado",entries=listOf(
            Entry(title="Fralda",estimateKnown=false,estimated=0,bought=false)
        )),Basket("Açougue")))
        assertEquals(0L,overview.budget)
        assertEquals(0L,overview.spent)
        assertEquals(0L,overview.planned)
        assertEquals(0,overview.knownEstimated)
        assertEquals(1,overview.count)
    }
}
