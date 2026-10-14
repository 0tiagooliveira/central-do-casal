package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import java.util.UUID

/** The user explicitly opens and confirms this draft. Imported references stay untouched. */
internal fun projectDraft(reference: Record): Record {
    require(reference.kind=="importedPlan")
    val steps=(reference.fields["pending"] as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotBlank() }?.take(100)?.map { mapOf("id" to UUID.randomUUID().toString(),"title" to it,"done" to false) } ?: emptyList()
    return Record(kind="plan",isPrivate=reference.isPrivate,fields=mapOf("title" to reference.text("title"),"note" to reference.text("note"),"horizon" to "Curto prazo","date" to LocalDate.now().toString(),"sourcePlanId" to reference.id,"steps" to steps,"stageRevision" to 0L))
}
