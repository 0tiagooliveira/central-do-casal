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
        TaskAlarmReceiver().onReceive(context,Intent("com.centraldocasal.app.TASK_ALARM")
            .putExtra("uid","local").putExtra("id","test-notice")
            .putExtra("scheduledDay",LocalDate.now().plusDays(1).toString()))
        val notice=shadowOf(manager).allNotifications.single()
        assertEquals("Reservar almoço de segunda",notice.extras.getCharSequence("android.title").toString())
        val body=notice.extras.getCharSequence("android.text").toString()
        assertTrue(body.contains("16:00"))
        assertTrue(body.contains("Rhayra"))
        assertTrue(body.contains("Conferir cardápio"))
        assertTrue(body.contains(LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))))
        assertTrue(body.contains("Responsável: Rhayra"))
        assertEquals("central-tasks-v2",notice.channelId)
    }
    @Test fun expandedNotificationKeepsMeaningfulLongTaskNote() {
        val note = "Conferir transporte, entrada, documentos e confirmar com o responsável antes de sair. " +
            "Levar os itens preparados e revisar o local de encontro quando o compromisso estiver próximo."
        val day = LocalDate.now().plusDays(1)
        val task = Record("detailed-note", "task", fields=mapOf(
            "title" to "Separar itens antes da viagem",
            "note" to note,
            "time" to "08:15",
            "date" to day.toString(),
            "owner" to "Tiago",
            "repeat" to "Única"
        ))
        Reminders.schedule(context,"local",task,"Tiago")
        TaskAlarmReceiver().onReceive(context,Intent("com.centraldocasal.app.TASK_ALARM")
            .putExtra("uid","local").putExtra("id",task.id).putExtra("scheduledDay",day.toString()))
        val notice = shadowOf(manager).allNotifications.single()
        val details = notice.extras.getCharSequence("android.text").toString()
        assertTrue(details.contains("08:15"))
        assertTrue(details.contains("Responsável: Tiago"))
        assertTrue(details.contains(note))
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE,notice.visibility)
        assertFalse(notice.publicVersion.extras.toString().contains(note))
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

    @Test fun foregroundRestorePreservesUnchangedPendingAlarm() {
        val task=Record("resume-task","task",fields=mapOf(
            "title" to "Separar mochila", "note" to "Conferir caderno",
            "time" to "16:00","date" to LocalDate.now().plusDays(1).toString(),
            "repeat" to "Única","owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        val alarm=context.getSystemService(AlarmManager::class.java)
        val before=shadowOf(alarm).peekNextScheduledAlarm()
        assertNotNull(before)
        Reminders.restore(context,forceRearm=false)
        assertSame("Voltar ao app sem mudanças não deve recriar alarme",before,shadowOf(alarm).peekNextScheduledAlarm())
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

    @Test fun privateTaskKeepsFullDetailsInShadeButGenericPublicVersion() {
        val task=Record("private-lock","task",true,mapOf(
            "title" to "Consulta privada", "note" to "Informação sensível",
            "time" to "16:00", "date" to LocalDate.now().plusDays(1).toString(),
            "repeat" to "Única", "owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        TaskAlarmReceiver().onReceive(context,
            Intent("com.centraldocasal.app.TASK_ALARM").putExtra("uid","local").putExtra("id",task.id))
        val notice=shadowOf(manager).allNotifications.single()
        assertEquals("Consulta privada",notice.extras.getCharSequence("android.title").toString())
        assertNotNull(notice.publicVersion)
        assertEquals("Lembrete de tarefa",notice.publicVersion.extras.getCharSequence("android.title").toString())
        assertFalse(notice.publicVersion.extras.toString().contains("Informação sensível"))
        assertFalse(notice.publicVersion.extras.toString().contains("Consulta privada"))
    }

    @Test fun recurringReminderKeepsNextAlarmEvenWhenAndroidBlocksDelivery() {
        val day=LocalDate.now().plusDays(1)
        val task=Record("blocked-recurring","task",fields=mapOf(
            "title" to "Dar ração para a Mel", "note" to "Separar porção",
            "time" to "16:00", "date" to day.toString(),
            "repeat" to "Diária", "owner" to "Rhayra"
        ))
        Reminders.schedule(context,"local",task,"Rhayra")
        shadowOf(manager).setNotificationsEnabled(false)
        TaskAlarmReceiver().onReceive(context,Intent("com.centraldocasal.app.TASK_ALARM")
            .putExtra("uid","local").putExtra("id",task.id).putExtra("scheduledDay",day.toString()))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        val health=Reminders.diagnostics(context,"local")
        assertNotNull("O Android bloqueou a entrega e o diagnóstico deve registrar isso.",health.lastDeliveryBlockedAt)
        assertEquals(1,health.futureSchedules)
        assertNotNull(health.nextAt)
        assertTrue(health.nextAt!! >= day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    @Test fun diagnosticsExplainWhyOneTaskDoesNotNotify() {
        val future = LocalDate.now().plusDays(2).toString()
        val ordinary=Record("eligible","task",fields=mapOf(
            "title" to "Separar material","date" to future,"time" to "16:00",
            "owner" to "Rhayra", "repeat" to "Única"
        ))
        assertNull(Reminders.blockReason(ordinary,"Rhayra"))
        assertEquals("Esta tarefa está atribuída a outro responsável.",
            Reminders.blockReason(ordinary,"Tiago"))
        assertEquals("Confirme o horário desta tarefa.",
            Reminders.blockReason(ordinary.copy(fields=ordinary.fields+("needsTimeReview" to true)),"Rhayra"))
        assertEquals("A opção Permitir lembrete está desligada nesta tarefa.",
            Reminders.blockReason(ordinary.copy(fields=ordinary.fields+("skipReminder" to true)),"Rhayra"))
        assertEquals("Informe um horário válido (HH:mm).",
            Reminders.blockReason(ordinary.copy(fields=ordinary.fields+("time" to "")),"Rhayra"))
        assertEquals("A data ou o horário desta tarefa já passou.",
            Reminders.blockReason(ordinary.copy(fields=ordinary.fields+("date" to "2020-01-01")),"Rhayra"))
    }

    @Test fun taskDiagnosticShowsActualAlarmAndNotMerelyEnabledPreference() {
        val task = Record("diagnostic-task","task",fields=mapOf(
            "title" to "Reservar almoço de amanhã","date" to LocalDate.now().plusDays(1).toString(),
            "time" to "16:00","owner" to "Rhayra", "repeat" to "Única"
        ))
        val before=Reminders.inspect(context,"local",task,"Rhayra")
        assertNull(before.plannedAt)
        assertTrue(before.status.contains("Sem alarme futuro"))
        Reminders.schedule(context,"local",task,"Rhayra")
        val after=Reminders.inspect(context,"local",task,"Rhayra")
        assertNotNull(after.plannedAt)
        assertTrue(after.status.contains("Programado"))
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
