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
    @Test fun parsesForecastAndTranslatesWeatherCodes(){
        val json="""{
          "current":{"temperature_2m":21.7,"apparent_temperature":20.3,"relative_humidity_2m":77,"wind_speed_10m":8.1,"weather_code":63},
          "daily":{"temperature_2m_max":[26.0],"temperature_2m_min":[14.2],"precipitation_probability_max":[82]}
        }"""
        val weather=WeatherSource.parse(json)
        assertEquals(21.7,weather.temperature,0.01)
        assertEquals(20.3,weather.feelsLike,0.01)
        assertEquals(77,weather.humidity)
        assertEquals(82,weather.rainChance)
        assertEquals("Chuva",weather.description)
        assertEquals("Trovoadas",weatherDescription(95))
    }
}
