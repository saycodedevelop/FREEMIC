package com.karaokelink.core.network

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class HostSessionsTest {
    private var time = 100L
    private val active = mutableMapOf<Int, String>()
    private val replies = mutableListOf<Triple<String, InetAddress, Int>>()
    private val host = HostSessions({ time }, { id, ip -> active[id] = ip; true }, { active.remove(it) }) { m, ip, port -> replies += Triple(m, ip, port) }
    private val first = InetAddress.getByName("192.168.1.11")
    private val second = InetAddress.getByName("192.168.1.12")
    private fun command(text: String, ip: InetAddress = first, port: Int = 50001) = host.receive(text.split(' '), ip, port)
    private fun connect(nonce: String, ip: InetAddress = first, port: Int = 50001): Int {
        command("KL1 CONNECT $nonce", ip, port)
        assertEquals(ip, replies.last().second); assertEquals(port, replies.last().third)
        return replies.last().first.removePrefix("KL1 ACCEPT $nonce ").toInt()
    }
    @Test fun twoMicrophonesHaveIndependentSessionsAndHeartbeats() {
        val a = connect("a"); val b = connect("b", second)
        assertNotEquals(a, b); assertEquals(2, host.size); assertEquals(2, active.size)
        command("KL1 PING $a 123")
        assertEquals("KL1 PONG $a 123", replies.last().first)
        command("KL1 PING $b 456", second)
        assertEquals("KL1 PONG $b 456", replies.last().first)
        command("KL1 BYE $a")
        assertEquals(setOf(b), active.keys); assertEquals(1, host.size)
        command("KL1 PING $b 789", second)
        assertEquals("KL1 PONG $b 789", replies.last().first)
    }
    @Test fun lostAcceptRetryDoesNotConsumeAnotherMicrophoneSlot() {
        val a = connect("a")
        assertEquals(a, connect("a")); assertEquals(1, active.size)
        connect("b", second); assertEquals(2, host.size)
        command("KL1 CONNECT c", InetAddress.getByName("192.168.1.13"))
        assertEquals("KL1 FULL c", replies.last().first); assertEquals(2, active.size)
    }
    @Test fun separatePortsOnSameAddressRemainIndependent() {
        assertNotEquals(connect("a"), connect("b", first, 50002))
        assertEquals(2, host.size)
    }
    @Test fun expiredSlotIsAvailableImmediatelyWithoutWaitingForTimer() {
        val a = connect("a"); val b = connect("b", second)
        time += 3500; command("KL1 PING $b 3500", second)
        time += 600
        val c = connect("c", InetAddress.getByName("192.168.1.13"))
        assertEquals(setOf(b, c), active.keys); assertFalse(active.containsKey(a))
    }
    @Test fun anotherMicrophoneCannotStopOrKeepAliveSomeoneElsesSession() {
        val a = connect("a"); connect("b", second)
        val count = replies.size
        command("KL1 BYE $a", second); command("KL1 PING $a 10", second)
        command("KL1 BYE $a", first, 50002)
        assertEquals(2, host.size); assertEquals(count, replies.size)
    }
    @Test fun failedNativeSlotNeverCreatesPhantomPeer() {
        val failed = HostSessions({ time }, { _, _ -> false }, {}) { _, _, _ -> fail("Should not accept failed native slot") }
        failed.receive("KL1 CONNECT a".split(' '), first, 50001)
        assertEquals(0, failed.size)
    }
}
