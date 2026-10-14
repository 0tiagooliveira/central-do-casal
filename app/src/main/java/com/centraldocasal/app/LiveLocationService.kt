package com.centraldocasal.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * Compartilhamento voluntário, com duração limitada. Sem ACCESS_BACKGROUND_LOCATION:
 * deve ser iniciado pelo usuário com app em primeiro plano. Não é serviço de emergência.
 * Se Android matar o processo, não reiniciamos a sessão; o documento expira logicamente.
 */
/** Usar GPS exige localização precisa; acesso apenas aproximado usa NETWORK. */
internal fun liveLocationProviderCandidates(fineGranted: Boolean, coarseGranted: Boolean): List<String> =
    buildList {
        if (fineGranted) add(LocationManager.GPS_PROVIDER)
        if (fineGranted || coarseGranted) add(LocationManager.NETWORK_PROVIDER)
    }

class LiveLocationService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.centraldocasal.app.live.START"
        const val ACTION_STOP = "com.centraldocasal.app.live.STOP"
        const val EXTRA_HOUSEHOLD = "household"
        const val EXTRA_UID = "uid"
        const val SESSION_MS = 30 * 60_000L
        const val STALE_MS = 90_000L
        private const val UPDATE_MS = 15_000L
        private const val CHANNEL = "central-live-location"
        private const val NOTICE_ID = 7781
    }

    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var uid = ""
    private var household = ""
    private var sharing = false
    private var lastWrite = 0L
    private var sessionEnd = 0L
    private val endSession = Runnable { stopSelf() }
    private val authListener = FirebaseAuth.AuthStateListener {
        if (sharing && it.currentUser?.uid != uid) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        auth.addAuthStateListener(authListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val who = intent.getStringExtra(EXTRA_UID).orEmpty()
        val home = intent.getStringExtra(EXTRA_HOUSEHOLD).orEmpty()
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if ((!fineGranted && !coarseGranted) || who.isBlank() || home.isBlank() || auth.currentUser?.uid != who) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sharing) return START_NOT_STICKY
        uid = who
        household = home
        sessionEnd = System.currentTimeMillis() + SESSION_MS
        try {
            ensureChannel()
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTICE_ID, notification)
            sharing = true
            var subscribed = false
            for (provider in liveLocationProviderCandidates(fineGranted, coarseGranted)) {
                if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    // Um provedor indisponível não deve impedir o outro.
                    val requested = runCatching {
                        @Suppress("MissingPermission")
                        manager.requestLocationUpdates(provider, UPDATE_MS, 0f, this, Looper.getMainLooper())
                    }.isSuccess
                    if (requested) subscribed = true
                }
            }
            if (!subscribed) stopSelf()
            else handler.postDelayed(endSession, SESSION_MS)
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: RuntimeException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val now = System.currentTimeMillis()
        if (!sharing || now > sessionEnd || auth.currentUser?.uid != uid) {
            stopSelf()
            return
        }
        if (now - lastWrite < UPDATE_MS || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.time <= 0L || now - location.time > 90_000L || location.time > now + 30_000L) return
        lastWrite = now
        firestore.collection("households").document(household)
            .collection("liveLocations").document(uid)
            .set(mapOf(
                "ownerUid" to uid,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "capturedAt" to Timestamp(Date(location.time)),
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(now + STALE_MS)),
                "sessionEndsAt" to Timestamp(Date(sessionEnd))
            ))
            .addOnFailureListener { error ->
                if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
                    error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    stopSelf()
                }
            }
    }

    override fun onDestroy() {
        handler.removeCallbacks(endSession)
        runCatching { manager.removeUpdates(this) }
        if (sharing && uid.isNotBlank() && household.isNotBlank() && auth.currentUser?.uid == uid) {
            firestore.collection("households").document(household)
                .collection("liveLocations").document(uid).delete()
        }
        sharing = false
        auth.removeAuthStateListener(authListener)
        super.onDestroy()
    }

    private fun ensureChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "Localização compartilhada", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Sessão de localização iniciada por você, com duração máxima de 30 minutos"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, NOTICE_ID,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, NOTICE_ID, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val generic = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("Central do Casal")
            .setContentText("Compartilhamento de localização ativo")
            .build()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_central)
            .setContentTitle("Compartilhando localização com o casal")
            .setContentText("Termina em até 30 min. Toque em Parar quando quiser.")
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(generic)
            .build()
    }
}
