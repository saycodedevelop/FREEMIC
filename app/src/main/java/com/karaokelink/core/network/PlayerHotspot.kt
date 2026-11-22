package com.karaokelink.core.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

internal data class HotspotState(
    val starting: Boolean = false,
    val ssid: String? = null,
    val password: String? = null,
    val failed: Boolean = false
)

/** Own only the reservation created by this app; never turn off a user's system hotspot. */
@Suppress("DEPRECATION")
internal class PlayerHotspot(context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var generation = 0
    var state = HotspotState()
        private set
    var onChanged: () -> Unit = {}
    private fun update(value: HotspotState) { state = value; onChanged() }

    fun start() {
        if (state.starting || reservation != null) return
        val epoch = ++generation
        update(HotspotState(starting = true))
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    if (epoch != generation) { value.close(); return }
                    val credentials = if (Build.VERSION.SDK_INT >= 30) {
                        value.softApConfiguration?.let { it.ssid to it.passphrase }
                    } else {
                        value.wifiConfiguration?.let { it.SSID?.removeSurrounding("\"") to it.preSharedKey?.removeSurrounding("\"") }
                    }
                    val ssid = credentials?.first
                    val password = credentials?.second
                    if (ssid.isNullOrBlank()) { value.close(); update(HotspotState(failed = true)); return }
                    reservation = value
                    update(HotspotState(ssid = ssid, password = password))
                }
                override fun onStopped() {
                    if (epoch == generation) { reservation = null; update(HotspotState()) }
                }
                override fun onFailed(reason: Int) {
                    if (epoch == generation) { reservation = null; update(HotspotState(failed = true)) }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            if (epoch == generation) update(HotspotState(failed = true))
        } catch (_: RuntimeException) {
            if (epoch == generation) update(HotspotState(failed = true))
        }
    }

    fun stop() {
        generation++
        reservation?.close(); reservation = null
        update(HotspotState())
    }
}
