package com.centraldocasal.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.centraldocasal.app.domain.*
import com.google.firebase.auth.FirebaseAuth
import java.time.*
import java.util.concurrent.TimeUnit

object Reminders {
    fun schedule(context: Context, uid: String, record: Record) {
        cancelTask(context, uid, record.id)
        if (record.flag("skipReminder") || record.flag("needsReview")) return
        if (record.text("time").isBlank() || !context.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE).getBoolean("enabled", true)) return
        val currentName = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
        if (uid != "local" && record.text("owner") !in listOf("Ambos", currentName)) return
        val time = runCatching { LocalTime.parse(record.text("time")) }.getOrNull() ?: return
        val start = runCatching { LocalDate.parse(record.text("date")) }.getOrNull() ?: return
        val today = maxOf(LocalDate.now(), start)
        val next = (0..366).asSequence().map { today.plusDays(it.toLong()) }.firstOrNull { date -> occurs(record, date) && date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now()) } ?: return
        val days = (record.fields["days"] as? List<*>)?.filterIsInstance<Number>()?.filter { it.toDouble() in 1.0..7.0 && it.toDouble() == it.toInt().toDouble() }?.map { it.toInt() }?.toIntArray() ?: intArrayOf()
        enqueue(context, uid, record.id, next, time, record.text("repeat"), start = start, days = days)
    }
    fun enqueue(context: Context, uid: String, id: String, date: LocalDate, time: LocalTime, repeat: String, quietDelay: Boolean = false, start: LocalDate = date, days: IntArray = intArrayOf()) {
        val now = Instant.now(); var target = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
        if (quietDelay) target = LocalDate.now().atTime(5, 40).atZone(ZoneId.systemDefault()).toInstant()
        val input = workDataOf("uid" to uid, "id" to id, "date" to date.toString(), "time" to time.toString(), "repeat" to repeat, "start" to start.toString(), "days" to days)
        val work = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(input).setInitialDelay(java.time.Duration.between(now, target).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS).addTag("central-reminders-$uid").addTag("task-$uid-$id").build()
        WorkManager.getInstance(context).enqueueUniqueWork("task-$uid-$id:$date", ExistingWorkPolicy.REPLACE, work)
    }
    fun cancel(context: Context, uid: String, id: String, day: LocalDate) { WorkManager.getInstance(context).cancelUniqueWork("task-$uid-$id:$day") }
    fun cancelTask(context: Context, uid: String, id: String) { WorkManager.getInstance(context).cancelAllWorkByTag("task-$uid-$id") }
    fun cancelAll(context: Context, uid: String) { WorkManager.getInstance(context).cancelAllWorkByTag("central-reminders-$uid") }
}
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.success()
        val activeUid = applicationContext.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE).getString("activeUid", "")
        if (activeUid != uid) return Result.success()
        val prefs = applicationContext.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val date = LocalDate.parse(inputData.getString("date")); val time = LocalTime.parse(inputData.getString("time")); val repeat = inputData.getString("repeat").orEmpty()
        val start = inputData.getString("start")?.let(LocalDate::parse) ?: date
        val days = inputData.getIntArray("days") ?: intArrayOf()
        val now = LocalTime.now()
        if (prefs.getBoolean("quiet", true) && quietAt(now.hour, now.minute)) { Reminders.enqueue(applicationContext, uid, id, date, time, repeat, quietDelay = true, start = start, days = days); return Result.success() }
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("personal", "Lembretes pessoais", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = Intent(applicationContext, MainActivity::class.java).putExtra("taskId", id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tap = PendingIntent.getActivity(applicationContext, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notice = NotificationCompat.Builder(applicationContext, "personal").setSmallIcon(com.centraldocasal.app.R.drawable.ic_central).setContentTitle("Central do Casal").setContentText("Você tem um lembrete para conferir.").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(tap).setAutoCancel(true).build()
            manager.notify("$uid:$id".hashCode(), notice)
        }
        if (repeat in listOf("Diária", "Semanal", "Dias da semana", "Mensal", "Primeiro domingo após dia 8")) {
            val task = Record(id = id, kind = "task", fields = mapOf("date" to start.toString(), "repeat" to repeat, "days" to days.toList()))
            val first = maxOf(date.plusDays(1), LocalDate.now())
            val next = (0..366).asSequence().map { first.plusDays(it.toLong()) }.firstOrNull { occurs(task, it) && it.atTime(time).atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now()) }
            if (next != null) Reminders.enqueue(applicationContext, uid, id, next, time, repeat, start = start, days = days)
        }
        return Result.success()
    }
}
