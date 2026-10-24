package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ContextualWeatherTipsTest {
    private val day = LocalDate.of(2026,10,24)
    private val rainy = WeatherSnapshot(
        temperature=19.0,feelsLike=14.0,humidity=85,wind=12.0,
        todayHigh=22.0,todayLow=13.0,rainChance=84,
        description="Chuva",tomorrowHigh=23.0,tomorrowLow=14.0,tomorrowRainChance=75
    )

    @Test fun threeTipsUseOnlyRealForecastAndVisibleAppointments() {
        val task=Record("school","task",fields=mapOf(
            "title" to "Buscar a Rhayra no ponto",
            "date" to day.toString(),"time" to "16:30","repeat" to "Única"
        ))
        val result=contextualWeatherTips(rainy,listOf(task),day)
        assertEquals(3,result.size)
        assertTrue(result[0].contains("84%"))
        assertTrue(result[0].contains("Buscar a Rhayra no ponto"))
        assertTrue(result[0].contains("16:30"))
        assertTrue(result[1].contains("75%"))
        assertTrue(result[2].contains("14 °C"))
    }

    @Test fun neverMentionsUnreviewedOrOtherDayTask() {
        val skipped=Record("other","task",fields=mapOf(
            "title" to "Segredo de outro dia",
            "date" to day.plusDays(1).toString(),"time" to "10:00","repeat" to "Única"
        ))
        val unreviewed=Record("unreviewed","task",fields=mapOf(
            "title" to "Horário pendente",
            "date" to day.toString(),"time" to "10:00","repeat" to "Única","needsTimeReview" to true
        ))
        val result=contextualWeatherTips(rainy,listOf(skipped,unreviewed),day)
        assertEquals(3,result.size)
        assertFalse(result.joinToString(" ").contains("Segredo"))
        assertFalse(result.joinToString(" ").contains("Horário pendente"))
    }
}
