package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class HourlyWeatherContextTest {
    private val day=LocalDate.of(2026,10,24)

    @Test fun parsesOnlyAvailableHourlyObservations() {
        val json="""{
          "current":{"temperature_2m":21,"apparent_temperature":20,"relative_humidity_2m":70,"wind_speed_10m":8,"weather_code":3},
          "daily":{"temperature_2m_max":[25,26],"temperature_2m_min":[14,15],"precipitation_probability_max":[90,70]},
          "hourly":{
            "time":["2026-10-24T15:00","2026-10-24T16:00","2026-10-24T17:00"],
            "temperature_2m":[23,21,null],
            "precipitation_probability":[10,85,null]
          }
        }"""
        val weather=WeatherSource.parse(json)
        assertEquals(3,weather.hours.size)
        assertEquals(LocalDateTime.of(2026,10,24,16,0),weather.hours[1].time)
        assertEquals(85,weather.hours[1].rainChance)
        assertNull(weather.hours[2].rainChance)
        assertNull(weather.hours[2].temperature)
    }

    @Test fun contextualTipUsesHourOfAppointmentInsteadOfDailyMaximum() {
        val weather=WeatherSnapshot(
            temperature=22.0,feelsLike=21.0,humidity=65,wind=6.0,
            todayHigh=28.0,todayLow=16.0,rainChance=95,
            description="Parcialmente nublado",
            tomorrowHigh=24.0,tomorrowLow=15.0,tomorrowRainChance=60,
            hours=listOf(
                HourlyWeather(LocalDateTime.of(2026,10,24,15,0),25.0,10),
                HourlyWeather(LocalDateTime.of(2026,10,24,16,0),23.0,85)
            )
        )
        val task=Record("pickup","task",fields=mapOf(
            "title" to "Buscar a Rhayra","date" to day.toString(),
            "time" to "16:30","repeat" to "Única"
        ))
        val tips=contextualWeatherTips(weather,listOf(task),day)
        assertEquals(3,tips.size)
        assertTrue(tips[0],tips[0].contains("85%"))
        assertTrue(tips[0],tips[0].contains("16:30"))
        assertFalse(tips[0],tips[0].contains("95%"))
    }

    @Test fun doesNotInventHourlyChanceWhenHourIsUnavailable() {
        val weather=WeatherSnapshot(
            temperature=22.0,feelsLike=21.0,humidity=65,wind=6.0,
            todayHigh=28.0,todayLow=16.0,rainChance=95,
            description="Parcialmente nublado",
            hours=listOf(HourlyWeather(LocalDateTime.of(2026,10,24,13,0),22.0,85))
        )
        val task=Record("pickup","task",fields=mapOf(
            "title" to "Buscar a Rhayra","date" to day.toString(),
            "time" to "16:30","repeat" to "Única"
        ))
        val first=contextualWeatherTips(weather,listOf(task),day).first()
        assertTrue(first,first.contains("chance máxima"))
        assertTrue(first,first.contains("não temos previsão específica"))
        assertFalse(first,first.contains("85%"))
    }
}
