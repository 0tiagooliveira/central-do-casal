package com.centraldocasal.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurs
import java.time.LocalDate
import java.time.LocalDateTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val tomorrowRainChance: Int? = null,
    val hours: List<WeatherHour> = emptyList(),
    val days: List<WeatherDay> = emptyList()
)

internal data class WeatherHour(val time: LocalDateTime, val temperature: Double, val rainChance: Int?, val code: Int?)
internal data class WeatherDay(val day: LocalDate, val high: Double, val low: Double, val rainChance: Int?)

internal data class WeatherInsight(val title: String, val detail: String)

/**
 * Orientações locais, determinísticas e transparentes. Não é uma resposta
 * gerada por um modelo remoto: não inventa dado, horário ou percentual.
 * Em futura integração com Sophia, só insumos meteorológicos válidos e
 * atividades explicitamente autorizadas poderão entrar no prompt.
 */
internal fun weatherInsights(
    forecast: WeatherSnapshot,
    tasks: List<Record> = emptyList(),
    now: LocalDateTime = LocalDateTime.now()
): List<WeatherInsight> {
    val upcoming = forecast.hours.filter { it.time >= now.minusMinutes(30) && it.time <= now.plusHours(10) }
    val risks = mutableListOf<WeatherInsight>()
    val nextRain = upcoming.firstOrNull { (it.rainChance ?: -1) >= 60 }
    if (nextRain != null) {
        val hour = nextRain.time.format(DateTimeFormatter.ofPattern("HH:mm"))
        risks += WeatherInsight(
            "Chuva prevista por volta das $hour",
            "Probabilidade de ${nextRain.rainChance}% para aquela hora. Considere um guarda-chuva ao sair."
        )
    } else if ((forecast.rainChance ?: -1) >= 60) {
        risks += WeatherInsight(
            "Vale acompanhar a chuva de hoje",
            "A probabilidade máxima diária prevista é de ${forecast.rainChance}%. O horário ainda precisa ser confirmado."
        )
    }
    val linkedTask = tasks.asSequence()
        .filter { it.kind == "task" && !it.flag("needsReview") && !it.flag("needsTimeReview") }
        .mapNotNull { task ->
            val time = runCatching { java.time.LocalTime.parse(task.text("time")) }.getOrNull()
            if (time == null) null
            else {
                val dates = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))
                dates.map { date -> date.atTime(time) }
                    .firstOrNull { it >= now && occurs(task, it.toLocalDate()) }
                    ?.let { task to it }
            }
        }
        .mapNotNull { (task, date) ->
            forecast.hours.firstOrNull { it.time.toLocalDate() == date.toLocalDate()
                && kotlin.math.abs(java.time.Duration.between(it.time, date).toMinutes()) <= 60
                && (it.rainChance ?: 0) >= 60 }?.let { hour ->
                WeatherInsight(
                    "Atenção ao horário de ${task.text("title")}",
                    "Para ${date.format(DateTimeFormatter.ofPattern("HH:mm"))}, há previsão de chuva de ${hour.rainChance}% perto desse horário."
                )
            }
        }
        .firstOrNull()
    if (linkedTask != null) risks.add(0, linkedTask)
    if (forecast.temperature >= 30) {
        risks += WeatherInsight("Dia quente", "Temperatura atual de ${forecast.temperature.roundToInt()} °C. Planeje água e pausas nos deslocamentos.")
    } else if (forecast.feelsLike <= 12) {
        risks += WeatherInsight("Sensação de frio", "Sensação atual de ${forecast.feelsLike.roundToInt()} °C. Vale separar um agasalho.")
    }
    if (risks.isEmpty()) {
        risks += WeatherInsight("Clima em acompanhamento", "Nenhum alerta contextual foi identificado nos dados disponíveis. Confira a previsão antes de sair.")
    }
    return risks.distinctBy { it.title }.take(3)
}

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
        val hourly = root.optJSONObject("hourly")
        val hourTimes = hourly?.optJSONArray("time")
        val hourTemp = hourly?.optJSONArray("temperature_2m")
        val hourRain = hourly?.optJSONArray("precipitation_probability")
        val hourCodes = hourly?.optJSONArray("weather_code")
        val hours = if (hourTimes == null || hourTemp == null) emptyList() else
            (0 until minOf(hourTimes.length(), hourTemp.length())).mapNotNull { index ->
                runCatching {
                    WeatherHour(
                        LocalDateTime.parse(hourTimes.getString(index)),
                        hourTemp.getDouble(index),
                        hourRain?.takeUnless { index >= it.length() || it.isNull(index) }?.getInt(index),
                        hourCodes?.takeUnless { index >= it.length() || it.isNull(index) }?.getInt(index)
                    )
                }.getOrNull()
            }
        val dates = daily.optJSONArray("time")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rains = daily.optJSONArray("precipitation_probability_max")
        val days = if (dates == null) emptyList() else
            (0 until minOf(dates.length(), highs.length(), lows.length(), 4)).mapNotNull { index ->
                runCatching {
                    WeatherDay(
                        LocalDate.parse(dates.getString(index)),
                        highs.getDouble(index),
                        lows.getDouble(index),
                        rains?.takeUnless { index >= it.length() || it.isNull(index) }?.getInt(index)
                    )
                }.getOrNull()
            }
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
            hours = hours,
            days = days
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
        val url=URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code&hourly=temperature_2m,precipitation_probability,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=4&timezone=auto")
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

