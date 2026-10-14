package com.centraldocasal.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.centraldocasal.app.domain.quietAt
import java.time.LocalTime

object AndroidInteractions {
    private const val TEST_CHANNEL = "central-test"
    private const val TEST_NOTIFICATION_ID = 7301
    private const val SOS_CHANNEL = "central-sos"

    /** Call only in response to a user action. Android retains control of touch feedback. */
    fun haptic(view: View, enabled: Boolean) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Call only after an explicit interaction. Android controls effect availability and volume. */
    fun clickSound(view: View, enabled: Boolean) {
        if (enabled) view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Manual test only. Acceptance by Android does not guarantee sound, vibration or visibility. */
    fun sendTestNotification(context: Context, uid: String, allowDuringQuiet: Boolean = false): String {
        val now = LocalTime.now()
        val preferences = context.getSharedPreferences("reminders_$uid", Context.MODE_PRIVATE)
        if (!allowDuringQuiet && preferences.getBoolean("quiet", true) && quietAt(now.hour, now.minute)) {
            return "Não enviado: Sossego ativo"
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "Não enviado: permita notificações nas configurações do Android"
        }
        if (!notificationsAllowed(context)) return "Não enviado: notificações bloqueadas no Android"
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return "Não enviado: serviço de notificações indisponível"
        manager.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "Teste de notificações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Testes enviados manualmente nas configurações do aplicativo"
        })
        if (manager.getNotificationChannel(TEST_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return "Não enviado: canal de teste bloqueado no Android"
        }
        val tap = PendingIntent.getActivity(
            context, TEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("Central do Casal")
            .setContentText("Esta é uma notificação de teste.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(TEST_NOTIFICATION_ID, notification)
            "Enviado ao Android"
        } catch (_: SecurityException) {
            "Não enviado: notificações bloqueadas no Android"
        }
    }

    /** A partner's SOS is intentionally separate from quiet-hour reminders. */
    fun notifySafetyAlert(context: Context, alertId: String, name: String, mapUrl: String): String {
        if (!notificationsAllowed(context)) return "Alerta recebido, mas as notificações estão bloqueadas neste aparelho."
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return "Alerta recebido; serviço de notificações indisponível."
        manager.createNotificationChannel(NotificationChannel(SOS_CHANNEL, "Alertas SOS do casal", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pedidos urgentes de ajuda do parceiro"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 900)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
        })
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, alertId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, SOS_CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("Central do Casal")
            .setContentText("Você tem um alerta importante.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, SOS_CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("SOS de $name")
            .setContentText("Toque para abrir o alerta e ver a localização.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name pediu ajuda. Localização: $mapUrl"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        return try { manager.notify(alertId.hashCode(), notification); "Alerta SOS enviado ao Android." } catch (_: SecurityException) { "Alerta recebido, mas o Android bloqueou notificações." }
    }
}
