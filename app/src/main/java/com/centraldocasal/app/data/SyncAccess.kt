package com.centraldocasal.app.data

internal fun confirmedProfile(pending: Boolean, fromCache: Boolean, previouslyConfirmed: Boolean = false) = !pending && (!fromCache || previouslyConfirmed)

/** Recovery in one stream must not hide a denial in another stream. */
internal class SyncAccess {
    private val errors = linkedMapOf<String, String>()
    val error: String? get() = errors.values.firstOrNull()
    fun fail(source: String, message: String) { errors[source] = message }
    fun confirm(source: String) { errors.remove(source) }
    fun reset() { errors.clear() }
    fun retainProfile() { errors.keys.retainAll(setOf("profile")) }
}
