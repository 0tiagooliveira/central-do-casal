package com.centraldocasal.app

import android.Manifest
import android.app.Application
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.centraldocasal.app.domain.Record
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class DetailedTaskNotificationTest {
    private lateinit var context:Application
    private lateinit var manager:NotificationManager
    @Before fun prepare(){
        context=ApplicationProvider.getApplicationContext()
        manager=context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("central_task_alarm_cache",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("central_local_v1",Context.MODE_PRIVATE).edit().putString("activeUid","local").commit()
        context.getSharedPreferences("reminders_local",Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",true).putBoolean("quiet",false).commit()
    }
    @Test fun notificationShowsTaskTitleTimeOwnerAndNote() {
        val task=Record("test-notice","task",fields=mapOf(
            "title" to "Reservar almoço de segunda",
            "note" to "Conferir cardápio antes",
            "time" to "16:00",
            "date" to LocalDate.now().plusDays(1).toString(),
            "repeat" to "Única",
            "owner" to "Rhayra",
            "skipReminder" to false,
            "needsReview" to false,
            "needsTimeReview" to false
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        TaskAlarmReceiver().onReceive(context,Intent("com.centraldocasal.app.TASK_ALARM").putExtra("uid","local").putExtra("id","test-notice"))
        val notice=shadowOf(manager).allNotifications.single()
        assertEquals("Reservar almoço de segunda",notice.extras.getCharSequence("android.title").toString())
        val body=notice.extras.getCharSequence("android.text").toString()
        assertTrue(body.contains("16:00"))
        assertTrue(body.contains("Rhayra"))
        assertTrue(body.contains("Conferir cardápio"))
        assertEquals("central-tasks-v2",notice.channelId)
    }
    @Test fun twoMinuteDiagnosticReallySchedulesAnAndroidAlarmBeforePostingNotification() {
        val result = Reminders.scheduleDeliveryTest(context, "local", "Rhayra")
        assertTrue(result, result.contains("Teste agendado"))
        val alarm = context.getSystemService(AlarmManager::class.java)
        assertNotNull(shadowOf(alarm).nextScheduledAlarm)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        val pending = context.getSharedPreferences("central_task_alarm_cache", Context.MODE_PRIVATE)
            .getStringSet("tasks:local", emptySet()).orEmpty()
        val diagnostic = pending.single { it.startsWith("diagnostic-") }
        TaskAlarmReceiver().onReceive(
            context, Intent("com.centraldocasal.app.TASK_ALARM").putExtra("uid","local").putExtra("id",diagnostic)
        )
        val notice = shadowOf(manager).allNotifications.single()
        assertEquals("Teste agendado da Central do Casal", notice.extras.getCharSequence("android.title").toString())
        assertTrue(notice.extras.getCharSequence("android.text").toString().contains("Rhayra"))
        assertFalse(context.getSharedPreferences("central_task_alarm_cache", Context.MODE_PRIVATE)
            .getStringSet("tasks:local", emptySet()).orEmpty().contains(diagnostic))
    }

    @Test fun refreshingUnchangedTaskPreservesItsPendingAlarm() {
        val task=Record("stable-task","task",fields=mapOf(
            "title" to "Buscar a Rhayra", "time" to "16:00",
            "date" to LocalDate.now().plusDays(1).toString(), "repeat" to "Única",
            "owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        val channel=manager.getNotificationChannel("central-tasks-v2")
        assertNotNull("O canal precisa existir antes do disparo para configurar no Android.", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH,channel.importance)
        val alarms=context.getSystemService(AlarmManager::class.java)
        val before=shadowOf(alarms).peekNextScheduledAlarm()
        assertNotNull(before)
        val planned=context.getSharedPreferences("central_task_alarm_cache",Context.MODE_PRIVATE)
            .getLong("planned_local:stable-task",0L)
        assertTrue(planned>System.currentTimeMillis())
        Reminders.schedule(context,"local",task,"Rhayra")
        assertSame("Refrescar o Firestore não pode substituir o alarme pendente.",before,shadowOf(alarms).peekNextScheduledAlarm())
        assertEquals(planned,context.getSharedPreferences("central_task_alarm_cache",Context.MODE_PRIVATE)
            .getLong("planned_local:stable-task",0L))
        val health=Reminders.diagnostics(context,"local")
        assertEquals(1,health.futureSchedules)
        assertEquals(planned,health.nextAt)
    }

    @Test fun privateTaskDetailsOnLockScreenRequireExplicitOptIn() {
        val task=Record("privacy-task","task",fields=mapOf(
            "title" to "Consulta pessoal", "note" to "Informação privada",
            "time" to "15:00","date" to LocalDate.now().plusDays(1).toString(),
            "repeat" to "Única","owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        val intent=Intent("com.centraldocasal.app.TASK_ALARM").putExtra("uid","local").putExtra("id","privacy-task")
        TaskAlarmReceiver().onReceive(context,intent)
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE,shadowOf(manager).allNotifications.single().visibility)
        context.getSharedPreferences("reminders_local",Context.MODE_PRIVATE)
            .edit().putBoolean("showDetailsOnLockScreen",true).commit()
        // A tarefa única foi consumida no primeiro disparo; recadastre para a segunda entrega.
        Reminders.schedule(context,"local",task,"Rhayra")
        TaskAlarmReceiver().onReceive(context,intent)
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC,shadowOf(manager).allNotifications.single().visibility)
        assertNotNull(Reminders.diagnostics(context,"local").lastDeliveredAt)
    }

    @Test fun reminderKeepsTheOriginalScheduledDayEvenWhenDeliveredLater() {
        val day = LocalDate.now().plusDays(1)
        val task=Record("dated-reminder","task",fields=mapOf(
            "title" to "Reservar almoço",
            "time" to "16:00","date" to day.toString(),
            "repeat" to "Única","owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        val event=Intent("com.centraldocasal.app.TASK_ALARM")
            .putExtra("uid","local").putExtra("id","dated-reminder")
            .putExtra("scheduledDay",day.toString())
        TaskAlarmReceiver().onReceive(context,event)
        val notice=shadowOf(manager).allNotifications.single()
        val target=shadowOf(notice.contentIntent).savedIntent
        assertEquals("dated-reminder",target.getStringExtra("taskId"))
        assertEquals(day.toString(),target.getStringExtra("taskDate"))
        assertEquals("local",target.getStringExtra("taskUid"))
    }

    @Test fun reminderTargetRejectsAnotherSignedInAccount() {
        assertTrue(reminderMatchesSession("local","local"))
        assertTrue(reminderMatchesSession("local",""))
        assertFalse(reminderMatchesSession("local","other"))
        assertFalse(reminderMatchesSession("","local"))
    }

    @Test fun profileNameChangeEnablesNewOwnerAlarm() {
        val task = Record("profile-task", "task", fields = mapOf(
            "title" to "Buscar a Rhayra", "owner" to "Rhayra",
            "date" to LocalDate.now().plusDays(1).toString(),
            "time" to "17:30", "repeat" to "Única"
        ))
        Reminders.schedule(context, "local", task, "Tiago")
        assertEquals(0, Reminders.diagnostics(context, "local").futureSchedules)
        Reminders.schedule(context, "local", task, "Rhayra")
        assertEquals(1, Reminders.diagnostics(context, "local").futureSchedules)
    }

    @Test fun changingQuietHoursRearmsExistingTaskToCorrectTime() {
        val tomorrow = LocalDate.now().plusDays(1)
        val task = Record("quiet-task", "task", fields = mapOf(
            "title" to "Rotina noturna", "owner" to "Rhayra",
            "date" to tomorrow.toString(),
            "time" to "02:10", "repeat" to "Única"
        ))
        Reminders.schedule(context, "local", task, "Rhayra")
        val before = Reminders.diagnostics(context, "local").nextAt
        assertNotNull(before)
        context.getSharedPreferences("reminders_local", Context.MODE_PRIVATE).edit().putBoolean("quiet",true).commit()
        Reminders.schedule(context, "local", task, "Rhayra")
        val after = Reminders.diagnostics(context, "local").nextAt
        assertNotNull(after)
        assertTrue("Com Sossego ativo, o alarme deve ser reagendado após a janela silenciosa.", after!! > before!!)
    }

    @Test fun anotherUsersPrivateTaskDoesNotAppearOnThisDevice() {
        val task=Record("other-owner","task",fields=mapOf(
            "title" to "Lembrete do Tiago",
            "time" to "16:00",
            "date" to LocalDate.now().plusDays(1).toString(),
            "repeat" to "Única",
            "owner" to "Tiago"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        TaskAlarmReceiver().onReceive(context,Intent("com.centraldocasal.app.TASK_ALARM").putExtra("uid","local").putExtra("id","other-owner"))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
