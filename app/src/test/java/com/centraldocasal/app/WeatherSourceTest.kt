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
}
