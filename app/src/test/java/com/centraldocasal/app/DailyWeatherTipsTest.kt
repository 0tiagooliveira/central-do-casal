package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class DailyWeatherTipsTest {
    private val now = LocalDateTime.of(2026, 10, 18, 15, 0)
    private val base = WeatherSnapshot(
        temperature=22.0, feelsLike=21.0, humidity=65, wind=9.0,
        todayHigh=26.0, todayLow=14.0, rainChance=null, description="Parcialmente nublado",
        tomorrowHigh=23.0, tomorrowLow=12.0
    )

    @Test fun threeTipsComeFromRealWeatherAndNoInventedTasks() {
        val result = dailyWeatherTips(base, emptyList(), now)
        assertEquals(3, result.size)
        assertTrue(result.none { it.title.contains("Rhayra") || it.title.contains("Lucca") })
        assertTrue(result.any { it.detail.contains("26") && it.detail.contains("14") })
    }

    @Test fun scheduledRainBecomesFirstTipWithoutInventedEvent() {
        val at = LocalDateTime.of(2026,10,18,17,0)
        val task = Record("pickup","task",fields=mapOf(
            "title" to "Buscar Rhayra","date" to "2026-10-18",
            "time" to "17:00","repeat" to "Única","owner" to "Tiago"
        ))
        val forecast = base.copy(hours=listOf(
            WeatherHour(LocalDateTime.of(2026,10,18,16,0),23.0,10,2),
            WeatherHour(at,20.0,75,61),
            WeatherHour(LocalDateTime.of(2026,10,18,20,0),13.0,30,2)
        ))
        val result = dailyWeatherTips(forecast, listOf(task), now)
        assertEquals(3, result.size)
        assertTrue(result[0].title.contains("Buscar Rhayra"))
        assertTrue(result[0].detail.contains("75%"))
        assertTrue(result.any { it.title == "Prepare a noite" })
    }

    @Test fun missingHourlyRainDoesNotClaimSpecificTimeOrProbability() {
        val tips = dailyWeatherTips(base.copy(rainChance=null, hours=listOf(
            WeatherHour(now.plusHours(1),20.0,null,3)
        )), emptyList(), now)
        assertEquals(3,tips.size)
        assertFalse(tips.any { it.title.contains("Chuva") || it.detail.contains("% de chance") })
    }

    @Test fun sparseForecastStillReturnsThreeGroundedHints() {
        val sparse = base.copy(todayHigh=21.0,todayLow=19.0,tomorrowHigh=null,tomorrowLow=null,hours=emptyList())
        val tips = dailyWeatherTips(sparse,emptyList(),now)
        assertEquals(3,tips.size)
        assertEquals(3,tips.map{it.title}.distinct().size)
        assertTrue(tips.any { it.detail.contains("9 km/h") })
    }

    @Test fun noTaskOutsideTodayOrTomorrowCanLeakIntoTip() {
        val task = Record("future","task",fields=mapOf(
            "title" to "Compromisso particular", "date" to "2026-10-22",
            "time" to "16:00", "repeat" to "Única"
        ))
        val tips = dailyWeatherTips(base,listOf(task),now)
        assertFalse(tips.any { it.title.contains("Compromisso particular") })
    }
}
