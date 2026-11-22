package com.karaokelink.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID

/** DNS-SD supplements broadcast. All mutable state belongs to the main looper. */
@Suppress("DEPRECATION")
internal class NsdDiscovery(context: Context, private val resolved: (String, Set<InetAddress>) -> Unit) {
    companion object { const val TYPE = "_karaokelink._udp." }
    private val manager = runCatching { context.getSystemService(NsdManager::class.java) }.getOrNull()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var epoch = 0
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val services = linkedMapOf<String, NsdServiceInfo>()
    private val pending = linkedSetOf<String>()
    private var resolving = false

    fun start(host: Boolean) {
        stop()
        val current = epoch
        val manager = manager ?: return
        if (host) {
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    handler.post { if (current != epoch) runCatching { manager.unregisterService(this) } }
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w("KaraokeLink.NSD", "Registration failed: $code") }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) { Log.w("KaraokeLink.NSD", "Unregistration failed: $code") }
            }
            registration = listener
            runCatching {
                manager.registerService(NsdServiceInfo().apply {
                    serviceName = "KaraokeLink-${UUID.randomUUID().toString().take(8)}"
                    serviceType = TYPE
                    port = LanTransport.CONTROL
                }, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { Log.w("KaraokeLink.NSD", "Broadcast discovery remains available", it) }
        } else {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    handler.post { if (current != epoch) runCatching { manager.stopServiceDiscovery(this) } }
                }
                override fun onServiceFound(info: NsdServiceInfo) { handler.post {
                    if (current == epoch && info.serviceType.trimEnd('.') == TYPE.trimEnd('.')) {
                        services[info.serviceName] = info
                        pending += info.serviceName
                        resolveNext(current)
                    }
                } }
                override fun onServiceLost(info: NsdServiceInfo) { handler.post {
                    if (current == epoch) {
                        services.remove(info.serviceName); pending.remove(info.serviceName)
                        resolved(info.serviceName, emptySet())
                    }
                } }
                override fun onDiscoveryStopped(type: String) {}
                override fun onStartDiscoveryFailed(type: String, code: Int) { Log.w("KaraokeLink.NSD", "Discovery failed: $code") }
                override fun onStopDiscoveryFailed(type: String, code: Int) { Log.w("KaraokeLink.NSD", "Stop discovery failed: $code") }
            }
            discovery = listener
            runCatching { manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { Log.w("KaraokeLink.NSD", "Broadcast discovery remains available", it) }
        }
    }

    fun refresh() { val current = epoch; handler.post {
        if (current == epoch) { pending += services.keys; resolveNext(current) }
    } }

    private fun resolveNext(current: Int) {
        if (resolving || current != epoch) return
        val name = pending.firstOrNull() ?: return
        pending.remove(name)
        val info = services[name] ?: return resolveNext(current)
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, code: Int) { complete(null) }
            override fun onServiceResolved(service: NsdServiceInfo) { complete(service) }
            private fun complete(service: NsdServiceInfo?) { handler.post {
                if (current == epoch) {
                    resolving = false
                    // Ignore a resolution completed after the service was lost/replaced.
                    if (services[name] === info && service != null && service.port == LanTransport.CONTROL) {
                        val addresses = if (Build.VERSION.SDK_INT >= 34) service.hostAddresses else listOfNotNull(service.host)
                        resolved(name, addresses.filterIsInstance<Inet4Address>().toSet())
                    }
                    resolveNext(current)
                }
            } }
        }
        runCatching { manager?.resolveService(info, listener) }.onFailure { listener.onResolveFailed(info, -1) }
    }

    fun stop() {
        epoch++
        discovery?.let { runCatching { manager?.stopServiceDiscovery(it) } }
        registration?.let { runCatching { manager?.unregisterService(it) } }
        discovery = null; registration = null
        services.keys.toList().forEach { resolved(it, emptySet()) }
        services.clear(); pending.clear(); resolving = false
    }
}
