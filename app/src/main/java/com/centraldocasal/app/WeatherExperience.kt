package com.centraldocasal.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
    val tomorrowRainChance: Int? = null
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
            tomorrowRainChance = nextDayChance("precipitation_probability_max")
        )
    }
    suspend fun fetch(latitude:Double,longitude:Double):WeatherSnapshot = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val now=System.currentTimeMillis()
        val cell=weatherCacheCell(latitude,longitude)
        lastForecast?.takeIf { it.cell==cell && now-it.savedAt in 0L..CACHE_MILLIS }?.let { return@withContext it.forecast }
        val lat=String.format(Locale.US,"%.3f",latitude)
        val lon=String.format(Locale.US,"%.3f",longitude)
        val url=URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=2&timezone=auto")
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

@Composable
internal fun WeatherCard(requestLocationPermission: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Tempo onde você está",style=MaterialTheme.typography.titleMedium)
            val data=weather
            if(data!=null) {
                Text("${data.temperature.toInt()} °C · ${data.description}",style=MaterialTheme.typography.titleLarge)
                Text("Consultado às ${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(data.fetchedAtMillis))} · atualizado sob demanda",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sensação de ${data.feelsLike.toInt()} °C · Mín. ${data.todayLow.toInt()} °C · Máx. ${data.todayHigh.toInt()} °C")
                Text("Umidade ${data.humidity}% · Vento ${data.wind.toInt()} km/h" +
                    (data.rainChance?.let { " · Chance de chuva $it%" } ?: ""),style=MaterialTheme.typography.bodySmall)
                if (data.tomorrowHigh != null && data.tomorrowLow != null) {
                    HorizontalDivider()
                    Text("Amanhã", style = MaterialTheme.typography.titleSmall)
                    Text("Mín. ${data.tomorrowLow.roundToInt()} °C · Máx. ${data.tomorrowHigh.roundToInt()} °C" +
                        (data.tomorrowRainChance?.let { " · Chuva: até $it% de probabilidade" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    if ((data.tomorrowRainChance ?: 0) >= 60) {
                        Text("Pode chover amanhã. Confira a previsão antes de organizar os deslocamentos.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Consulte a previsão para sua localização, somente quando desejar.",style=MaterialTheme.typography.bodySmall)
            }
            if(message!=null) Text(message.orEmpty(),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick={
                    if(!SafetyLocationReader.permitted(context)) {
                        message="Permita a localização e toque novamente para consultar."
                        requestLocationPermission()
                    } else {
                        loading=true
                        message=null
                        SafetyLocationReader.request(context) { coordinate ->
                            if(coordinate==null || System.currentTimeMillis()-coordinate.capturedAt > 60*60*1000L) {
                                loading=false
                                message="Não encontramos uma localização recente. Ative a localização e tente novamente."
                            } else scope.launch {
                                runCatching { WeatherSource.fetch(coordinate.latitude,coordinate.longitude) }
                                    .onSuccess { weather=it }
                                    .onFailure { message="Não foi possível buscar a previsão agora. Confira sua conexão." }
                                loading=false
                            }
                        }
                    }
                },enabled=!loading) { Text(if(loading) "Consultando…" else if(data!=null) "Atualizar tempo" else "Ver previsão") }
            }
            TextButton(onClick={
                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))
            },contentPadding=PaddingValues(0.dp)) { Text("Previsão: Open-Meteo · CC BY 4.0",style=MaterialTheme.typography.labelSmall) }
            Text("A consulta envia uma localização aproximada ao Open-Meteo. Não salvamos essa localização no Firebase. Consultas da mesma região em até 15 minutos usam cache temporário em memória.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
