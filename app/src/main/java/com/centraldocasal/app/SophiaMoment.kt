package com.centraldocasal.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurrenceId
import com.centraldocasal.app.domain.occurs
import com.centraldocasal.app.domain.quietAt
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Uma recomendação é vinculada a uma tarefa e a uma evidência horária, nunca a um palpite da IA. */
internal data class SophiaMoment(
    val id: String,
    val taskId: String,
    val eventAt: LocalDateTime,
    val notifyAt: LocalDateTime,
    val title: String,
    val message: String,
    val priority: Int
)

/** Domínio puro e testável: nenhuma consulta de rede, Android ou dado de outro usuário. */
internal object SophiaMomentPlanner {
    internal fun belongsTo(task: Record, profileName: String): Boolean {
        val owner = task.text("owner", "Ambos").trim()
        if (owner.equals("Ambos", ignoreCase = true)) return true
        val mine = profileName.trim()
        return mine.isNotBlank() && (owner.equals(mine, ignoreCase = true) ||
            owner.equals(mine.substringBefore(' '), ignoreCase = true))
    }

    fun plan(
        forecast: WeatherSnapshot,
        records: List<Record>,
        profileName: String,
        now: LocalDateTime
    ): List<SophiaMoment> {
        // Um dado de clima antigo não deve disparar um aviso meteorológico.
        if (System.currentTimeMillis() - forecast.fetchedAtMillis > 3 * 60 * 60 * 1000L) return emptyList()
        val done = records.filter { it.kind == "occurrence" && it.flag("done") }.map { it.id }.toSet()
        val candidates = records.asSequence()
            .filter { it.kind == "task" && belongsTo(it, profileName) &&
                !it.flag("needsReview") && !it.flag("needsTimeReview") && !it.flag("skipReminder") }
            .flatMap { task ->
                val time = runCatching { LocalTime.parse(task.text("time")) }.getOrNull()
                    ?: return@flatMap emptySequence<SophiaMoment>()
                (0L..1L).asSequence().mapNotNull { offset ->
                    val date = now.toLocalDate().plusDays(offset)
                    if (!occurs(task, date) || occurrenceId(task.id, date) in done) return@mapNotNull null
                    val event = date.atTime(time)
                    if (!event.isAfter(now.plusMinutes(10)) || event.isAfter(now.plusHours(30))) return@mapNotNull null
                    val nearest = forecast.hours.filter {
                        kotlin.math.abs(Duration.between(it.time, event).toMinutes()) <= 60
                    }.minByOrNull { kotlin.math.abs(Duration.between(it.time, event).toMinutes()) }
                        ?: return@mapNotNull null
                    val title = task.text("title").trim().take(70)
                    if (title.isEmpty()) return@mapNotNull null
                    val text = title.lowercase()
                    val outdoor = listOf("buscar", "faculdade", "ponto", "trabalho", "sair", "passeio", "mercado", "creche", "roçar", "mel", "caminhar").any { it in text }
                    val babyCare = ("lucca" in text && ("banho" in text || "roupa" in text))
                    val rain = nearest.rainChance
                    val lead: Long
                    val priority: Int
                    val message: String
                    when {
                        rain != null && rain >= 60 && outdoor -> {
                            lead = 45
                            priority = 100 + rain
                            message = "Há previsão de $rain% de chuva perto de ${time}. Prepare-se antes de $title."
                        }
                        nearest.temperature <= 14 && (outdoor || babyCare) -> {
                            lead = 60
                            priority = 85
                            message = "A previsão indica cerca de ${nearest.temperature.toInt()}°C perto de ${time}. Vale se preparar para $title."
                        }
                        nearest.temperature >= 30 && outdoor -> {
                            lead = 60
                            priority = 80
                            message = "Pode fazer cerca de ${nearest.temperature.toInt()}°C perto de ${time}. Planeje água e conforto para $title."
                        }
                        else -> return@mapNotNull null
                    }
                    val intended = event.minusMinutes(lead)
                    // Quando a previsão chega um pouco depois do momento ideal,
                    // ainda há tempo para agir até dez minutos antes do compromisso.
                    val send = if (intended.isBefore(now)) now.plusSeconds(8) else intended
                    if (!send.isBefore(event.minusMinutes(9))) return@mapNotNull null
                    SophiaMoment(
                        id = "${task.id}:${date}",
                        taskId = task.id,
                        eventAt = event,
                        notifyAt = send,
                        title = "Sophia · $title",
                        message = message,
                        priority = priority
                    )
                }
            }.toList()
        // Até 3 avisos por dia, escolhidos por relevância, com desempate determinístico.
        return candidates.groupBy { it.eventAt.toLocalDate() }.values
            .flatMap { day -> day.sortedWith(compareByDescending<SophiaMoment> { it.priority }
                .thenBy { it.eventAt }.thenBy { it.id }).take(3) }
            .sortedWith(compareBy<SophiaMoment> { it.notifyAt }.thenBy { it.id })
    }
}

