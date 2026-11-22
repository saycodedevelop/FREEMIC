package com.karaokelink.core.network

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real UDP sockets with a fake NSD daemon; no Wi-Fi, JNI, or broadcast needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NsdDiscoveryTest.FakeNsd::class])
class LanTransportTest {
    @Test fun twoMicrophonesDiscoverByNsdUnicastAndConnectToSameHost() {
        val context = nsdTestContext()
        val host = LanTransport(context) { emptySet() }
        val microphones = List(2) { LanTransport(context) { emptySet() } }
        val found = CountDownLatch(2)
        val accepted = CountDownLatch(2)
        val sessions = ConcurrentHashMap<Int, Int>()
        val names = ConcurrentHashMap<Int, String>()
        val discoveryPorts = mutableSetOf<Int>()
        val table = HostSessions({ System.nanoTime() / 1_000_000 }, { _, _ -> true }, {}) { message, ip, port -> host.command(message, ip, port) }
        host.onControl = { message, ip, port -> table.receive(message.split(' '), ip, port) }
        try {
            NsdDiscoveryTest.FakeNsd.requests.clear()
            host.start(true)
            microphones.forEachIndexed { index, microphone ->
                microphone.onHost = { ip, name ->
                    if (ip == "127.0.0.1" && !sessions.containsKey(index)) {
                        names[index] = name
                        if (sessions.putIfAbsent(index, 0) == null) found.countDown()
                        microphone.command("KL1 CONNECT mic$index", InetAddress.getByName(ip))
                    }
                }
                microphone.onControl = { message, _, _ ->
                    val parts = message.split(' ')
                    if (parts.size == 4 && parts[1] == "ACCEPT" && sessions.replace(index, 0, parts[3].toInt())) accepted.countDown()
                }
                microphone.start(false)
                val service = android.net.nsd.NsdServiceInfo().apply {
                    serviceName = "loopback-player"; serviceType = NsdDiscovery.TYPE
                    @Suppress("DEPRECATION")
                    this.host = InetAddress.getByName("127.0.0.1")
                    port = LanTransport.CONTROL
                }
                NsdDiscoveryTest.FakeNsd.discovery!!.onServiceFound(service)
                shadowOf(Looper.getMainLooper()).idle()
                NsdDiscoveryTest.FakeNsd.requests.last().second.onServiceResolved(service)
                shadowOf(Looper.getMainLooper()).idle()
                val field = LanTransport::class.java.getDeclaredField("discovery").apply { isAccessible = true }
                discoveryPorts += (field.get(microphone) as java.net.DatagramSocket).localPort
            }
            assertTrue("Both microphones must find the host without broadcast", found.await(6, TimeUnit.SECONDS))
            assertTrue("Both microphones must be accepted", accepted.await(6, TimeUnit.SECONDS))
            assertEquals(2, discoveryPorts.size)
            assertFalse(discoveryPorts.contains(LanTransport.DISCOVERY))
            assertEquals(2, sessions.values.toSet().size)
            assertTrue(sessions.values.all { it > 0 })
            assertEquals(2, table.size)
            assertEquals(listOf(DeviceName.current(), DeviceName.current()), listOf(names[0], names[1]))
        } finally { microphones.forEach { it.stop() }; host.stop() }
    }
}
