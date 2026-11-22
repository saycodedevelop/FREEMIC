package com.karaokelink.core.network

import java.net.InetAddress
import java.security.SecureRandom

/** Main-thread host control table; each microphone owns a distinct native mixer slot. */
internal class HostSessions(
    private val now: () -> Long,
    private val allow: (Int, String) -> Boolean,
    private val remove: (Int) -> Unit,
    private val send: (String, InetAddress, Int) -> Unit
) {
    companion object { const val MAX_MICROPHONES = 2; const val TIMEOUT_MS = 4000L }
    private data class Peer(val id: Int, val ip: InetAddress, val port: Int, val nonce: String, var seen: Long)
    private val peers = mutableListOf<Peer>()
    private val random = SecureRandom()
    val size get() = peers.size

    fun receive(parts: List<String>, ip: InetAddress, port: Int) {
        if (parts.size < 3 || parts[0] != "KL1") return
        expire()
        if (parts[1] == "CONNECT" && parts.size == 3 && parts[2].length in 1..32) {
            var peer = peers.find { it.ip == ip && it.port == port && it.nonce == parts[2] }
            if (peer == null) {
                if (peers.size >= MAX_MICROPHONES) { send("KL1 FULL ${parts[2]}", ip, port); return }
                var id: Int
                do { id = random.nextInt(Int.MAX_VALUE - 1) + 1 } while (peers.any { it.id == id })
                if (!allow(id, ip.hostAddress!!)) return
                peer = Peer(id, ip, port, parts[2], now()); peers += peer
            }
            peer.seen = now()
            send("KL1 ACCEPT ${peer.nonce} ${peer.id}", ip, port)
        } else {
            val id = parts[2].toIntOrNull() ?: return
            val peer = peers.find { it.id == id && it.ip == ip && it.port == port } ?: return
            if (parts[1] == "PING" && parts.size == 4 && parts[3].length < 24) {
                peer.seen = now(); send("KL1 PONG $id ${parts[3]}", ip, port)
            } else if (parts[1] == "BYE" && parts.size == 3) { remove(id); peers.remove(peer) }
        }
    }
    fun expire() {
        val time = now()
        peers.filter { time - it.seen > TIMEOUT_MS }.toList().forEach { remove(it.id); peers.remove(it) }
    }
    fun clear() { peers.clear() }
}