private fun weatherGlyph(description: String): String = when {
    "Trovoadas" in description -> "⛈️"
    "Chuva" in description || "Pancadas" in description || "Garoa" in description -> "🌧️"
    "Nevoeiro" in description -> "🌫️"
    "Nublado" in description -> "☁️"
    "Parcialmente" in description -> "⛅"
    else -> "☀️"
}

@Composable
internal fun WeatherCard(
    requestLocationPermission: () -> Unit,
    tasks: List<Record> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = weather
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("O TEMPO PARA A NOSSA ROTINA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(if (current == null) "Como está o tempo?" else "Previsão na sua região",
                        style = MaterialTheme.typography.titleLarge)
                }
                Text(if (current == null) "🌤️" else weatherGlyph(current.description),
                    fontSize = 38.sp)
            }
            if (current == null) {
                Text("Veja a temperatura, a chuva das próximas horas e como isso pode afetar os planos de vocês.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${current.temperature.roundToInt()}°",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold)
                    Column {
                        Text(current.description, style = MaterialTheme.typography.titleMedium)
                        Text("Sensação ${current.feelsLike.roundToInt()}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Hoje: ${current.todayLow.roundToInt()}° / ${current.todayHigh.roundToInt()}°",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                val insights = remember(current, tasks) { weatherInsights(current, tasks) }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦  Leitura inteligente do tempo",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        insights.take(if (expanded) 3 else 1).forEach { insight ->
                            Text(insight.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Text(insight.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Sugestões automáticas baseadas em dados meteorológicos e horários da rotina, não em IA generativa.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(if (expanded) "Mostrar menos" else "Ver previsão por hora e próximos dias")
                }
                if (expanded) {
                val upcoming = current.hours.filter { it.time >= LocalDateTime.now().minusMinutes(30) }.take(9)
                if (upcoming.isNotEmpty()) {
                    Text("Próximas horas", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(upcoming, key = { it.time.toString() }) { hour ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.width(84.dp)
                            ) {
                                Column(
                                    Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(hour.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                        style = MaterialTheme.typography.labelSmall)
                                    Text(weatherGlyph(weatherDescription(hour.code ?: -1)), fontSize = 24.sp)
                                    Text("${hour.temperature.roundToInt()}°",
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(hour.rainChance?.let { "💧 $it%" } ?: "Chuva s/d",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                val nextDays = current.days.drop(1).take(3)
                if (nextDays.isNotEmpty()) {
                    Text("Próximos dias", style = MaterialTheme.typography.titleMedium)
                    nextDays.forEach { day ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.day.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR")))
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text(day.rainChance?.let { "💧 $it%" } ?: "Chuva s/d",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(14.dp))
                            Text("${day.low.roundToInt()}°  ${day.high.roundToInt()}°",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (current.tomorrowHigh != null && current.tomorrowLow != null) {
                    Text("Amanhã: ${current.tomorrowLow.roundToInt()}° a ${current.tomorrowHigh.roundToInt()}°",
                        style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                Text("${current.humidity}% de umidade  ·  vento ${current.wind.roundToInt()} km/h",
                    style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Consultado às ${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(current.fetchedAtMillis))}. Previsões podem mudar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message != null) {
                Text(message.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (!SafetyLocationReader.permitted(context)) {
                        message = "Permita a localização e toque novamente para consultar."
                        requestLocationPermission()
                    } else {
                        loading = true
                        message = null
                        SafetyLocationReader.request(context) { coordinate ->
                            if (coordinate == null || System.currentTimeMillis() - coordinate.capturedAt > 60 * 60 * 1000L) {
                                loading = false
                                message = "Não encontramos uma localização recente. Ative a localização e tente novamente."
                            } else scope.launch {
                                runCatching { WeatherSource.fetch(coordinate.latitude, coordinate.longitude) }
                                    .onSuccess { weather = it }
                                    .onFailure { message = "Não foi possível buscar a previsão agora. Confira sua conexão." }
                                loading = false
                            }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (loading) "Consultando…" else if (current != null) "Atualizar previsão" else "Ver previsão para mim")
            }
            TextButton(
                onClick = { showPrivacy = !showPrivacy },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (showPrivacy) "Ocultar fonte e privacidade" else "Fonte dos dados e privacidade") }
            if (showPrivacy) {
                Text("Fonte: Open-Meteo (CC BY 4.0). A localização é consultada somente ao tocar no botão e enviada com coordenadas arredondadas a duas casas decimais. Os dados desta consulta não são gravados no Firebase e o cache em memória dura até 15 minutos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) }
                ) { Text("Abrir Open-Meteo") }
            }
        }
    }
}
