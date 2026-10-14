package com.centraldocasal.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PriceComparisonTest {
    private fun point(date: String, price: String, unit: String = "kg") = ObservedUnitPrice(date, unit, price)
    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test fun preservesMoreThanTwoDecimalsAndExactDifference() {
        val result = comparePrices(listOf(point("2026-01-01", "1.2345"), point("2026-02-01", "1.2350")), "kg")!!
        assertDecimal("1.2345", result.first.price)
        assertDecimal("1.2350", result.last.price)
        assertDecimal("0.0005", result.difference)
        assertDecimal("0.04050223", result.percent!!)
        assertEquals(PriceTrend.HIGHER, result.trend)
    }

    @Test fun reversedInputUsesChronologyAndRecognizesLowerPrice() {
        val result = comparePrices(listOf(point("2026-02-01", "1.2345"), point("2026-01-01", "1.2350")), "KG")!!
        assertEquals("2026-01-01", result.first.date)
        assertDecimal("-0.0005", result.difference)
        assertTrue(result.percent!! < BigDecimal.ZERO)
        assertEquals(PriceTrend.LOWER, result.trend)
    }

    @Test fun differentUnitsRemainSeparateWithoutConversionOrMerging() {
        val points = listOf(point("2026-01-01", "10", "kg"), point("2026-02-01", "12", " KG "), point("2026-01-02", "0.01", "g"), point("2026-02-02", "0.009", "G"), point("2026-03-01", "3", "un"))
        val groups = comparePriceGroups(points)
        assertEquals(setOf("KG", "G"), groups.keys)
        assertDecimal("2", groups.getValue("KG").difference)
        assertDecimal("-0.001", groups.getValue("G").difference)
        assertEquals(2, groups.getValue("KG").points.size)
        assertNull(comparePrices(points, "un"))
        assertNull(comparePrices(points, "L"))
    }

    @Test fun equalPricesWithDifferentScalesHaveSameTrend() {
        val result = comparePrices(listOf(point("2026-01-01", "2.5000"), point("2026-02-01", "2.5")), "kg")!!
        assertEquals(PriceTrend.SAME, result.trend)
        assertDecimal("0", result.difference)
        assertDecimal("0", result.percent!!)
    }

    @Test fun zeroBaselineDoesNotInventAPercentage() {
        val result = comparePrices(listOf(point("2026-01-01", "0.0000"), point("2026-02-01", "2.5")), "kg")!!
        assertNull(result.percent)
        assertDecimal("2.5", result.difference)
        assertEquals(PriceTrend.HIGHER, result.trend)
    }

    @Test fun invalidPricesDatesAndUnitsAreIgnoredWithoutBecomingZero() {
        val points = listOf(point("2026-01-01", "1.25"), point("2026-01-02", "NaN"), point("2026-01-03", "-1"), point("2026-01-04", ""), point("2026-02-30", "3"), point("not-a-date", "3"), point("2026-02-01", "7", " "), point("2026-03-01", "1.50"))
        val result = comparePrices(points, "kg")!!
        assertEquals(2, result.points.size)
        assertDecimal("0.25", result.difference)
        assertDecimal("20", result.percent!!)
    }

    @Test fun duplicateDatesRetainObservedOrderAndActualLastObservation() {
        val points = listOf(point("2026-02-01", "3"), point("2026-01-01", "1"), point("2026-02-01", "2"))
        val result = comparePrices(points, "kg")!!
        assertEquals(listOf("1", "3", "2"), result.points.map { it.price.toPlainString() })
        assertDecimal("2", result.last.price)
        assertDecimal("1", result.difference)
    }
}
