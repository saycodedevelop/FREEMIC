package com.karaokelink.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.*
import com.karaokelink.R
import com.karaokelink.data.SessionRuntime
import com.karaokelink.domain.*
import com.karaokelink.presentation.MainActivity
import kotlinx.coroutines.*

/** Started explicitly from the visible Mic screen after RECORD_AUDIO is granted. */
open class MicrophoneService : Service() {
    companion object {
        const val START = "com.karaokelink.START_MIC"
        const val STOP = "com.karaokelink.STOP_MIC"
        const val CHANNEL = "live-microphone"
        const val NOTIFICATION = 41
        var running = false
            private set
        fun start(context: Context, ip: String) {
            context.startForegroundService(Intent(context, MicrophoneService::class.java).setAction(START).putExtra("ip", ip))
        }
        fun stop(context: Context) { context.stopService(Intent(context, MicrophoneService::class.java)) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SessionRepository
    private var updates: Job? = null
    private var lease: Job? = null
    private var cpu: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    protected open fun createRepository(): SessionRepository = SessionRuntime.get(this)

    override fun onCreate() {
        super.onCreate()
        repository = createRepository()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "ไมโครโฟนขณะปิดจอ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "แสดงขณะส่งเสียงไมค์ไปยังเครื่องเล่น พร้อมปุ่มหยุด"
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch { repository.pause(); stopSelf() }
            return START_NOT_STICKY
        }
        val ip = intent?.getStringExtra("ip")
        if (intent?.action != START || ip.isNullOrBlank() || repository.state.value.mode != Mode.MIC ||
            !repository.continuesInBackground || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            scope.launch { if (repository.state.value.mode == Mode.MIC) repository.pause(); stopSelf() }
            return START_NOT_STICKY
        }
        try {
            val notification = notification("กำลังเชื่อมต่อเครื่องเล่น…")
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION, notification)
            running = true
            acquireNetworkLease()
            scope.launch { repository.connect(ip) }
            updates?.cancel()
            updates = scope.launch {
                var previous = ""
                repository.state.collect { state ->
                    if (state.mode != Mode.MIC || !repository.continuesInBackground) { stopSelf(); return@collect }
                    val message = if (state.peers > 0) "กำลังส่งเสียงไมค์ • ปิดหน้าจอได้" else state.status
                    if (message != previous) {
                        previous = message
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
                    }
                }
            }
        } catch (_: SecurityException) {
            scope.launch { repository.pause(); stopSelf() }
        } catch (_: IllegalStateException) {
            scope.launch { repository.pause(); stopSelf() }
        }
        // Never resume recording automatically after a process kill or reboot.
        return START_NOT_STICKY
    }
    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MicrophoneService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle("KaraokeLink • ไมโครโฟนกำลังทำงาน").setContentText(message)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "หยุดไมค์", stop).build()).build()
    }
    @Suppress("DEPRECATION")
    private fun acquireNetworkLease() {
        if (cpu == null) cpu = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KaraokeLink:live-microphone").apply { setReferenceCounted(false) }
        if (wifi == null) wifi = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KaraokeLink:audio-wifi").apply { setReferenceCounted(false); acquire() }
        if (lease == null) lease = scope.launch {
            // A bounded, refreshed CPU lease; destruction always releases both locks.
            while (isActive) { cpu?.acquire(60_000); delay(30_000) }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        running = false
        updates?.cancel(); lease?.cancel()
        if (repository.state.value.mode == Mode.MIC && repository.continuesInBackground) {
            scope.launch { repository.pause() }
        }
        cpu?.let { if (it.isHeld) it.release() }; cpu = null
        wifi?.let { if (it.isHeld) it.release() }; wifi = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }
}