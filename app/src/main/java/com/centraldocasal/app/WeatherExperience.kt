package com.centraldocasal.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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

    fun lastKnown(): WeatherSnapshot? = lastForecast?.takeIf { System.currentTimeMillis()-it.savedAt in 0L..CACHE_MILLIS }?.forecast

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
        // A previsão não precisa de coordenadas de ~100 metros; usamos uma
        // célula de ~1 km, igual à chave do cache, minimizando dados enviados.
        val (lat,lon)=weatherRequestCoordinates(latitude,longitude)
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
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(SafetyLocationReader.permitted(context)) }
    var weather by remember { mutableStateOf(WeatherSource.lastKnown()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = SafetyLocationReader.permitted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    suspend fun obtainWeather() {
        if (!SafetyLocationReader.permitted(context) || loading) return
        loading = true
        error = null
        try {
            val coordinate = suspendCancellableCoroutine<SafetyLocation?> { continuation ->
                SafetyLocationReader.request(context) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
            val current = coordinate?.takeIf { point ->
                System.currentTimeMillis() - point.capturedAt in 0L..3_600_000L
            }
            if (current == null) {
                error = "Não foi possível localizar este aparelho. Confira a permissão e o GPS."
            } else {
                weather = WeatherSource.fetch(current.latitude, current.longitude)
            }
        } catch (_: Exception) {
            error = "Previsão temporariamente indisponível. Toque para tentar de novo."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(allowed) {
        if (allowed && WeatherSource.lastKnown() == null) obtainWeather()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .40f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("PREVISÃO DE HOJE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val data = weather
                    if (data != null) {
                        Text("${data.temperature.roundToInt()}° · ${data.description}",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Mín. ${data.todayLow.roundToInt()}°   Máx. ${data.todayHigh.roundToInt()}°" +
                            (data.rainChance?.let { "   Chuva $it%" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                    } else if (loading) {
                        Text("Buscando o tempo…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(if (allowed) "Tempo indisponível" else "Veja o tempo da sua região",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!allowed) {
                    TextButton(onClick = requestLocationPermission) { Text("Ativar") }
                } else {
                    TextButton(onClick = { scope.launch { obtainWeather() } }, enabled = !loading) {
                        Text(if (loading) "…" else "Atualizar")
                    }
                }
            }
            if (error != null) Text(error.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (weather != null && (weather?.tomorrowRainChance ?: 0) >= 60) {
                Text("Amanhã pode chover. Vale conferir antes de sair.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