/** Configuração e filas são locais e isoladas por UID. Nenhuma credencial é persistida. */
internal object SophiaMoments {
    private const val CHANNEL = "central-sophia-v1"
    private const val ACTION = "com.centraldocasal.app.SOPHIA_MOMENT"
    private fun prefs(context: Context, uid: String) =
        context.getSharedPreferences("sophia_$uid", Context.MODE_PRIVATE)
    private fun activeUid(context: Context) =
        context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).getString("activeUid", "").orEmpty()
    fun enabled(context: Context, uid: String) = uid.isNotBlank() && prefs(context, uid).getBoolean("enabled", false)
    fun location(context: Context, uid: String): Pair<Double, Double>? {
        if (!enabled(context, uid)) return null
        val p = prefs(context, uid)
        val lat = p.getString("latitude", null)?.toDoubleOrNull()
        val lon = p.getString("longitude", null)?.toDoubleOrNull()
        return if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
    }

    fun enable(context: Context, uid: String, latitude: Double, longitude: Double) {
        if (uid.isBlank() || activeUid(context) != uid) return
        val (lat, lon) = weatherRequestCoordinates(latitude, longitude)
        prefs(context, uid).edit().putString("latitude", lat).putString("longitude", lon)
            .putBoolean("enabled", true).apply()
        ensureWorker(context, uid)
    }
    fun disable(context: Context, uid: String) {
        if (uid.isBlank()) return
        cancelPending(context, uid)
        WorkManager.getInstance(context).cancelUniqueWork("sophia-weather-$uid")
        prefs(context, uid).edit().clear().apply()
    }
    fun ensureWorker(context: Context, uid: String) {
        if (!enabled(context, uid) || activeUid(context) != uid || location(context, uid) == null) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SophiaWeatherWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints).setInputData(workDataOf("uid" to uid))
            .addTag("sophia-weather-$uid").build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sophia-weather-$uid", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Cache mínimo e privado para que o worker não dependa de consultas Firestore em segundo plano. */
    fun syncTasks(context: Context, uid: String, profileName: String, records: List<Record>) {
        if (!enabled(context, uid) || activeUid(context) != uid) return
        val tasks = JSONArray()
        records.filter { it.kind == "task" && SophiaMomentPlanner.belongsTo(it, profileName) }
            .forEach { r ->
                tasks.put(JSONObject().put("id", r.id).put("title", r.text("title"))
                    .put("date", r.text("date")).put("time", r.text("time"))
                    .put("repeat", r.text("repeat")).put("owner", r.text("owner", "Ambos"))
                    .put("private", r.isPrivate).put("needsReview", r.flag("needsReview"))
                    .put("needsTimeReview", r.flag("needsTimeReview"))
                    .put("skipReminder", r.flag("skipReminder"))
                    .put("days", JSONArray(r.fields["days"] as? List<*> ?: emptyList<Int>())))
            }
        val done = JSONArray()
        records.filter { it.kind == "occurrence" && it.flag("done") }.forEach { done.put(it.id) }
        prefs(context, uid).edit().putString("tasks", tasks.toString())
            .putString("done", done.toString()).putString("profileName", profileName).apply()
        ensureWorker(context, uid)
        cachedForecast(context, uid)?.let { schedule(context, uid, profileName, records, it) }
    }
    private fun cachedRecords(context: Context, uid: String): List<Record> {
        val p = prefs(context, uid)
        val tasks = runCatching { JSONArray(p.getString("tasks", "[]")) }.getOrDefault(JSONArray())
        val records = mutableListOf<Record>()
        for (i in 0 until tasks.length()) {
            val json = tasks.optJSONObject(i) ?: continue
            val days = json.optJSONArray("days") ?: JSONArray()
            records += Record(json.optString("id"), "task", json.optBoolean("private"),
                mapOf("title" to json.optString("title"), "date" to json.optString("date"),
                    "time" to json.optString("time"), "repeat" to json.optString("repeat"),
                    "owner" to json.optString("owner"), "needsReview" to json.optBoolean("needsReview"),
                    "needsTimeReview" to json.optBoolean("needsTimeReview"),
                    "skipReminder" to json.optBoolean("skipReminder"),
                    "days" to (0 until days.length()).map { days.optInt(it) }))
        }
        val done = runCatching { JSONArray(p.getString("done", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until done.length()) records += Record(done.optString(i), "occurrence", fields=mapOf("done" to true))
        return records
    }
    fun updateForecast(context: Context, uid: String, snapshot: WeatherSnapshot) {
        if (!enabled(context, uid) || activeUid(context) != uid) return
        val json = JSONObject().put("at", snapshot.fetchedAtMillis)
            .put("temp", snapshot.temperature).put("feels", snapshot.feelsLike)
            .put("hours", JSONArray().apply {
                snapshot.hours.forEach { hour ->
                    put(JSONObject().put("at", hour.time.toString()).put("temp", hour.temperature)
                        .put("rain", hour.rainChance ?: JSONObject.NULL).put("code", hour.code ?: JSONObject.NULL))
                }
            })
        prefs(context, uid).edit().putString("forecast", json.toString()).apply()
        schedule(context, uid, prefs(context,uid).getString("profileName","").orEmpty(),
            cachedRecords(context,uid), snapshot)
    }
    private fun cachedForecast(context: Context, uid: String): WeatherSnapshot? = runCatching {
        val raw = prefs(context, uid).getString("forecast", null) ?: return null
        val json = JSONObject(raw)
        val at = json.getLong("at")
        if (System.currentTimeMillis() - at !in 0L..(3 * 60 * 60 * 1000L)) return null
        val hours = json.getJSONArray("hours")
        WeatherSnapshot(
            temperature = json.getDouble("temp"), feelsLike = json.getDouble("feels"),
            humidity = 0, wind = 0.0, todayHigh = 0.0, todayLow = 0.0,
            rainChance = null, description = "", fetchedAtMillis = at,
            hours = (0 until hours.length()).mapNotNull { i ->
                runCatching { val h = hours.getJSONObject(i)
                    WeatherHour(LocalDateTime.parse(h.getString("at")), h.getDouble("temp"),
                        if (h.isNull("rain")) null else h.getInt("rain"),
                        if (h.isNull("code")) null else h.getInt("code")) }.getOrNull()
            }
        )
    }.getOrNull()

    fun currentTip(context: Context, uid: String, profileName: String, records: List<Record>, now: LocalDateTime = LocalDateTime.now()): SophiaMoment? {
        if (!enabled(context, uid)) return null
        val forecast = cachedForecast(context, uid) ?: return null
        return SophiaMomentPlanner.plan(forecast, records, profileName, now)
            .filter { !alreadySent(context, uid, it) }
            .firstOrNull { it.eventAt <= now.plusHours(2) }
    }

    private fun deliveredSet(context: Context, uid: String, date: LocalDate): Set<String> =
        prefs(context, uid).getStringSet("sent_$date", emptySet()).orEmpty()
    private fun alreadySent(context: Context, uid: String, item: SophiaMoment) =
        item.id in deliveredSet(context, uid, item.eventAt.toLocalDate())

    private fun intent(context: Context, uid: String, id: String): Intent =
        Intent(context, SophiaAlarmReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("central-sophia://${Uri.encode(uid)}/${Uri.encode(id)}")
            putExtra("uid", uid)
            putExtra("id", id)
        }
    private fun pending(context: Context, uid: String, id: String) =
        PendingIntent.getBroadcast(context, (uid + id).hashCode(), intent(context, uid, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelPending(context: Context, uid: String) {
        val arr = runCatching { JSONArray(prefs(context, uid).getString("pending", "[]")) }.getOrDefault(JSONArray())
        val alarm = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until arr.length()) alarm?.cancel(pending(context, uid, arr.optJSONObject(i)?.optString("id").orEmpty()))
        prefs(context, uid).edit().remove("pending").remove("scheduleFingerprint").apply()
    }
    private fun toJson(item: SophiaMoment, forecastAt: Long) =
        JSONObject().put("id", item.id).put("taskId", item.taskId)
            .put("event", item.eventAt.toString()).put("notify", item.notifyAt.toString())
            .put("title", item.title).put("message", item.message)
            .put("forecastAt", forecastAt)

    private fun schedule(context: Context, uid: String, name: String, records: List<Record>, forecast: WeatherSnapshot) {
        if (name.isBlank() || !enabled(context, uid) || activeUid(context) != uid) return
        val now = LocalDateTime.now()
        val items = SophiaMomentPlanner.plan(forecast, records, name, now)
            .filterNot { alreadySent(context, uid, it) }
            .groupBy { it.eventAt.toLocalDate() }
            .values.flatMap { day ->
                val count = deliveredSet(context, uid, day.first().eventAt.toLocalDate()).size
                day.take((3 - count).coerceAtLeast(0))
            }
        val payload = JSONArray().apply { items.forEach { put(toJson(it, forecast.fetchedAtMillis)) } }
        val p = prefs(context, uid)
        // Inclui fuso e janela horária para reprogramar em mudanças relevantes.
        val fingerprint = "${ZoneId.systemDefault().id}|${payload}"
        if (p.getString("scheduleFingerprint", null) == fingerprint) return
        cancelPending(context, uid)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val scheduled = JSONArray()
        for (i in 0 until payload.length()) {
            val entry = payload.getJSONObject(i)
            val target = LocalDateTime.parse(entry.getString("notify"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (target <= System.currentTimeMillis()) continue
            val id = entry.getString("id")
            try {
                if (Build.VERSION.SDK_INT >= 31 && alarm.canScheduleExactAlarms())
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending(context, uid, id))
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending(context, uid, id))
                scheduled.put(entry)
            } catch (_: SecurityException) {
                // A ausência de autorização nunca deve quebrar a Home.
            }
        }
        p.edit().putString("pending", scheduled.toString())
            .putString("scheduleFingerprint", fingerprint).apply()
    }
    fun restore(context: Context) {
        val uid = activeUid(context)
        if (!enabled(context, uid)) return
        prefs(context, uid).edit().remove("scheduleFingerprint").apply()
        cachedForecast(context, uid)?.let { schedule(context, uid,
            prefs(context,uid).getString("profileName","").orEmpty(), cachedRecords(context,uid), it) }
        ensureWorker(context, uid)
    }

    fun deliver(context: Context, uid: String, id: String) {
        if (activeUid(context) != uid || !enabled(context, uid)) return
        val p = prefs(context, uid)
        val arr = runCatching { JSONArray(p.getString("pending", "[]")) }.getOrDefault(JSONArray())
        val entry = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("id") == id } ?: return
        val event = runCatching { LocalDateTime.parse(entry.getString("event")) }.getOrNull() ?: return
        val day = event.toLocalDate()
        val sent = deliveredSet(context, uid, day)
        if (id in sent || sent.size >= 3 || System.currentTimeMillis() - entry.optLong("forecastAt") > 3 * 60 * 60 * 1000L) return
        if (event.isBefore(LocalDateTime.now()) || quietAt(LocalTime.now().hour, LocalTime.now().minute)) return
        if (!AndroidInteractions.notificationsAllowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos inteligentes da Sophia",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos personalizados antes de compromissos, com base na previsão do tempo"
        })
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("taskId", entry.optString("taskId"))
            putExtra("taskDate", day.toString())
            putExtra("taskUid", uid)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap = PendingIntent.getActivity(context, (uid + id).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val lockScreen = context.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE)
            .getBoolean("showDetailsOnLockScreen", false)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_central).setContentTitle("Sophia")
            .setContentText("Há uma dica para seu dia. Desbloqueie para ver.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle(entry.optString("title").take(90))
            .setContentText(entry.optString("message").take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.optString("message").take(240)))
            .setContentIntent(tap).setAutoCancel(true)
            .setVisibility(if (lockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(if (lockScreen) null else publicVersion).build()
        try {
            manager.notify((uid + id).hashCode(), notification)
            p.edit().putStringSet("sent_$day", sent + id).apply()
        } catch (_: SecurityException) { /* Permissão pode ter sido revogada. */ }
    }
}

class SophiaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.centraldocasal.app.SOPHIA_MOMENT") return
        SophiaMoments.deliver(context, intent.getStringExtra("uid").orEmpty(), intent.getStringExtra("id").orEmpty())
    }
}

class SophiaWeatherWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val uid = context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE)
            .getString("activeUid", "").orEmpty()
        if (inputData.getString("uid") != uid) return Result.success()
        val location = SophiaMoments.location(context, uid) ?: return Result.success()
        return runCatching {
            SophiaMoments.updateForecast(context, uid, WeatherSource.fetch(location.first, location.second))
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
