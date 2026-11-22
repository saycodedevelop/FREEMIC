package com.karaokelink.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.*
import com.karaokelink.R
import com.karaokelink.data.SessionRuntime
import com.karaokelink.domain.*
import com.karaokelink.presentation.MainActivity
import kotlinx.coroutines.*

/** Keeps the player audio/network session alive independently of the Activity's visibility. */
open class PlayerService : Service() {
    companion object {
        const val START = "com.karaokelink.START_PLAYER"
        const val STOP = "com.karaokelink.STOP_PLAYER"
        const val CHANNEL = "live-player"
        const val NOTIFICATION = 42
        private var owner: PlayerService? = null
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PlayerService::class.java).setAction(START))
        }
        fun stop(context: Context) {
            owner?.ownsSession = false
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SessionRepository
    private var ownsSession = false
    private var updates: Job? = null
    private var lease: Job? = null
    private var cpu: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    protected open fun createRepository(): SessionRepository = SessionRuntime.get(this)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.karaokelink.presentation.AppLanguage.wrap(newBase))
    }
    override fun onCreate() {
        super.onCreate()
        repository = createRepository()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.player_notification_channel), NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch {
                if (repository.state.value.mode == Mode.HOST) repository.close()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (intent?.action != START || repository.state.value.mode != Mode.HOST || !repository.continuesInBackground) {
            stopSelf(); return START_NOT_STICKY
        }
        try {
            val notification = notification(repository.state.value.peers)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION, notification)
            owner = this; ownsSession = true
            acquireLease()
            updates?.cancel()
            updates = scope.launch {
                var previous = -1
                repository.state.collect { state ->
                    if (state.mode != Mode.HOST || !repository.continuesInBackground) { stopSelf(); return@collect }
                    if (state.engineError) { repository.pause(); stopSelf(); return@collect }
                    if (state.peers != previous) {
                        previous = state.peers
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state.peers))
                    }
                }
            }
        } catch (_: RuntimeException) {
            scope.launch {
                if (repository.state.value.mode == Mode.HOST) repository.pause()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(peers: Int): Notification {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 3, Intent(this, PlayerService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.player_notification_title))
            .setContentText(getString(R.string.player_notification_active, peers))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop_player), stop).build()).build()
    }
    @Suppress("DEPRECATION")
    private fun acquireLease() {
        if (cpu == null) cpu = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KaraokeLink:live-player").apply { setReferenceCounted(false) }
        if (wifi == null) wifi = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KaraokeLink:player-wifi").apply { setReferenceCounted(false); acquire() }
        if (lease == null) lease = scope.launch {
            while (isActive) { cpu?.acquire(60_000); delay(30_000) }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (owner === this) owner = null
        updates?.cancel(); lease?.cancel()
        if (ownsSession && repository.state.value.mode == Mode.HOST && repository.continuesInBackground) {
            scope.launch { repository.pause() }
        }
        ownsSession = false
        cpu?.let { if (it.isHeld) it.release() }; cpu = null
        wifi?.let { if (it.isHeld) it.release() }; wifi = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }
}
