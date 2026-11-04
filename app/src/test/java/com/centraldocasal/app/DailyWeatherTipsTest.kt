package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class DailyWeatherTipsTest {
    private val day = LocalDate.of(2026, 10, 18)
    private val weather = WeatherSnapshot(
        temperature = 22.0, feelsLike = 21.0, humidity = 65, wind = 9.0,
        todayHigh = 26.0, todayLow = 14.0, rainChance = null,
        description = "Parcialmente nublado",
        tomorrowHigh = 23.0, tomorrowLow = 12.0
    )

    @Test fun threeTipsComeFromObservedWeatherWithoutInventedTasks() {
        val tips = contextualWeatherTips(weather, emptyList(), day)
        assertEquals(3, tips.size)
        assertTrue(tips.none { "Rhayra" in it || "Buscar" in it })
        assertTrue(tips.any { "26" in it && "14" in it })
    }

    @Test fun hourlyRainIsLinkedOnlyToActualAppointment() {
        val task = Record("pickup", "task", fields = mapOf(
            "title" to "Buscar Rhayra", "date" to "2026-10-18",
            "time" to "17:00", "repeat" to "Única", "owner" to "Tiago"
        ))
        val hour = HourlyWeather(LocalDateTime.of(2026,10,18,17,0), 20.0, 75)
        val tips = contextualWeatherTips(weather.copy(hours = listOf(hour)),listOf(task),day)
        assertEquals(3,tips.size)
        assertTrue(tips.first().contains("Buscar Rhayra"))
        assertTrue(tips.first().contains("75%"))
    }

    @Test fun missingRainChanceNeverCreatesInventedProbability() {
        val tips = contextualWeatherTips(weather.copy(hours=listOf(
            HourlyWeather(LocalDateTime.of(2026,10,18,17,0),20.0,null)
        )),emptyList(),day)
        assertEquals(3,tips.size)
        assertFalse(tips.any { "%" in it })
    }

    @Test fun sparseForecastProvidesDistinctGroundedHints() {
        val sparse=weather.copy(todayHigh=21.0,todayLow=19.0,tomorrowHigh=null,tomorrowLow=null,hours=emptyList())
        val tips=contextualWeatherTips(sparse,emptyList(),day)
        assertEquals(3,tips.size)
        assertEquals(3,tips.distinct().size)
        assertTrue(tips.any { "9 km/h" in it })
    }

    @Test fun tasksOnOtherDatesDoNotLeakIntoWeatherTips() {
        val later=Record("future","task",fields=mapOf(
            "title" to "Compromisso particular", "date" to "2026-10-22",
            "time" to "16:00", "repeat" to "Única"
        ))
        val tips=contextualWeatherTips(weather,listOf(later),day)
        assertFalse(tips.any { "Compromisso particular" in it })
    }
}
