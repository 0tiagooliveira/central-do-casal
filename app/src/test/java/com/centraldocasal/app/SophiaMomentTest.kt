package com.centraldocasal.app

import android.app.Application
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class SophiaMomentTest {
    private fun task(id: String, owner: String, at: LocalDateTime, title: String = "Buscar no ponto",
                     skip: Boolean = false, private: Boolean = false) =
        Record(id, "task", private, mapOf(
            "title" to title, "owner" to owner, "date" to at.toLocalDate().toString(),
            "time" to at.toLocalTime().toString(), "repeat" to "Única",
            "skipReminder" to skip
        ))
    private fun forecast(events: List<Pair<LocalDateTime,Int?>>, temp: Double = 22.0) = WeatherSnapshot(
        temperature = temp, feelsLike = temp, humidity = 55, wind = 5.0,
        todayHigh = 26.0, todayLow = 18.0, rainChance = 80, description = "Nublado",
        fetchedAtMillis = System.currentTimeMillis(),
        hours = events.map { HourlyWeather(it.first, temp, it.second) }
    )

    @Test fun differentUsersNeverReceivePartnersAssignedTasks() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(3)
        val tiago = task("tiago", "Tiago", event)
        val rhayra = task("rhayra", "Rhayra", event, "Faculdade")
        val weather = forecast(listOf(event to 85))
        assertEquals(listOf("tiago:${event.toLocalDate()}"),
            SophiaMomentPlanner.plan(weather, listOf(tiago, rhayra), "Tiago Oliveira", now).map { it.id })
        assertEquals(listOf("rhayra:${event.toLocalDate()}"),
            SophiaMomentPlanner.plan(weather, listOf(tiago, rhayra), "Rhayra", now).map { it.id })
    }

    @Test fun householdTasksAreEligibleForBothAccountsButPrivateOwnerNeverLeaks() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(3)
        val shared = task("shared", "Ambos", event, "Passeio com Mel")
        val privateTask = task("secret", "Tiago", event, "Buscar no ponto", private = true)
        val weather = forecast(listOf(event to 75))
        assertEquals(setOf("shared", "secret"), SophiaMomentPlanner.plan(weather,
            listOf(shared, privateTask), "Tiago", now).map { it.taskId }.toSet())
        assertEquals(listOf("shared"), SophiaMomentPlanner.plan(weather,
            listOf(shared, privateTask), "Rhayra", now).map { it.taskId })
    }

    @Test fun completedOptedOutOrUnreviewedTasksNeverGetAdvice() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(2)
        val completed = task("done", "Tiago", event)
        val doneMark = Record(occurrenceId("done", event.toLocalDate()), "occurrence",
            fields = mapOf("done" to true))
        val skip = task("skip", "Tiago", event, skip = true)
        val unreviewed = task("review", "Tiago", event).copy(fields =
            task("review", "Tiago", event).fields + ("needsTimeReview" to true))
        assertTrue(SophiaMomentPlanner.plan(forecast(listOf(event to 95)),
            listOf(completed, doneMark, skip, unreviewed), "Tiago", now).isEmpty())
    }

    @Test fun threeBestAlertsPerDayWithActionableFortyFiveMinuteLead() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(4)
        val records = (1..5).map { task("task-$it", "Tiago", event, "Buscar item $it") }
        val selected = SophiaMomentPlanner.plan(forecast(listOf(event to 70)), records, "Tiago", now)
        assertEquals(3, selected.size)
        assertEquals(listOf("task-1","task-2","task-3"), selected.map { it.taskId })
        assertEquals(event.minusMinutes(45), selected.first().notifyAt)
    }

    @Test fun unknownRainDoesNotProduceImaginaryRainAlert() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(2)
        assertTrue(SophiaMomentPlanner.plan(forecast(listOf(event to null)),
            listOf(task("t", "Tiago", event)), "Tiago", now).isEmpty())
    }

    @Test fun coldAdviceOnlyForRelevantRoutine() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(2)
        val tasks = listOf(task("bath", "Tiago", event, "Banho do Lucca"),
            task("generic", "Tiago", event, "Conferir documentos"))
        assertEquals(listOf("bath"), SophiaMomentPlanner.plan(
            forecast(listOf(event to 20), temp = 11.0), tasks, "Tiago", now).map { it.taskId })
    }

    @Test fun oldForecastNeverCreatesAlerts() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusHours(2)
        val outdated = forecast(listOf(event to 100))
            .copy(fetchedAtMillis = System.currentTimeMillis() - 4 * 60 * 60 * 1000L)
        assertTrue(SophiaMomentPlanner.plan(outdated,
            listOf(task("t", "Tiago", event)), "Tiago", now).isEmpty())
    }

    @Test fun ignoresEventsThatCanNoLongerBePrepared() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val event = now.plusMinutes(8)
        assertTrue(SophiaMomentPlanner.plan(forecast(listOf(event to 90)),
            listOf(task("t", "Tiago", event)), "Tiago", now).isEmpty())
    }
}
