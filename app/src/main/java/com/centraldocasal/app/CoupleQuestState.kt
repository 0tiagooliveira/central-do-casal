package com.centraldocasal.app

import com.centraldocasal.app.domain.Record

/**
 * Local presentation rules. Two entries in an array do not prove two household
 * participants confirmed a real experience. Only distinct, current household
 * members count toward progress and completion.
 *
 * Firestore security rules remain authoritative for accepting an update.
 */
internal data class CoupleQuestState(
    val participants: Set<String>,
    val accepted: Set<String>,
    val completed: Set<String>
) {
    val readyToComplete: Boolean get() = participants.size == 2 && accepted == participants
    val confirmedByBoth: Boolean get() = readyToComplete && completed == participants
    val progress: Float get() =
        if (participants.size != 2) 0f else ((accepted.size + completed.size) / 4f).coerceIn(0f, 1f)
}

internal fun coupleQuestState(quest: Record, householdMembers: List<String>): CoupleQuestState {
    val participants = householdMembers.filter(String::isNotBlank).toSet()
        .takeIf { it.size == 2 } ?: emptySet()
    fun confirmed(key: String): Set<String> =
        (quest.fields[key] as? List<*>)
            .orEmpty().filterIsInstance<String>().filter { it in participants }.toSet()
    return CoupleQuestState(
        participants,
        confirmed("accepted"),
        confirmed("completed").intersect(confirmed("accepted"))
    )
}
