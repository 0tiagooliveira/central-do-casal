package com.centraldocasal.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
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

/**
 * Three evidence-based ideas shown automatically in the Home.
 *
 * Priorities: real appointment risks > next-hour weather > family preparation.
 * This is a deterministic local planner. It never creates tasks or assumes a
 * specific family activity exists. When an event is mentioned it comes from
 * an actual, authorized task supplied by the caller.
 */
internal fun dailyWeatherTips(
    forecast: WeatherSnapshot,
    tasks: List<Record> = emptyList(),
    now: LocalDateTime = LocalDateTime.now()
): List<WeatherInsight> {
    val candidates = mutableListOf<WeatherInsight>()
    val hours = forecast.hours.filter {
        !it.time.isBefore(now.minusMinutes(30)) && !it.time.isAfter(now.plusHours(24))
    }.sortedBy { it.time }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    var linkedRainHour: LocalDateTime? = null

    val appointments = tasks.asSequence()
        .filter { it.kind == "task" && !it.flag("needsReview") && !it.flag("needsTimeReview") }
        .mapNotNull { task ->
            val time = runCatching { java.time.LocalTime.parse(task.text("time")) }.getOrNull()
                ?: return@mapNotNull null
            listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))
                .firstOrNull { day -> occurs(task, day) && !day.atTime(time).isBefore(now) }
                ?.let { task to it.atTime(time) }
        }.sortedBy { it.second }.toList()

    appointments.forEach { (task, at) ->
        if (candidates.any { it.title.startsWith("No horário de ") }) return@forEach
        val hour = hours.filter { it.time.toLocalDate() == at.toLocalDate() }
            .minByOrNull { kotlin.math.abs(java.time.Duration.between(it.time, at).toMinutes()) }
            ?.takeIf { kotlin.math.abs(java.time.Duration.between(it.time, at).toMinutes()) <= 60 }
        if (hour != null) {
            val title = task.text("title").trim()
            when {
                (hour.rainChance ?: -1) >= 60 -> {
                    linkedRainHour = hour.time
                    candidates += WeatherInsight(
                        "No horário de $title",
                        "Por volta das ${at.format(timeFmt)}, há ${hour.rainChance}% de chance de chuva. Considere levar um guarda-chuva."
                    )
                }
                hour.temperature >= 29 -> candidates += WeatherInsight(
                    "No horário de $title",
                    "Por volta das ${at.format(timeFmt)}, a previsão indica ${hour.temperature.roundToInt()} °C. Prepare água para sair."
                )
                hour.temperature <= 14 -> candidates += WeatherInsight(
                    "No horário de $title",
                    "Por volta das ${at.format(timeFmt)}, a previsão indica ${hour.temperature.roundToInt()} °C. Separe um agasalho."
                )
            }
        }
    }

    val nextRain = hours.firstOrNull { (it.rainChance ?: -1) >= 60 }
    if (nextRain != null && linkedRainHour != nextRain.time) {
        candidates += WeatherInsight(
            "Chuva nas próximas horas",
            "A previsão aponta ${nextRain.rainChance}% de chance de chuva às ${nextRain.time.format(timeFmt)}. Antecipe atividades externas, se possível."
        )
    } else if(nextRain == null && (forecast.rainChance ?: -1) >= 60) {
        candidates += WeatherInsight(
            "Chuva possível hoje",
            "A maior probabilidade diária indicada é ${forecast.rainChance}%. Ainda não há horário confirmado para sugerir uma mudança na agenda."
        )
    }

    val night = hours.firstOrNull {
        it.time.toLocalDate() == now.toLocalDate() && it.time.hour in 18..23
    }
    if (night != null && night.temperature <= 16) {
        candidates += WeatherInsight(
            "Prepare a noite",
            "Perto das ${night.time.format(timeFmt)} a previsão indica ${night.temperature.roundToInt()} °C. Vale separar roupas mais quentes para a família."
        )
    }
    if (forecast.temperature >= 29 && candidates.none { it.title.startsWith("No horário de ") && it.detail.contains("água") }) {
        candidates += WeatherInsight(
            "Calor ao longo do dia",
            "Agora são ${forecast.temperature.roundToInt()} °C. Deixe água à mão e prefira horários mais frescos para sair."
        )
    } else if(forecast.feelsLike <= 13 && night == null) {
        candidates += WeatherInsight(
            "Atenção à sensação de frio",
            "A sensação está em ${forecast.feelsLike.roundToInt()} °C. Considere um agasalho para deslocamentos."
        )
    }

    val dryerHour = hours.filter {
        it.time.toLocalDate() == now.toLocalDate() && (it.rainChance ?: 101) <= 20 && it.time.hour in 7..18
    }.firstOrNull()
    if (dryerHour != null && nextRain != null) {
        candidates += WeatherInsight(
            "Uma janela para sair",
            "Às ${dryerHour.time.format(timeFmt)}, a chance de chuva prevista é de ${dryerHour.rainChance}%. Pode ser uma alternativa para tarefas externas."
        )
    }

    if (forecast.todayHigh - forecast.todayLow >= 6) {
        candidates += WeatherInsight(
            "Roupas para variação de temperatura",
            "A previsão vai de ${forecast.todayLow.roundToInt()} °C a ${forecast.todayHigh.roundToInt()} °C hoje. Uma camada extra pode ajudar nas saídas."
        )
    }
    if (candidates.size < 3 && forecast.tomorrowHigh != null && forecast.tomorrowLow != null) {
        candidates += WeatherInsight(
            "Planeje amanhã",
            "A previsão é de ${forecast.tomorrowLow.roundToInt()} °C a ${forecast.tomorrowHigh.roundToInt()} °C. Separe o necessário para os compromissos já agendados."
        )
    }
    if (candidates.size < 3) {
        candidates += WeatherInsight(
            "Conforto ao sair",
            "A sensação térmica agora é de ${forecast.feelsLike.roundToInt()} °C. Confira se a roupa está adequada ao momento."
        )
    }
    if (candidates.size < 3) {
        candidates += WeatherInsight(
            "Temperatura de hoje",
            "A mínima prevista é ${forecast.todayLow.roundToInt()} °C e a máxima ${forecast.todayHigh.roundToInt()} °C. Programe os deslocamentos considerando essa diferença."
        )
    }
    if (candidates.size < 3) {
        candidates += WeatherInsight(
            "Vento na região",
            "A velocidade atual informada é de ${forecast.wind.roundToInt()} km/h. Confira as condições antes de atividades ao ar livre."
        )
    }
    return candidates.distinctBy { it.title }.take(3)
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
    uid: String,
    profileName: String,
    records: List<Record>,
    requestNotificationsPermission: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weather by remember(uid) { mutableStateOf<WeatherSnapshot?>(null) }
    var busy by remember(uid) { mutableStateOf(false) }
    var message by remember(uid) { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable(uid) { mutableStateOf(false) }
    var enabled by remember(uid) { mutableStateOf(SophiaMoments.enabled(context, uid)) }
    var lastLocation by remember(uid) { mutableStateOf<Pair<Double,Double>?>(null) }

    // Depois da ativação inicial, somente coordenadas aproximadas já autorizadas
    // são reutilizadas. Nenhum GPS em segundo plano e nenhum botão diário.
    LaunchedEffect(uid, enabled) {
        if (enabled) {
            SophiaMoments.location(context, uid)?.let { location ->
                busy = true
                runCatching { WeatherSource.fetch(location.first, location.second) }
                    .onSuccess {
                        weather = it
                        SophiaMoments.syncTasks(context, uid, profileName, records)
                        SophiaMoments.updateForecast(context, uid, it)
                    }.onFailure {
                        message = "Sem atualização do tempo agora. Os avisos dependem de dados recentes."
                    }
                busy = false
            }
        }
    }

    fun consult() {
        if (busy) return
        if (!SafetyLocationReader.permitted(context)) {
            message = "Autorize a localização e consulte novamente. Ela não será monitorada em segundo plano."
            requestLocationPermission()
            return
        }
        busy = true
        message = null
        SafetyLocationReader.request(context) { coordinate ->
            if (coordinate == null || System.currentTimeMillis() - coordinate.capturedAt !in 0L..(60 * 60 * 1000L)) {
                busy = false
                message = "Localização recente indisponível. Confira a localização do aparelho."
            } else scope.launch {
                runCatching { WeatherSource.fetch(coordinate.latitude, coordinate.longitude) }
                    .onSuccess {
                        lastLocation = coordinate.latitude to coordinate.longitude
                        weather = it
                        if (enabled) {
                            SophiaMoments.enable(context, uid, coordinate.latitude, coordinate.longitude)
                            SophiaMoments.syncTasks(context, uid, profileName, records)
                            SophiaMoments.updateForecast(context, uid, it)
                        }
                    }
                    .onFailure { message = "Não foi possível consultar a previsão. Confira a conexão." }
                busy = false
            }
        }
    }

    val current = weather
    fun activate() {
        val location = lastLocation
        if (location == null) {
            message = "Para ativar, consulte a previsão com a localização autorizada."
            return
        }
        SophiaMoments.enable(context, uid, location.first, location.second)
        // Não fingir ativação se o usuário está no modo local ou em outra conta.
        val active = SophiaMoments.enabled(context, uid)
        enabled = active
        if (!active) {
            message = "Entre na sua conta para habilitar avisos individuais."
            return
        }
        SophiaMoments.syncTasks(context, uid, profileName, records)
        current?.let { SophiaMoments.updateForecast(context, uid, it) }
        message = if (!AndroidInteractions.notificationsAllowed(context)) {
            requestNotificationsPermission()
            "Autorize as notificações do Android para receber os avisos."
        } else null
    }
    val eligibleTasks = records.filter { it.kind == "task" && SophiaMomentPlanner.belongsTo(it, profileName) }
    val dailyTips = remember(current, records, uid, enabled) {
        if (current == null) emptyList() else dailyWeatherTips(current, eligibleTasks, LocalDateTime.now())
    }
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.tertiary
    val outline = scheme.primaryContainer
    val rainHour = current?.hours?.firstOrNull {
        it.time.isAfter(LocalDateTime.now().minusMinutes(30)) && (it.rainChance ?: -1) >= 60
    }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize().testTag("home-weather"),
        shape = RoundedCornerShape(22.dp),
        color = scheme.primaryContainer.copy(alpha = 0.46f),
        border = androidx.compose.foundation.BorderStroke(1.dp,outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("✦",color=accent,fontSize=16.sp)
                    Text("O TEMPO E NOSSA ROTINA",style=MaterialTheme.typography.labelSmall,
                        fontWeight=FontWeight.Bold,color=scheme.primary)
                }
                TextButton(onClick = { expanded = !expanded },
                    contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),
                    modifier=Modifier.heightIn(min=38.dp)) {
                    Text(if(expanded) "Fechar" else "Detalhes",style=MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Text(weatherGlyph(current?.description ?: "Condição a confirmar"),fontSize=27.sp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if (current == null) {
                        Text("Previsão ainda não consultada",style=MaterialTheme.typography.titleMedium)
                        Text("Autorize uma região para começar.",style=MaterialTheme.typography.bodySmall,
                            color=scheme.onSurfaceVariant)
                    } else {
                        Text("${current.temperature.roundToInt()} °C · ${current.description}",
                            style=MaterialTheme.typography.titleMedium,color=scheme.onSurface)
                        Text("Mín. ${current.todayLow.roundToInt()} °C · Máx. ${current.todayHigh.roundToInt()} °C",
                            style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
                    }
                }
            }
            if (current != null) {
                Text("✧ Três dicas para o dia de vocês",style=MaterialTheme.typography.labelMedium,
                    fontWeight=FontWeight.Bold,color=scheme.primary)
                dailyTips.forEachIndexed { index, tip ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Surface(shape=RoundedCornerShape(8.dp),color=scheme.surface,
                            modifier=Modifier.size(25.dp)) {
                            Box(contentAlignment=Alignment.Center) {
                                Text("${index+1}",style=MaterialTheme.typography.labelSmall,color=scheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(tip.title,style=MaterialTheme.typography.labelMedium,
                                fontWeight=FontWeight.SemiBold,color=scheme.onSurface)
                            Text(tip.detail,style=MaterialTheme.typography.bodySmall,
                                color=scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (rainHour != null) {
                Text("Possibilidade de chuva às ${rainHour.time.format(DateTimeFormatter.ofPattern("HH:mm"))}: ${rainHour.rainChance}%",
                    style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)
            }
            if (message != null) Text(message.orEmpty(),style=MaterialTheme.typography.bodySmall,
                color=scheme.error)
            if (current == null) {
                TextButton(onClick={ consult() },enabled=!busy,modifier=Modifier.heightIn(min=40.dp)) {
                    Text(if(busy) "Consultando…" else "Consultar previsão",style=MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded) {
                if (current != null) {
                    Text("Previsão das próximas horas",style=MaterialTheme.typography.titleSmall)
                    val upcoming = current.hours.filter { it.time >= LocalDateTime.now().minusMinutes(30) }.take(8)
                    if(upcoming.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(upcoming,key={it.time.toString()}) { hour ->
                            Surface(shape=RoundedCornerShape(12.dp),color=scheme.surface) {
                                Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(hour.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                        style=MaterialTheme.typography.labelSmall)
                                    Text("${hour.temperature.roundToInt()}°",style=MaterialTheme.typography.titleSmall)
                                    Text(hour.rainChance?.let { "$it% chuva" } ?: "Sem dado",
                                        style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("Dados Open-Meteo. Consultado às " +
                        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(current.fetchedAtMillis)),
                        style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)
                    TextButton(onClick={consult()},enabled=!busy) {
                        Text("Atualizar previsão")
                    }
                }
                if (current != null || enabled) Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Avisos inteligentes neste aparelho",modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodySmall)
                    Switch(checked=enabled,onCheckedChange={ next ->
                        if(next) activate() else {
                            SophiaMoments.disable(context,uid)
                            enabled=false
                        }
                    })
                }
                Text("Com sua autorização, a Central usa a região aproximada e as tarefas permitidas para avisar antes de compromissos. Sem monitorar sua localização continuamente. A Sophia generativa ainda não está conectada.",
                    style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
                TextButton(onClick={
                    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))
                }) { Text("Fonte Open-Meteo") }
            }
        }
    }
}
