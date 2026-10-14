package com.centraldocasal.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

object Money {
    fun cents(text: String): Long {
        val normalized = text.trim().replace("R$", "").replace(" ", "").let { if (it.contains(',')) it.replace(".", "").replace(',', '.') else it }
        val value = BigDecimal(normalized)
        require(value >= BigDecimal.ZERO && value <= BigDecimal("10000000")) { "Informe um valor entre zero e R$ 10 milhões." }
        require(value.scale() <= 2) { "Use até duas casas decimais para preços." }
        return value.movePointRight(2).longValueExact()
    }
    fun quantity(text: String): Long {
        val value = BigDecimal(text.trim().replace(',', '.'))
        require(value > BigDecimal.ZERO && value <= BigDecimal("100000")) { "Quantidade deve ser positiva e até 100.000." }
        require(value.scale() <= 3) { "Use até três casas decimais na quantidade." }
        return value.movePointRight(3).longValueExact()
    }
    fun subtotal(cents: Long, milli: Long): Long {
        require(cents >= 0 && milli > 0)
        return BigDecimal(cents).multiply(BigDecimal(milli)).divide(BigDecimal(1000), 0, RoundingMode.HALF_UP).longValueExact()
    }
}

data class Entry(val id: String = UUID.randomUUID().toString(), val title: String = "", val unit: String = "un", val plannedMilli: Long = 1000, val actualMilli: Long = 1000, val estimated: Long = 0, val actual: Long? = null, val bought: Boolean = false, val estimateKnown: Boolean = true, val unitReviewed: Boolean = true, val quantityReviewed: Boolean = true, val sourceBaseId: String = "", val sourceSkuId: String = "", val category: String = "", val storageRoom: String = "", val recipient: String = "Casa", val group: String = "Essenciais", val subgroup: String = "", val priority: String = "Normal", val sourcePlanId: String = "", val sourceStepId: String = "") {
    fun estimateReady() = estimateKnown && unitReviewed && quantityReviewed && unit in allowedUnits && plannedMilli > 0 && estimated >= 0
    fun expected() = if (estimateReady()) Money.subtotal(estimated, plannedMilli) else 0L
    fun real() = if (bought) Money.subtotal(requireNotNull(actual) { "Falta o preço real de $title." }, actualMilli) else 0
    fun priceDifference() = if (bought && estimateReady()) Money.subtotal(requireNotNull(actual), actualMilli) - Money.subtotal(estimated, actualMilli) else 0
    fun quantityDifference() = if (bought && estimateReady()) Money.subtotal(estimated, actualMilli) - expected() else 0
    fun toMap(): Map<String, Any?> = mapOf("id" to id, "title" to title, "unit" to unit, "plannedMilli" to plannedMilli, "actualMilli" to actualMilli, "estimated" to estimated, "actual" to actual, "bought" to bought, "estimateKnown" to estimateKnown, "unitReviewed" to unitReviewed, "quantityReviewed" to quantityReviewed, "sourceBaseId" to sourceBaseId, "sourceSkuId" to sourceSkuId, "category" to category, "storageRoom" to storageRoom, "recipient" to recipient, "group" to group, "subgroup" to subgroup, "priority" to priority, "sourcePlanId" to sourcePlanId, "sourceStepId" to sourceStepId)
    companion object {
        val allowedUnits: Set<String> = setOf("un", "kg", "g", "L", "mL", "pacote")
        fun from(m: Map<String, Any?>) = Entry(m.str("id"), m.str("title"), m.str("unit", "un"), m.num("plannedMilli", 1000), m.num("actualMilli", 1000), m.num("estimated"), (m["actual"] as? Number)?.toLong(), m["bought"] == true, m["estimateKnown"] as? Boolean ?: true, m["unitReviewed"] as? Boolean ?: true, m["quantityReviewed"] as? Boolean ?: true, m.str("sourceBaseId"), m.str("sourceSkuId"), m.str("category"),m.str("storageRoom"),m.str("recipient", "Casa"),m.str("group", "Essenciais"),m.str("subgroup"),m.str("priority", "Normal"),m.str("sourcePlanId"),m.str("sourceStepId"))
    }
}
data class Basket(val market: String, val generation: String = UUID.randomUUID().toString(), val budget: Long = 0, val revision: Long = 0, val entries: List<Entry> = emptyList()) {
    fun expected() = entries.sumOf { it.expected() }
    fun unreviewedCount() = entries.count { !it.unitReviewed || !it.quantityReviewed || it.unit !in Entry.allowedUnits }
    fun unknownEstimateCount() = entries.count { !it.estimateKnown }
    fun real() = entries.sumOf { it.real() }
    fun validateClose() {
        require(entries.any { it.bought }) { "Marque ao menos um item comprado." }
        entries.filter { it.bought }.forEach {
            require(it.unitReviewed && it.unit in Entry.allowedUnits) { "Confirme a unidade de ${it.title}." }
            require(it.quantityReviewed && it.plannedMilli > 0 && it.actualMilli > 0) { "Confirme a quantidade de ${it.title}." }
            require(it.actual != null) { "Informe o preço real de ${it.title}." }
            require(it.actual >= 0) { "O preço real de ${it.title} não pode ser negativo." }
        }
    }
    fun toMap(): Map<String, Any?> = mapOf("market" to market, "generation" to generation, "budget" to budget, "revision" to revision, "entries" to entries.map { it.toMap() })
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(m: Map<String, Any?>) = Basket(m.str("market", "Mercado"), m.str("generation"), m.num("budget"), m.num("revision"), (m["entries"] as? List<Map<String, Any?>> ?: emptyList()).map(Entry::from))
    }
}
data class Record(val id: String = UUID.randomUUID().toString(), val kind: String, val isPrivate: Boolean = false, val fields: Map<String, Any?>) {
    fun text(key: String, fallback: String = "") = fields.str(key, fallback)
    fun number(key: String, fallback: Long = 0) = fields.num(key, fallback)
    fun flag(key: String) = fields[key] == true
    fun toMap(): Map<String, Any?> = fields + mapOf("kind" to kind, "private" to isPrivate)
}
fun Map<String, Any?>.str(key: String, fallback: String = "") = this[key] as? String ?: fallback
fun Map<String, Any?>.num(key: String, fallback: Long = 0) = (this[key] as? Number)?.toLong() ?: fallback
fun occurs(task: Record, day: LocalDate): Boolean {
    if (task.flag("needsReview")) return false
    val start = runCatching { LocalDate.parse(task.text("date")) }.getOrNull() ?: return false
    if (day < start) return false
    return when (task.text("repeat")) {
        "Diária" -> true
        "Semanal" -> day.dayOfWeek == start.dayOfWeek
        "Dias da semana" -> (task.fields["days"] as? List<*>)?.any {
            it is Number && it.toDouble() == day.dayOfWeek.value.toDouble()
        } == true
        "Mensal" -> day.dayOfMonth == minOf(start.dayOfMonth, day.lengthOfMonth())
        "Primeiro domingo após dia 8" -> day.dayOfWeek == java.time.DayOfWeek.SUNDAY && day.dayOfMonth in 9..15
        else -> day == start
    }
}
fun occurrenceId(taskId: String, day: LocalDate) = "$taskId:$day"
fun quietAt(hour: Int, minute: Int, start: String = "00:05", end: String = "05:40"): Boolean {
    fun minutes(s: String): Int { val p = s.split(':'); return p[0].toInt() * 60 + p[1].toInt() }
    val now = hour * 60 + minute; val from = minutes(start); val to = minutes(end)
    return if (from <= to) now in from until to else now >= from || now < to
}
