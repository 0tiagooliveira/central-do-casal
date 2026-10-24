package com.centraldocasal.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local hours returned by Open-Meteo timezone=auto, never guessed from daily highs. */
internal data class HourlyWeather(val time: LocalDateTime, val temperature: Double?, val rainChance: Int?)

internal data class WeatherSnapshot(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val todayHigh: Double,
    val todayLow: Double,
    val rainChance: Int?,
    val description: String,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val tomorrowHigh: Double? = null,
    val tomorrowLow: Double? = null,
    val tomorrowRainChance: Int? = null,
    val hours: List<HourlyWeather> = emptyList()
)

internal fun weatherDescription(code: Int): String = when(code) {
    0 -> "Céu limpo"
    1,2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45,48 -> "Nevoeiro"
    51,53,55,56,57 -> "Garoa"
    61,63,65,66,67 -> "Chuva"
    71,73,75,77 -> "Neve"
    80,81,82 -> "Pancadas de chuva"
    85,86 -> "Pancadas de neve"
    95,96,99 -> "Trovoadas"
    else -> "Condição a confirmar"
}

/** Open-Meteo forecast API: data CC BY 4.0, coordinates sent only after explicit user action. */
internal fun weatherCacheCell(latitude: Double, longitude: Double): Pair<Int,Int> =
    (latitude * 100).roundToInt() to (longitude * 100).roundToInt()

/** O servidor recebe só uma célula aproximada, nunca a coordenada GPS bruta. */
internal fun weatherRequestCoordinates(latitude: Double, longitude: Double): Pair<String,String> {
    val cell = weatherCacheCell(latitude,longitude)
    return String.format(Locale.US,"%.2f",cell.first / 100.0) to
        String.format(Locale.US,"%.2f",cell.second / 100.0)
}

internal object WeatherSource {
    private data class Cache(val cell: Pair<Int,Int>, val savedAt: Long, val forecast: WeatherSnapshot)
    @Volatile private var lastForecast: Cache? = null
    private const val CACHE_MILLIS = 15 * 60 * 1000L

    fun parse(json: String): WeatherSnapshot {
        val root=JSONObject(json)
        if(root.optBoolean("error",false)) error("Previsão indisponível")
        val current=root.getJSONObject("current")
        val daily=root.getJSONObject("daily")
        fun nextDayDouble(field: String): Double? = daily.optJSONArray(field)
            ?.takeIf { it.length() > 1 && !it.isNull(1) }
            ?.getDouble(1)
        fun nextDayChance(field: String): Int? = daily.optJSONArray(field)
            ?.takeIf { it.length() > 1 && !it.isNull(1) }
            ?.getInt(1)
        val hours = root.optJSONObject("hourly")?.let { hourly ->
            val times = hourly.optJSONArray("time")
            val temperatures = hourly.optJSONArray("temperature_2m")
            val rain = hourly.optJSONArray("precipitation_probability")
            (0 until minOf(times?.length() ?: 0, 48)).mapNotNull { index ->
                val time = runCatching { LocalDateTime.parse(times!!.getString(index)) }.getOrNull()
                    ?: return@mapNotNull null
                HourlyWeather(
                    time = time,
                    temperature = temperatures?.takeIf { index < it.length() && !it.isNull(index) }
                        ?.optDouble(index)?.takeIf { !it.isNaN() },
                    rainChance = rain?.takeIf { index < it.length() && !it.isNull(index) }
                        ?.optInt(index)?.takeIf { it in 0..100 }
                )
            }
        } ?: emptyList()
        return WeatherSnapshot(
            current.getDouble("temperature_2m"),
            current.getDouble("apparent_temperature"),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m"),
            daily.getJSONArray("temperature_2m_max").getDouble(0),
            daily.getJSONArray("temperature_2m_min").getDouble(0),
            daily.optJSONArray("precipitation_probability_max")?.takeIf { it.length()>0 && !it.isNull(0) }?.optInt(0),
            weatherDescription(current.getInt("weather_code")),
            tomorrowHigh = nextDayDouble("temperature_2m_max"),
            tomorrowLow = nextDayDouble("temperature_2m_min"),
            tomorrowRainChance = nextDayChance("precipitation_probability_max"),
            hours = hours
        )
    }
    suspend fun fetch(latitude:Double,longitude:Double):WeatherSnapshot = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val now=System.currentTimeMillis()
        val cell=weatherCacheCell(latitude,longitude)
        lastForecast?.takeIf { it.cell==cell && now-it.savedAt in 0L..CACHE_MILLIS }?.let { return@withContext it.forecast }
        // A previsão não precisa de coordenadas de ~100 metros; usamos uma
        // célula de ~1 km, igual à chave do cache, minimizando dados enviados.
        val (lat,lon)=weatherRequestCoordinates(latitude,longitude)
        val url=URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&hourly=temperature_2m,precipitation_probability&forecast_days=2&timezone=auto")
        val connection=url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=7000
            connection.readTimeout=7000
            connection.requestMethod="GET"
            connection.setRequestProperty("Accept","application/json")
            if(connection.responseCode!=200)error("Resposta de previsão: ${connection.responseCode}")
            val snapshot=parse(connection.inputStream.bufferedReader().use { it.readText() })
            lastForecast=Cache(cell,System.currentTimeMillis(),snapshot)
            snapshot
        } finally { connection.disconnect() }
    }
}


