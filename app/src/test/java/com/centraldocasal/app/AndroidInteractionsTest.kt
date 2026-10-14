package com.centraldocasal.app

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidInteractionsTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager

    @Before fun prepareIsolatedAndroidFixture() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun runtimePermissionDenialDoesNotPostOrClaimSuccess() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(AndroidInteractions.notificationsAllowed(context))
        assertTrue(AndroidInteractions.sendTestNotification(context, "fixture", allowDuringQuiet = true).startsWith("Não enviado:"))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun globalNotificationBlockDoesNotPost() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(AndroidInteractions.notificationsAllowed(context))
        assertEquals("Não enviado: notificações bloqueadas no Android", AndroidInteractions.sendTestNotification(context, "fixture", allowDuringQuiet = true))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun blockedChannelIsPreservedAndDoesNotPost() {
        manager.createNotificationChannel(NotificationChannel("central-test", "Teste fixture", NotificationManager.IMPORTANCE_NONE))
        assertTrue(AndroidInteractions.notificationsAllowed(context))
        assertEquals("Não enviado: canal de teste bloqueado no Android", AndroidInteractions.sendTestNotification(context, "fixture", allowDuringQuiet = true))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("central-test").importance)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun manualTestPostsGenericContentWithoutEnablingRoutineReminders() {
        assertEquals("Enviado ao Android", AndroidInteractions.sendTestNotification(context, "fixture", allowDuringQuiet = true))
        val notices = shadowOf(manager).allNotifications
        assertEquals(1, notices.size)
        assertEquals("central-test", notices.single().channelId)
        assertEquals("Esta é uma notificação de teste.", notices.single().extras.getCharSequence("android.text").toString())
        assertFalse(context.getSharedPreferences("reminders_fixture", Application.MODE_PRIVATE).getBoolean("enabled", false))
    }

    @Test fun sosNotificationKeepsSensitiveDetailsPrivateOnLockScreen() {
        assertEquals(
            "Alerta SOS enviado ao Android.",
            AndroidInteractions.notifySafetyAlert(context, "fixture-alert", "Pessoa Teste", "https://maps.example/secret")
        )
        val notice = shadowOf(manager).allNotifications.single()
        assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
        assertNotNull(notice.publicVersion)
        assertEquals("Central do Casal", notice.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Você tem um alerta importante.", notice.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(notice.publicVersion.extras.toString().contains("Pessoa Teste"))
        assertFalse(notice.publicVersion.extras.toString().contains("maps.example"))
    }
}
