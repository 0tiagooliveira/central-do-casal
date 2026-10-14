package com.centraldocasal.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/** Each call must contain observations from one SKU. Different units stay separate. */
data class ObservedUnitPrice(val date: String, val unit: String, val priceText: String)

data class UnitPricePoint(val date: String, val unit: String, val price: BigDecimal)

enum class PriceTrend { LOWER, HIGHER, SAME }

data class UnitPriceComparison(
    val unit: String,
    val first: UnitPricePoint,
    val last: UnitPricePoint,
    val difference: BigDecimal,
    val percent: BigDecimal?,
    val trend: PriceTrend,
    val points: List<UnitPricePoint>
)

private fun normalizedUnit(unit: String) = unit.trim().uppercase(Locale.ROOT)

private fun validPricePoints(points: List<ObservedUnitPrice>): List<UnitPricePoint> = points.mapNotNull {
    val unit = normalizedUnit(it.unit)
    if (unit.isBlank()) return@mapNotNull null
    val date = runCatching { LocalDate.parse(it.date) }.getOrNull() ?: return@mapNotNull null
    val price = runCatching { BigDecimal(it.priceText.trim()) }.getOrNull() ?: return@mapNotNull null
    if (price < BigDecimal.ZERO) return@mapNotNull null
    UnitPricePoint(date.toString(), unit, price)
}.sortedBy { it.date }

private fun comparison(unit: String, points: List<UnitPricePoint>): UnitPriceComparison? {
    if (points.size < 2) return null
    val first = points.first()
    val last = points.last()
    val difference = last.price.subtract(first.price)
    val percent = if (first.price.signum() == 0) null else
        difference.multiply(BigDecimal("100")).divide(first.price, 8, RoundingMode.HALF_UP)
    val trend = when (difference.signum()) {
        -1 -> PriceTrend.LOWER
        1 -> PriceTrend.HIGHER
        else -> PriceTrend.SAME
    }
    return UnitPriceComparison(unit, first, last, difference, percent, trend, points.toList())
}

fun comparePrices(points: List<ObservedUnitPrice>, unit: String): UnitPriceComparison? {
    val selectedUnit = normalizedUnit(unit)
    return comparison(selectedUnit, validPricePoints(points).filter { it.unit == selectedUnit })
}

fun comparePriceGroups(points: List<ObservedUnitPrice>): Map<String, UnitPriceComparison> =
    validPricePoints(points).groupBy { it.unit }.mapNotNull { (unit, history) ->
        comparison(unit, history)?.let { unit to it }
    }.toMap()