/** Tips only from current real forecast and records visible in this authenticated profile. */
internal fun contextualWeatherTips(weather: WeatherSnapshot, records: List<Record>, day: LocalDate): List<String> {
    val tasks = records.filter {
        it.kind == "task" && !it.flag("needsReview") && !it.flag("needsTimeReview") &&
            it.text("time").isNotBlank() && occurs(it, day)
    }.sortedBy { it.text("time") }
    val tips = mutableListOf<String>()
    val next = tasks.firstOrNull { runCatching { LocalTime.parse(it.text("time")) }.isSuccess }
    val hourForecast = next?.let { task ->
        val time = runCatching { LocalTime.parse(task.text("time")) }.getOrNull()
        weather.hours.firstOrNull { it.time.toLocalDate() == day && it.time.hour == time?.hour }
    }
    if(next != null && hourForecast?.rainChance != null) {
        val chance = hourForecast.rainChance
        tips += if(chance >= 60) {
            "Para ${next.text("title")} às ${next.text("time")}, a previsão indica ${chance}% de chance de chuva nessa hora. Confiram o trajeto."
        } else {
            "Para ${next.text("title")} às ${next.text("time")}, a chance de chuva prevista nessa hora é ${chance}%."
        }
    } else if ((weather.rainChance ?: 0) >= 60) {
        val chance = weather.rainChance
        tips += if(next == null) "Hoje a chance máxima de chuva é ${chance}%. Confiram guarda-chuva e deslocamentos antes de sair."
            else "Hoje a chance máxima de chuva é ${chance}%, mas não temos previsão para a hora de ${next.text("title")}. Confiram o trajeto."
    } else if (next != null) {
        tips += "A rotina tem ${next.text("title")} às ${next.text("time")}. A previsão de hoje indica máxima de ${weather.todayHigh.roundToInt()} °C."
    }
    if ((weather.tomorrowRainChance ?: 0) >= 60) {
        tips += "Amanhã a chance máxima de chuva chega a ${weather.tomorrowRainChance}%. Planejem o trajeto com antecedência."
    } else if(weather.tomorrowLow != null && weather.tomorrowHigh != null) {
        tips += "Amanhã a previsão varia de ${weather.tomorrowLow.roundToInt()} °C a ${weather.tomorrowHigh.roundToInt()} °C. Vale separar o que levar antes de sair."
    }
    if(weather.feelsLike <= 15.0) {
        tips += "Sensação atual de ${weather.feelsLike.roundToInt()} °C. Confiram os agasalhos antes de sair com o Lucca."
    } else if(weather.feelsLike >= 29.0) {
        tips += "Sensação atual de ${weather.feelsLike.roundToInt()} °C. Considerem água e pausas nos passeios com o Lucca."
    } else {
        tips += "Sensação atual de ${weather.feelsLike.roundToInt()} °C e umidade de ${weather.humidity}%. Ajustem os itens da saída ao clima."
    }
    if(tips.size < 3) {
        tips += "A mínima de hoje é ${weather.todayLow.roundToInt()} °C e a máxima ${weather.todayHigh.roundToInt()} °C. Revejam roupas para os horários de saída."
    }
    if(tips.size < 3) {
        tips += "Vento informado: ${weather.wind.roundToInt()} km/h. Confiram os objetos leves nas áreas externas."
    }
    return tips.distinct().take(3)
}

