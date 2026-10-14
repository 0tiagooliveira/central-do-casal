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
    private fun fingerprintKey(uid: String, id: String) = "fingerprint_$uid:$id"
    private fun plannedKey(uid: String, id: String) = "planned_$uid:$id"

    private fun alarmIntent(context: Context, uid: String, id: String, scheduledDay: LocalDate? = null) =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("central-reminder://task/${Uri.encode(uid)}/${Uri.encode(id)}")
            putExtra("uid", uid)
            putExtra("id", id)
            scheduledDay?.let { putExtra("scheduledDay", it.toString()) }
        }

    private fun pending(context: Context, uid: String, id: String, scheduledDay: LocalDate? = null) =
        PendingIntent.getBroadcast(context, 0, alarmIntent(context, uid, id, scheduledDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun prefs(context: Context, uid: String) =
        context.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE)

    /** Create the task channel before the first alarm fires so the Android settings are usable. */
    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(NotificationChannel(CHANNEL, "Tarefas com detalhes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes de tarefas com título, horário e responsável"
            })
    }

    private fun belongs(record: Record, profileName: String): Boolean {
        val owner = record.text("owner", "Ambos").trim()
        if (owner.equals("Ambos", true)) return true
        val mine = profileName.trim().lowercase()
        if (mine.isEmpty()) return false
        return owner.lowercase() == mine || owner.substringBefore(' ').equals(mine.substringBefore(' '), true)
    }

    /** The same eligibility rules used for scheduling, exposed per task for troubleshooting. */
    internal fun blockReason(record: Record, profileName: String, now: Instant = Instant.now()): String? {
        if (record.kind != "task") return "Não é uma tarefa."
        if (record.flag("needsReview")) return "Confirme os dados importados desta tarefa."
        if (record.flag("needsTimeReview")) return "Confirme o horário desta tarefa."
        if (record.flag("skipReminder")) return "A opção Permitir lembrete está desligada nesta tarefa."
        val time = runCatching { LocalTime.parse(record.text("time")) }.getOrNull()
            ?: return "Informe um horário válido (HH:mm)."
        val start = runCatching { LocalDate.parse(record.text("date")) }.getOrNull()
            ?: return "Confirme a data inicial desta tarefa."
        if (!belongs(record, profileName)) return "Esta tarefa está atribuída a outro responsável."
        if (record.text("repeat") !in setOf("Diária", "Semanal", "Dias da semana", "Mensal", "Primeiro domingo após dia 8") &&
            !start.atTime(time).atZone(ZoneId.systemDefault()).toInstant().isAfter(now)) {
            return "A data ou o horário desta tarefa já passou."
        }
        return null
    }

    data class TaskCheck(val title: String, val status: String, val plannedAt: Long?)

    /** Reads only local scheduling metadata. A planned alarm is not a delivery guarantee. */
    fun inspect(context: Context, uid: String, record: Record, profileName: String): TaskCheck {
        val title = record.text("title").ifBlank { "Tarefa sem título" }
        val reason = blockReason(record, profileName)
        if (reason != null) return TaskCheck(title, reason, null)
        if (!prefs(context, uid).getBoolean("enabled", true)) return TaskCheck(title, "Lembretes desligados neste aparelho.", null)
        val p = store(context)
        val time = p.getLong(plannedKey(uid,record.id),0L)
        if (time <= System.currentTimeMillis()) return TaskCheck(title,"Sem alarme futuro confirmado no aparelho. Use Reagendar tarefas.",null)
        if (!AndroidInteractions.notificationsAllowed(context)) return TaskCheck(title,"Alarme programado, mas notificações bloqueadas no Android.",time)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return TaskCheck(title,"Alarme programado, mas canal Tarefas com detalhes está bloqueado.",time)
        }
        return TaskCheck(title,"Programado no aparelho; entrega ainda não verificada.",time)
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
        p.edit().remove(key(uid,id)).remove(fingerprintKey(uid,id)).remove(plannedKey(uid,id)).putStringSet("$ID_LIST:$uid", names).apply()
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

    fun schedule(context: Context, uid: String, record: Record, profileName: String = "", completedDay: LocalDate? = null, forceRearm: Boolean = false) {
        if(uid.isBlank() || record.kind != "task") return
        ensureChannel(context)
        val pref=prefs(context,uid)
        if(profileName.isNotBlank()) pref.edit().putString("profileName",profileName).apply()
        val name=profileName.ifBlank { pref.getString("profileName","").orEmpty() }
        if(!pref.getBoolean("enabled",true) || blockReason(record,name)!=null) {
            cancelAlarm(context,uid,record.id)
            forget(context,uid,record.id)
            return
        }
        val time=runCatching { LocalTime.parse(record.text("time")) }.getOrNull()
        val start=runCatching { LocalDate.parse(record.text("date")) }.getOrNull()
        if(time==null || start==null) { cancelAlarm(context,uid,record.id);forget(context,uid,record.id);return }
        val today=maxOf(LocalDate.now(),start)
        val upcoming=(0..366).asSequence().map { today.plusDays(it.toLong()) }.firstOrNull {
            it!=completedDay && occurs(record,it) &&
                it.atTime(time).atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now())
        }
        if(upcoming==null){cancelAlarm(context,uid,record.id);forget(context,uid,record.id);return}
        val p=store(context)
        val exact=Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms()==true
        // Só campos que afetam agendamento ou texto visível entram no hash.
        // Registros recuperados do cache podem ter defaults/ordem diferentes
        // dos objetos Firestore. Isso não deve rearmar alarmes sem necessidade.
        val weekdays = (record.fields["days"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }?.distinct()?.sorted().orEmpty()
        val fingerprint=(listOf(
            upcoming.toString(),time.toString(),name,
            pref.getBoolean("quiet",true).toString(),exact.toString(),
            ZoneId.systemDefault().id,record.text("date"),
            record.text("repeat"),record.text("title"),record.text("note"),
            record.text("owner","Ambos"),record.flag("skipReminder").toString(),
            record.flag("needsReview").toString(),record.flag("needsTimeReview").toString(),
            weekdays.joinToString(",")
        ).joinToString("|")).hashCode().toString()
        if(!forceRearm && p.getString(fingerprintKey(uid,record.id),null)==fingerprint &&
            p.getLong(plannedKey(uid,record.id),0L)>System.currentTimeMillis()) return
        cancelAlarm(context,uid,record.id)
        cache(context,uid,record)
        try {
            val scheduledAt=plan(context,uid,record.id,upcoming,time)
            if(scheduledAt>0L) {
                p.edit().putString(fingerprintKey(uid,record.id),fingerprint)
                    .putLong(plannedKey(uid,record.id),scheduledAt)
                    .apply()
            } else forget(context,uid,record.id)
        } catch (_:Exception) {
            forget(context,uid,record.id)
            p.edit().putLong("lastScheduleFailed:$uid",System.currentTimeMillis()).apply()
        }
    }

    private fun plan(context:Context,uid:String,id:String,date:LocalDate,time:LocalTime):Long {
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
        if(whenMillis<=System.currentTimeMillis())return 0L
        arm(context,uid,id,whenMillis,date)
        return whenMillis
    }

    private fun cancelAlarm(context:Context,uid:String,id:String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context,uid,id))
    }
    fun cancel(context:Context,uid:String,id:String,day:LocalDate) {
        cancelAlarm(context,uid,id)
        read(context,uid,id)?.let { schedule(context,uid,it,completedDay=day,forceRearm=true) }
    }
    fun cancelTask(context:Context,uid:String,id:String) {
        cancelAlarm(context,uid,id)
        forget(context,uid,id)
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag("task-$uid-$id") }
    }
    fun cancelAll(context:Context,uid:String) {
        val ids=store(context).getStringSet("$ID_LIST:$uid",emptySet()).orEmpty().toList()
        ids.forEach { cancelTask(context,uid,it) }
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag("central-reminders-$uid") }
    }
    fun migrate(context: Context, uid: String) {
        if(uid.isBlank())return
        val pref=prefs(context,uid)
        if(!pref.getBoolean(MIGRATION,false)) {
            runCatching { WorkManager.getInstance(context).cancelAllWorkByTag("central-reminders-$uid") }
            pref.edit().putBoolean(MIGRATION,true).apply()
        }
    }
    /** Boot always rearms; foreground resume only rearms if time zone, permissions or data changed. */
    fun restore(context:Context, forceRearm:Boolean=true) {
        val uid=context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).getString("activeUid","").orEmpty()
        if(uid.isBlank())return
        val ids=store(context).getStringSet("$ID_LIST:$uid",emptySet()).orEmpty().toList()
        ids.forEach { id -> read(context,uid,id)?.let { schedule(context,uid,it,forceRearm=forceRearm) } }
    }
    data class Diagnostics(
        val knownTasks: Int,
        val futureSchedules: Int,
        val nextAt: Long?,
        val lastDeliveredAt: Long?,
        val lastFailedAt: Long?,
        val lastDeliveryBlockedAt: Long?,
        val notificationsAllowed: Boolean,
        val taskChannelAllowed: Boolean,
        val exactAlarmsAllowed: Boolean
    )

    fun diagnostics(context: Context, uid: String): Diagnostics {
        val p=store(context)
        val now=System.currentTimeMillis()
        val ids=p.getStringSet("$ID_LIST:$uid",emptySet()).orEmpty()
        val planned=ids.map { p.getLong(plannedKey(uid,it),0L) }.filter { it>now }
        val channel=context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL)
        return Diagnostics(
            knownTasks=ids.size,
            futureSchedules=planned.size,
            nextAt=planned.minOrNull(),
            lastDeliveredAt=p.getLong("lastDelivered:$uid",0L).takeIf { it>0L },
            lastFailedAt=p.getLong("lastScheduleFailed:$uid",0L).takeIf { it>0L },
            lastDeliveryBlockedAt=p.getLong("lastDeliveryBlocked:$uid",0L).takeIf { it>0L },
            notificationsAllowed=AndroidInteractions.notificationsAllowed(context),
            taskChannelAllowed=channel?.importance != NotificationManager.IMPORTANCE_NONE,
            exactAlarmsAllowed=Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms()==true
        )
    }

    /** Manual device-only test: uses the same channel and payload as real tasks. */
    fun sendDetailedTest(context:Context,uid:String,profileName:String):String {
        if(uid.isBlank())return "Entre na conta para testar."
        if(!AndroidInteractions.notificationsAllowed(context))return "Notificações bloqueadas no Android."
        val id="manual-detailed-preview"
        val task=Record(id=id,kind="task",fields=mapOf(
            "title" to "Exemplo: reservar almoço de amanhã",
            "note" to "Conferir horário e disponibilidade",
            "owner" to profileName.ifBlank { "Ambos" },
            "date" to LocalDate.now().plusDays(1).toString(),
            "time" to LocalTime.now().withSecond(0).withNano(0).toString(),
            "repeat" to "Única"
        ))
        cache(context,uid,task)
        return try {
            deliver(context,uid,id)
            "Notificação de exemplo enviada. Verifique a bandeja do Android."
        } finally { forget(context,uid,id) }
    }

    /**
     * Schedules a real OS-level broadcast two minutes from now; unlike the immediate preview
     * this checks AlarmManager, background delivery and notification visibility on the device.
     * It is intentionally independent of the user's quiet hours and has no Firebase writes.
     */
    fun scheduleDeliveryTest(context:Context,uid:String,profileName:String):String {
        if(uid.isBlank())return "Entre na conta antes de testar."
        if(!AndroidInteractions.notificationsAllowed(context))return "Ative as notificações do Android antes de testar."
        if(!prefs(context,uid).getBoolean("enabled",true))return "Ative os lembretes de tarefas antes de testar."
        val at=System.currentTimeMillis()+2*60_000L
        val local=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val id="diagnostic-$at"
        val task=Record(id=id,kind="task",fields=mapOf(
            "title" to "Teste agendado da Central do Casal",
            "note" to "Este aviso foi disparado por um alarme real do Android.",
            "owner" to profileName.ifBlank { "Meu perfil" },
            "date" to local.toLocalDate().toString(),
            "time" to local.toLocalTime().withSecond(0).withNano(0).toString(),
            "repeat" to "Única"
        ))
        cache(context,uid,task)
        return try {
            arm(context,uid,id,at,local.toLocalDate())
            val exact=Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms()==true
            "Teste agendado para ${local.toLocalTime().withSecond(0).withNano(0)}. Feche o app e aguarde dois minutos. ${if(exact) "Alarme exato autorizado." else "Alarme aproximado: o Android pode atrasar."}"
        } catch (_:Exception) {
            forget(context,uid,id)
            "Não foi possível agendar o teste neste aparelho."
        }
    }

    private fun arm(context:Context,uid:String,id:String,at:Long,scheduledDay:LocalDate) {
        val manager=context.getSystemService(AlarmManager::class.java)
            ?: error("AlarmManager indisponível")
        try {
            if(Build.VERSION.SDK_INT>=31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context,uid,id,scheduledDay))
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context,uid,id,scheduledDay))
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context,uid,id,scheduledDay))
        }
    }

    internal fun deliver(context:Context,uid:String,id:String,scheduledDay:LocalDate=LocalDate.now()) {
        val active=context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).getString("activeUid","")
        if(active!=uid)return
        val task=read(context,uid,id) ?: return
        val pref=prefs(context,uid)
        if(!pref.getBoolean("enabled",true))return
        val time=runCatching{LocalTime.parse(task.text("time"))}.getOrNull() ?:return
        val date=scheduledDay
        // Mesmo quando o Android bloqueia a entrega, não perder a PRÓXIMA
        // ocorrência de uma tarefa recorrente. Nenhum detalhe sai do aparelho.
        fun advance() {
            if (task.text("repeat") in listOf("Diária","Semanal","Dias da semana","Mensal","Primeiro domingo após dia 8")) {
                schedule(context,uid,task,completedDay=date,forceRearm=true)
            } else {
                forget(context,uid,id)
            }
        }
        ensureChannel(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        val notificationsAllowed=AndroidInteractions.notificationsAllowed(context)
        val channelAllowed=manager?.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
        if(!notificationsAllowed || manager==null || !channelAllowed) {
            store(context).edit().putLong("lastDeliveryBlocked:$uid",System.currentTimeMillis()).apply()
            advance()
            return
        }
        val owner=task.text("owner","Ambos")
        val details="${time} • $owner".let { formatted ->
            if(task.text("note").isBlank()) formatted else "$formatted • ${task.text("note").take(120)}"
        }
        val intent=Intent(context,MainActivity::class.java).apply {
            putExtra("taskId",id)
            putExtra("taskDate",date.toString())
            putExtra("taskUid",uid)
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap=PendingIntent.getActivity(context,(uid+id).hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("Lembrete de tarefa")
            .setContentText("Desbloqueie para ver os detalhes.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val showOnLockScreen=pref.getBoolean("showDetailsOnLockScreen",false)
        val notice=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle(task.text("title").ifBlank{"Tarefa agendada"})
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(if(showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(if(showOnLockScreen) null else publicVersion)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify((uid+id).hashCode(),notice)
            store(context).edit().putLong("lastDelivered:$uid",System.currentTimeMillis()).apply()
        } catch (_:SecurityException) {
            store(context).edit().putLong("lastDeliveryBlocked:$uid",System.currentTimeMillis()).apply()
        }
        // Cada disparo processado libera a próxima ocorrência, mesmo se o Android
        // vetar a notificação atual. Não usamos rede nem Firebase no receiver.
        advance()
    }
}

class TaskAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!= "com.centraldocasal.app.TASK_ALARM")return
        val uid=intent.getStringExtra("uid") ?:return
        val id=intent.getStringExtra("id") ?:return
        val scheduledDay=intent.getStringExtra("scheduledDay")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        Reminders.deliver(context,uid,id,scheduledDay)
    }
}

class TaskBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) Reminders.restore(context)
    }
}

/** Compatibility: old WorkManager alarms are canceled during migration. */
class ReminderWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork() = Result.success()
}
