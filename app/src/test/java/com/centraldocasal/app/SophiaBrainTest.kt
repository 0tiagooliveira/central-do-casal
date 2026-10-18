package com.centraldocasal.app

import com.centraldocasal.app.data.DataState
import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Basket
import com.centraldocasal.app.domain.Entry
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class SophiaBrainTest {
    private val now = LocalDateTime.of(2026, 10, 18, 15, 0)
    private val tiago = SessionState(uid = "tiago-uid", name = "Tiago Oliveira", local = true)
    private val rhayra = SessionState(uid = "rhayra-uid", name = "Rhayra", local = true)

    private fun task(id: String, owner: String, date: String = "2026-10-18",
                     title: String = "Pagar a conta") = Record(
        id, "task", fields = mapOf("title" to title, "owner" to owner,
            "date" to date, "repeat" to "Única", "time" to "19:00")
    )

    @Test fun readOnlyAgendaIsScopedToTheCurrentMemberAndCompletion() {
        val records = listOf(
            task("mine", "Tiago", title = "Buscar Mel"),
            task("partner", "Rhayra", title = "Assunto particular"),
            task("shared", "Ambos", title = "Jantar"),
            Record(occurrenceId("shared", now.toLocalDate()), "occurrence",
                fields = mapOf("done" to true))
        )
        val snapshot = DataState(records = records)
        val answer = SophiaBrain.decide("Como está meu dia?", tiago, snapshot, now)
        assertTrue(answer.message.contains("Buscar Mel"))
        assertFalse(answer.message.contains("Assunto particular"))
        assertFalse(answer.message.contains("Jantar"))
        assertNull(answer.proposedOperation)
        val partner = SophiaBrain.decide("agenda", rhayra, snapshot, now)
        assertTrue(partner.message.contains("Assunto particular"))
        assertFalse(partner.message.contains("Buscar Mel"))
    }

    @Test fun tomorrowQueryRespectsRecurringDatesInsteadOfRepeatingToday() {
        val future = task("future", "Tiago", "2026-10-19", "Dar ração")
        val answer = SophiaBrain.decide("agenda amanhã", tiago,
            DataState(records = listOf(future)), now)
        assertTrue(answer.message.contains("Dar ração"))
        assertNull(answer.proposedOperation)
    }

    @Test fun creatingReminderOnlyProducesAProposalAndScopesItToUid() {
        val answer = SophiaBrain.decide(
            "Lembre-me de dar ração à Mel amanhã às 22:00",
            tiago, DataState(), now, requestId = "00000000-0000-4000-8000-000000000001"
        )
        val op = answer.proposedOperation as SophiaOperation.CreateTask
        assertEquals(tiago.uid, op.sessionUid)
        assertEquals("dar ração à Mel", op.title)
        assertEquals(LocalDate.of(2026, 10, 19), op.day)
        assertEquals(LocalTime.of(22, 0), op.time)
        assertEquals("00000000-0000-4000-8000-000000000001", op.recordId)
        assertTrue(answer.message.contains("Confirmar"))
        assertTrue(answer.evidence.contains("nenhuma alteração"))
    }

    @Test fun creatingTaskWithoutTimeCannotSilentlyScheduleAnAlarm() {
        val answer = SophiaBrain.decide("Crie uma tarefa estudar amanhã", tiago,
            DataState(), now)
        val operation = answer.proposedOperation as SophiaOperation.CreateTask
        assertNull(operation.time)
        assertEquals(LocalDate.of(2026,10,19), operation.day)
        assertTrue(answer.message.contains("sem notificação"))

        val missing = SophiaBrain.decide("Me lembre de estudar amanhã", tiago,
            DataState(), now)
        assertNull(missing.proposedOperation)
        assertTrue(missing.message.contains("informe o horário"))
    }

    @Test fun badTimesOrEmptyTitlesNeverProduceWrites() {
        val invalid = listOf(
            "Lembre-me de teste às 25:80",
            "Lembre-me de x às 18:00",
            "Crie uma tarefa ",
            "Lembre-me de teste às banana"
        )
        invalid.forEach {
            assertNull(SophiaBrain.decide(it, tiago, DataState(), now).proposedOperation)
        }
    }

    @Test fun completeOnlyExactUniquePendingTaskAfterUserConfirmation() {
        val data = DataState(records = listOf(task("a", "Tiago"),
            task("partner", "Rhayra")))
        val valid = SophiaBrain.decide("Concluir tarefa Pagar a conta", tiago, data, now)
        assertEquals("a", (valid.proposedOperation as SophiaOperation.CompleteTask).taskId)
        assertEquals(tiago.uid, valid.proposedOperation?.sessionUid)
        assertNull(SophiaBrain.decide("Concluir tarefa Pagar a conta", rhayra,
            DataState(records = listOf(task("a", "Tiago"))), now).proposedOperation)
        val duplicates = DataState(records = listOf(task("a", "Tiago"),task("b","Tiago")))
        assertNull(SophiaBrain.decide("Concluir tarefa Pagar a conta", tiago,
            duplicates, now).proposedOperation)
    }

    @Test fun shoppingReadsActualListWithoutClaimingPurchase() {
        val data = DataState(baskets = mapOf("Mercado" to Basket("Mercado",
            entries = listOf(Entry(title = "Arroz"), Entry(title = "Banana", bought = true)))))
        val decision = SophiaBrain.decide("O que falta nas compras?", tiago, data, now)
        assertTrue(decision.message.contains("1 item"))
        assertNull(decision.proposedOperation)
    }

    @Test fun smartHomeCountsOnlyRegisteredUnarchivedDevicesWithoutClaimingConnection() {
        val data = DataState(records = listOf(
            Record("d1", "smartDevice", fields = mapOf("title" to "Luz")),
            Record("d2", "smartDevice", fields = mapOf("title" to "TV", "archived" to true))))
        val decision = SophiaBrain.decide("Como está nossa casa?", tiago, data, now)
        assertTrue(decision.message.contains("1 aparelho"))
        assertTrue(decision.message.contains("não comprova"))
        assertNull(decision.proposedOperation)
    }

    @Test fun unrecognizedCommandNeverTriggersAnExternalAction() {
        val answer = SophiaBrain.decide("Ligue a lâmpada do quarto", tiago, DataState(), now)
        assertNull(answer.proposedOperation)
        assertTrue(answer.message.contains("ainda não um chat generativo"))
    }

    @Test fun unauthenticatedOrLongInputIsRejected() {
        val guest = SessionState()
        assertNull(SophiaBrain.decide("Agenda", guest, DataState(), now).proposedOperation)
        assertNull(SophiaBrain.decide("crie uma tarefa " + "a".repeat(400),tiago,
            DataState(), now).proposedOperation)
    }
}