@Composable
internal fun WeatherCard(requestLocationPermission: () -> Unit, profileUid: String = "", records: List<Record> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val prefs = remember(profileUid) { context.getSharedPreferences("weather_${profileUid}",Context.MODE_PRIVATE) }
    var automatic by remember(profileUid) { mutableStateOf(prefs.getBoolean("autoWeather", false)) }

    fun refresh() {
        if(loading) return
        if(!SafetyLocationReader.permitted(context)) {
            message = "Autorize a localização para consultar o tempo."
            return
        }
        loading = true
        message = null
        SafetyLocationReader.request(context) { loc ->
            if(loc == null || System.currentTimeMillis() - loc.capturedAt !in 0L..3_600_000L) {
                message = "Localização recente indisponível. Confira o GPS."
                loading = false
            } else scope.launch {
                runCatching { WeatherSource.fetch(loc.latitude, loc.longitude) }
                    .onSuccess { weather = it }
                    .onFailure { message = "Não foi possível consultar a previsão. Confira a conexão." }
                loading = false
            }
        }
    }
    LaunchedEffect(automatic, profileUid) {
        while(automatic && profileUid.isNotBlank() && isActive) {
            if(SafetyLocationReader.permitted(context)) refresh()
            delay(15*60*1000L)
        }
    }

    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Tempo onde você está",style=MaterialTheme.typography.titleMedium)
            val data = weather
            if(data!=null) {
                Text("${data.temperature.roundToInt()} °C · ${data.description}",style=MaterialTheme.typography.titleLarge)
                Text("Consultado às ${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(data.fetchedAtMillis))}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sensação ${data.feelsLike.roundToInt()} °C · Mín. ${data.todayLow.roundToInt()} °C · Máx. ${data.todayHigh.roundToInt()} °C")
                Text("Umidade ${data.humidity}% · Vento ${data.wind.roundToInt()} km/h" + (data.rainChance?.let { " · Chuva: até ${it}%" } ?: ""),style=MaterialTheme.typography.bodySmall)
                if(data.hours.isNotEmpty()) {
                    val upcoming=data.hours.filter { it.time.toLocalDate()==LocalDate.now() &&
                        !it.time.isBefore(LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)) }.take(3)
                    if(upcoming.isNotEmpty()) {
                        Text("Nas próximas horas",style=MaterialTheme.typography.labelLarge)
                        Text(upcoming.joinToString(" · ") { h ->
                            "${h.time.format(DateTimeFormatter.ofPattern("HH'h'"))}: " +
                                (h.temperature?.let { "${it.roundToInt()} °C" } ?: "temperatura indisponível") +
                                (h.rainChance?.let { " · chuva ${it}%" } ?: "")
                        },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Três dicas para a família",style=MaterialTheme.typography.titleSmall)
                contextualWeatherTips(data,records,LocalDate.now()).forEachIndexed { index,tip ->
                    Text("${index + 1}. $tip", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Previsão e dicas aparecerão após uma consulta com a localização permitida.",style=MaterialTheme.typography.bodySmall)
            }
            if(message!=null) Text(message.orEmpty(),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Switch(checked=automatic,onCheckedChange={
                    automatic=it
                    prefs.edit().putBoolean("autoWeather",it).apply()
                })
                Text("Atualizar automaticamente ao abrir Hoje (opcional)",style=MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick={
                if(!SafetyLocationReader.permitted(context)) {
                    message="Permita a localização e toque novamente para consultar."
                    requestLocationPermission()
                } else refresh()
            },enabled=!loading) { Text(if(loading) "Consultando…" else if(data!=null) "Atualizar tempo" else "Ver previsão") }
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))},contentPadding=PaddingValues(0.dp)) {
                Text("Previsão: Open-Meteo · CC BY 4.0",style=MaterialTheme.typography.labelSmall)
            }
            Text("Enviamos coordenadas aproximadas ao Open-Meteo. Não salvamos a posição no Firebase. Cache em memória por 15 minutos.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
