package com.centraldocasal.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class WeatherSourceTest {
    @Test fun weatherCacheGroupsNearbyCoordinatesWithoutSharingUnrelatedCities() {
        assertEquals(weatherCacheCell(-25.4283,-49.2731),weatherCacheCell(-25.4284,-49.2732))
        assertNotEquals(weatherCacheCell(-25.4283,-49.2731),weatherCacheCell(-23.5505,-46.6333))
    }

    @Test fun apiCoordinatesAreRoundedToAboutOneKilometer() {
        val rawLatitude = -25.428376
        val rawLongitude = -49.273156
        val coarse = weatherRequestCoordinates(rawLatitude,rawLongitude)
        assertEquals("-25.43",coarse.first)
        assertEquals("-49.27",coarse.second)
        assertEquals(coarse,weatherRequestCoordinates(-25.4284,-49.2732))
        assertFalse(coarse.first.contains("8376"))
        assertFalse(coarse.second.contains("3156"))
    }

    @Test fun parsesForecastAndTranslatesWeatherCodes(){
        val json="""{
          "current":{"temperature_2m":21.7,"apparent_temperature":20.3,"relative_humidity_2m":77,"wind_speed_10m":8.1,"weather_code":63},
          "daily":{"temperature_2m_max":[26.0,24.4],"temperature_2m_min":[14.2,13.3],"precipitation_probability_max":[82,70]}
        }"""
        val weather=WeatherSource.parse(json)
        assertEquals(21.7,weather.temperature,0.01)
        assertEquals(20.3,weather.feelsLike,0.01)
        assertEquals(77,weather.humidity)
        assertEquals(82,weather.rainChance)
        assertEquals(24.4,weather.tomorrowHigh!!,0.01)
        assertEquals(13.3,weather.tomorrowLow!!,0.01)
        assertEquals(70,weather.tomorrowRainChance)
        assertEquals("Chuva",weather.description)
        assertEquals("Trovoadas",weatherDescription(95))
    }

    @Test fun missingTomorrowForecastDoesNotInventValues() {
        val json = """{
          "current":{"temperature_2m":19,"apparent_temperature":18,"relative_humidity_2m":75,"wind_speed_10m":5,"weather_code":3},
          "daily":{"temperature_2m_max":[23],"temperature_2m_min":[15],"precipitation_probability_max":[40]}
        }"""
        val snapshot = WeatherSource.parse(json)
        assertNull(snapshot.tomorrowHigh)
        assertNull(snapshot.tomorrowLow)
        assertNull(snapshot.tomorrowRainChance)
    }
    @Test fun hourlyForecastUsesProvidedValuesWithoutInventingMissingRain() {
        val json = """{
          "current":{"temperature_2m":20,"apparent_temperature":19,"relative_humidity_2m":65,"wind_speed_10m":11,"weather_code":2},
          "hourly":{
            "time":["2026-10-14T15:00","2026-10-14T16:00"],
            "temperature_2m":[20.5,21.3],
            "precipitation_probability":[70,null],
            "weather_code":[61,2]
          },
          "daily":{
            "time":["2026-10-14","2026-10-15","2026-10-16"],
            "temperature_2m_max":[25,23,21],
            "temperature_2m_min":[15,13,12],
            "precipitation_probability_max":[70,25,null]
          }
        }"""
        val forecast = WeatherSource.parse(json)
        assertEquals(2, forecast.hours.size)
        assertEquals(70, forecast.hours[0].rainChance)
        assertNull(forecast.hours[1].rainChance)
        assertEquals(3, forecast.days.size)
        assertNull(forecast.days[2].rainChance)
        assertEquals(21.3, forecast.hours[1].temperature, 0.01)
    }

    @Test fun weatherInsightConnectsRainWithActualTaskTime() {
        val appointment = java.time.LocalDateTime.of(2026,10,14,17,0)
        val task = com.centraldocasal.app.domain.Record(
            id = "pick-up",
            kind = "task",
            fields = mapOf("title" to "Buscar a Rhayra", "date" to "2026-10-14",
                "time" to "17:00", "repeat" to "Única")
        )
        val forecast = WeatherSnapshot(
            temperature = 22.0, feelsLike = 21.0, humidity = 78,
            wind = 9.0, todayHigh = 24.0, todayLow = 16.0,
            rainChance = 80, description = "Nublado",
            hours = listOf(WeatherHour(appointment, 21.0, 75, 61))
        )
        val insights = weatherInsights(
            forecast, listOf(task),
            now = java.time.LocalDateTime.of(2026,10,14,15,0)
        )
        assertTrue(insights.any { "Buscar a Rhayra" in it.title && "75%" in it.detail })
        val noRain = weatherInsights(forecast.copy(
            rainChance = null, hours = listOf(WeatherHour(appointment,21.0,null,2))
        ), listOf(task), java.time.LocalDateTime.of(2026,10,14,15,0))
        assertFalse(noRain.any { "Buscar a Rhayra" in it.title })
    }

}
