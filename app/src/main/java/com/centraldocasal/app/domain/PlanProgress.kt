package com.centraldocasal.app.domain

data class PlanProgress(val target: Long?, val registered: Long, val remaining: Long?, val fraction: Float, val history: List<Record>)

/** Only explicit contributions in the plan's scope count. Imported preferences are not goals. */
fun planProgress(plan: Record, records: List<Record>): PlanProgress {
    require(plan.kind == "plan")
    val history = records.filter { it.kind == "contribution" && it.text("planId") == plan.id && it.isPrivate == plan.isPrivate }
        .distinctBy { it.id }.sortedWith(compareByDescending<Record> { it.text("date") }.thenBy { it.id })
    val total = history.fold(0L) { amount, record ->
        val next = record.number("amount").coerceAtLeast(0)
        if (Long.MAX_VALUE - amount < next) Long.MAX_VALUE else amount + next
    }
    val target = (plan.fields["target"] as? Number)?.toLong()?.takeIf { it > 0 }
    return PlanProgress(target, total, target?.let { (it - total).coerceAtLeast(0) },
        target?.let { (total.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() } ?: 0f, history)
}
