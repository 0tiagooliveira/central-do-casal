package com.centraldocasal.app

import android.Manifest
import android.app.Application
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
