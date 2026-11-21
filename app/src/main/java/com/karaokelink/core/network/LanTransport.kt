package com.karaokelink.core.network

import android.os.Build
import kotlinx.coroutines.*
import java.net.*
import java.util.Collections

/** Discovery 40101, control 40100, native PCM 40102. One owner per APK process. */
class LanTransport {
    companion object { const val CONTROL = 40100; const val DISCOVERY = 40101 }
    private var scope: CoroutineScope? = null
    private var discovery: DatagramSocket? = null
    private var control: DatagramSocket? = null
    var onHost: (String, String) -> Unit = { _, _ -> }
    var onControl: (String, InetAddress, Int) -> Unit = { _, _, _ -> }
    var onError: (String) -> Unit = {}

    fun start(host: Boolean) {
        stop()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = owner
        try {
            control = DatagramSocket(if (host) CONTROL else 0).apply { soTimeout = 500 }
            discovery = DatagramSocket(null).apply {
                reuseAddress = true; broadcast = true
                bind(InetSocketAddress(DISCOVERY)); soTimeout = 500
            }
            val controlSocket = control!!
            val discoverySocket = discovery!!
            owner.launch { receive(controlSocket, false, host) }
            owner.launch { receive(discoverySocket, true, host) }
            if (!host) owner.launch {
                while (isActive) {
                    for (address in broadcasts()) send(discovery, "KL1 DISCOVER", address, DISCOVERY)
                    delay(1500)
                }
            }
        } catch (e: Exception) { stop(); throw e }
    }
    private suspend fun receive(socket: DatagramSocket, isDiscovery: Boolean, host: Boolean) {
        val bytes = ByteArray(512)
        while (currentCoroutineContext().isActive && !socket.isClosed) {
            try {
                val packet = DatagramPacket(bytes, bytes.size)
                socket.receive(packet)
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (isDiscovery) {
                    if (host && message == "KL1 DISCOVER") {
                        send(socket, "KL1 HOST ${Build.MODEL.take(60)}", packet.address, packet.port)
                    } else if (!host && message.startsWith("KL1 HOST ")) {
                        onHost(packet.address.hostAddress!!, message.removePrefix("KL1 HOST "))
                    }
                } else onControl(message, packet.address, packet.port)
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                if (!socket.isClosed) onError(e.message ?: "UDP error")
            }
        }
    }
    fun command(message: String, ip: InetAddress, port: Int = CONTROL) {
        val socket = control ?: return
        scope?.launch { send(socket, message, ip, port) }
    }
    private fun send(socket: DatagramSocket?, message: String, ip: InetAddress, port: Int) {
        try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            socket?.send(DatagramPacket(bytes, bytes.size, ip, port))
        } catch (e: Exception) { if (socket?.isClosed == false) onError(e.message ?: "UDP send failed") }
    }
    private fun broadcasts(): Set<InetAddress> = buildSet {
        add(InetAddress.getByName("255.255.255.255"))
        runCatching {
            for (network in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (network.isUp && !network.isLoopback)
                    network.interfaceAddresses.mapNotNullTo(this) { it.broadcast }
            }
        }
    }
    fun stop() {
        scope?.cancel(); scope = null
        discovery?.close(); control?.close()
        discovery = null; control = null
    }
}