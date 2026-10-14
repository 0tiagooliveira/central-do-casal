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

internal data class WeatherSnapshot(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val todayHigh: Double,
    val todayLow: Double,
    val rainChance: Int?,
    val description: String
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
internal object WeatherSource {
    fun parse(json: String): WeatherSnapshot {
        val root=JSONObject(json)
        if(root.optBoolean("error",false)) error("Previsão indisponível")
        val current=root.getJSONObject("current")
        val daily=root.getJSONObject("daily")
        return WeatherSnapshot(
            current.getDouble("temperature_2m"),
            current.getDouble("apparent_temperature"),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m"),
            daily.getJSONArray("temperature_2m_max").getDouble(0),
            daily.getJSONArray("temperature_2m_min").getDouble(0),
            daily.optJSONArray("precipitation_probability_max")?.takeIf { it.length()>0 && !it.isNull(0) }?.optInt(0),
            weatherDescription(current.getInt("weather_code"))
        )
    }
    suspend fun fetch(latitude:Double,longitude:Double):WeatherSnapshot = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
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
            parse(connection.inputStream.bufferedReader().use { it.readText() })
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
                Text("Sensação de ${data.feelsLike.toInt()} °C · Mín. ${data.todayLow.toInt()} °C · Máx. ${data.todayHigh.toInt()} °C")
                Text("Umidade ${data.humidity}% · Vento ${data.wind.toInt()} km/h" +
                    (data.rainChance?.let { " · Chance de chuva $it%" } ?: ""),style=MaterialTheme.typography.bodySmall)
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
            Text("A consulta envia uma localização aproximada ao Open-Meteo. Não salvamos essa localização no Firebase.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
