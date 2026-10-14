package com.centraldocasal.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.centraldocasal.app.domain.Record
import com.centraldocasal.app.domain.occurs
import com.centraldocasal.app.domain.quietAt
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** User-initiated task notifications use Android alarms, not inexact background jobs. */
object Reminders {
    private const val CHANNEL = "central-tasks-v2"
    private const val ACTION = "com.centraldocasal.app.TASK_ALARM"
    private const val CACHE = "central_task_alarm_cache"
    private const val MIGRATION = "alarm_migrated_v2"
    private const val ID_LIST = "tasks"

    private fun store(context: Context) = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
    private fun key(uid: String, id: String) = "record_$uid:$id"
    private fun alarmIntent(context: Context, uid: String, id: String) =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("central-reminder://task/${Uri.encode(uid)}/${Uri.encode(id)}")
            putExtra("uid", uid)
            putExtra("id", id)
        }

    private fun pending(context: Context, uid: String, id: String) =
        PendingIntent.getBroadcast(context, 0, alarmIntent(context, uid, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun prefs(context: Context, uid: String) =
        context.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE)

    private fun belongs(record: Record, profileName: String): Boolean {
        val owner = record.text("owner", "Ambos").trim()
        if (owner.equals("Ambos", true)) return true
        val mine = profileName.trim().lowercase()
        if (mine.isEmpty()) return false
        return owner.lowercase() == mine || owner.substringBefore(' ').equals(mine.substringBefore(' '), true)
    }

    private fun cache(context: Context, uid: String, record: Record) {
        val p = store(context)
        val names = p.getStringSet("$ID_LIST:$uid", emptySet()).orEmpty().toMutableSet()
        names.add(record.id)
        val data = JSONObject()
            .put("id", record.id)
            .put("title", record.text("title"))
            .put("note", record.text("note"))
            .put("owner", record.text("owner", "Ambos"))
            .put("date", record.text("date"))
            .put("time", record.text("time"))
            .put("repeat", record.text("repeat"))
            .put("isPrivate", record.isPrivate)
            .put("skipReminder", record.flag("skipReminder"))
            .put("needsReview", record.flag("needsReview"))
            .put("needsTimeReview", record.flag("needsTimeReview"))
            .put("days", JSONArray(record.fields["days"] as? List<*> ?: emptyList<Int>()))
        p.edit().putString(key(uid, record.id), data.toString()).putStringSet("$ID_LIST:$uid", names).apply()
    }

    private fun forget(context: Context, uid: String, id: String) {
        val p = store(context)
        val names = p.getStringSet("$ID_LIST:$uid", emptySet()).orEmpty().toMutableSet()
        names.remove(id)
        p.edit().remove(key(uid,id)).putStringSet("$ID_LIST:$uid", names).apply()
    }

    private fun read(context: Context, uid: String, id: String): Record? =
        runCatching {
            val json = JSONObject(store(context).getString(key(uid,id), null) ?: return null)
            val array = json.optJSONArray("days")
            val days = (0 until (array?.length() ?: 0)).map { array!!.optInt(it) }
            Record(id=id,kind="task",isPrivate=json.optBoolean("isPrivate",false),fields=mapOf(
                "title" to json.optString("title"),
                "note" to json.optString("note"),
                "owner" to json.optString("owner","Ambos"),
                "date" to json.optString("date"),
                "time" to json.optString("time"),
                "repeat" to json.optString("repeat"),
                "skipReminder" to json.optBoolean("skipReminder",false),
                "needsReview" to json.optBoolean("needsReview",false),
                "needsTimeReview" to json.optBoolean("needsTimeReview",false),
                "days" to days
            ))
        }.getOrNull()

    fun schedule(context: Context, uid: String, record: Record, profileName: String = "", completedDay: LocalDate? = null) {
        if(uid.isBlank() || record.kind != "task") return
        val pref=prefs(context,uid)
        if(profileName.isNotBlank()) pref.edit().putString("profileName",profileName).apply()
        val name=profileName.ifBlank { pref.getString("profileName","").orEmpty() }
        cancelAlarm(context,uid,record.id)
        if(record.flag("skipReminder") || record.flag("needsReview") || record.flag("needsTimeReview") ||
           !pref.getBoolean("enabled",true) || !belongs(record,name)) {
            forget(context,uid,record.id)
            return
        }
        val time=runCatching { LocalTime.parse(record.text("time")) }.getOrNull()
        val start=runCatching { LocalDate.parse(record.text("date")) }.getOrNull()
        if(time==null || start==null) { forget(context,uid,record.id);return }
        val today=maxOf(LocalDate.now(),start)
        val upcoming=(0..366).asSequence().map { today.plusDays(it.toLong()) }.firstOrNull {
            it!=completedDay && occurs(record,it) &&
                it.atTime(time).atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now())
        }
        if(upcoming==null){forget(context,uid,record.id);return}
        cache(context,uid,record)
        plan(context,uid,record.id,upcoming,time)
    }

    private fun plan(context:Context,uid:String,id:String,date:LocalDate,time:LocalTime) {
        val manager=context.getSystemService(AlarmManager::class.java) ?: return
        var instant=date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
        val p=prefs(context,uid)
        if(p.getBoolean("quiet",true)){
            val local=instant.atZone(ZoneId.systemDefault())
            if(quietAt(local.hour,local.minute)) {
                instant=if(local.toLocalTime()<LocalTime.of(5,40))
                    date.atTime(5,40).atZone(ZoneId.systemDefault()).toInstant()
                else date.plusDays(1).atTime(5,40).atZone(ZoneId.systemDefault()).toInstant()
            }
        }
        val whenMillis=instant.toEpochMilli()
        if(whenMillis<=System.currentTimeMillis())return
        try {
            if(Build.VERSION.SDK_INT>=31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMillis,pending(context,uid,id))
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMillis,pending(context,uid,id))
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMillis,pending(context,uid,id))
        }
    }

    private fun cancelAlarm(context:Context,uid:String,id:String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context,uid,id))
    }
    fun cancel(context:Context,uid:String,id:String,day:LocalDate) {
        cancelAlarm(context,uid,id)
        read(context,uid,id)?.let { schedule(context,uid,it,completedDay=day) }
    }
    fun cancelTask(context:Context,uid:String,id:String) {
        cancelAlarm(context,uid,id)
        forget(context,uid,id)
        WorkManager.getInstance(context).cancelAllWorkByTag("task-$uid-$id")
    }
    fun cancelAll(context:Context,uid:String) {
        val ids=store(context).getStringSet("$ID_LIST:$uid",emptySet()).orEmpty().toList()
        ids.forEach { cancelTask(context,uid,it) }
        WorkManager.getInstance(context).cancelAllWorkByTag("central-reminders-$uid")
    }
    fun migrate(context: Context, uid: String) {
        if(uid.isBlank())return
        val pref=prefs(context,uid)
        if(!pref.getBoolean(MIGRATION,false)) {
            WorkManager.getInstance(context).cancelAllWorkByTag("central-reminders-$uid")
            pref.edit().putBoolean(MIGRATION,true).apply()
        }
    }
    fun restore(context:Context) {
        val uid=context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).getString("activeUid","").orEmpty()
        if(uid.isBlank())return
        val ids=store(context).getStringSet("$ID_LIST:$uid",emptySet()).orEmpty().toList()
        ids.forEach { id -> read(context,uid,id)?.let { schedule(context,uid,it) } }
    }
    internal fun deliver(context:Context,uid:String,id:String) {
        val active=context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).getString("activeUid","")
        if(active!=uid)return
        val task=read(context,uid,id) ?: return
        val pref=prefs(context,uid)
        if(!pref.getBoolean("enabled",true))return
        val time=runCatching{LocalTime.parse(task.text("time"))}.getOrNull() ?:return
        val date=LocalDate.now()
        if(!AndroidInteractions.notificationsAllowed(context))return
        val manager=context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Tarefas com detalhes",NotificationManager.IMPORTANCE_HIGH).apply {
            description="Lembretes de tarefas com título, horário e responsável"
        })
        if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return
        val owner=task.text("owner","Ambos")
        val details="${time} • $owner".let { formatted ->
            if(task.text("note").isBlank()) formatted else "$formatted • ${task.text("note").take(120)}"
        }
        val intent=Intent(context,MainActivity::class.java).apply {
            putExtra("taskId",id)
            putExtra("taskDate",date.toString())
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap=PendingIntent.getActivity(context,(uid+id).hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle(task.text("title").ifBlank{"Tarefa agendada"})
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify((uid+id).hashCode(),notice)
        // The next occurrence is computed locally; no Firebase call in a background receiver.
        if(task.text("repeat") in listOf("Diária","Semanal","Dias da semana","Mensal","Primeiro domingo após dia 8")) {
            schedule(context,uid,task,completedDay=date)
        } else {
            forget(context,uid,id)
        }
    }
}

class TaskAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!= "com.centraldocasal.app.TASK_ALARM")return
        val uid=intent.getStringExtra("uid") ?:return
        val id=intent.getStringExtra("id") ?:return
        Reminders.deliver(context,uid,id)
    }
}

class TaskBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action==Intent.ACTION_MY_PACKAGE_REPLACED) Reminders.restore(context)
    }
}

/** Compatibility: old WorkManager alarms are canceled during migration. */
class ReminderWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork() = Result.success()
}
