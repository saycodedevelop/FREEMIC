package com.karaokelink.core.network

import android.content.Context
import android.content.ContextWrapper
import org.robolectric.shadow.api.Shadow
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], shadows = [NsdDiscoveryTest.FakeNsd::class])
@Suppress("DEPRECATION")
class NsdDiscoveryTest {
    @Implements(NsdManager::class)
    class FakeNsd {
        companion object {
            var discovery: NsdManager.DiscoveryListener? = null
            var registration: NsdManager.RegistrationListener? = null
            var advertised: NsdServiceInfo? = null
            val requests = mutableListOf<Pair<NsdServiceInfo, NsdManager.ResolveListener>>()
            var stopped = 0
            var unregistered = 0
        }
        @Implementation fun discoverServices(type: String, protocol: Int, listener: NsdManager.DiscoveryListener) { discovery = listener }
        @Implementation fun registerService(info: NsdServiceInfo, protocol: Int, listener: NsdManager.RegistrationListener) { advertised = info; registration = listener }
        @Implementation fun resolveService(info: NsdServiceInfo, listener: NsdManager.ResolveListener) { requests += info to listener }
        @Implementation fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) { stopped++ }
        @Implementation fun unregisterService(listener: NsdManager.RegistrationListener) { unregistered++ }
    }
    private val addresses = mutableMapOf<String, Set<InetAddress>>()
    private lateinit var nsd: NsdDiscovery
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun info(name: String = "player", ip: String = "192.168.1.10", portNumber: Int = LanTransport.CONTROL) = NsdServiceInfo().apply {
        serviceName = name; serviceType = NsdDiscovery.TYPE; host = InetAddress.getByName(ip); port = portNumber
    }
    @Before fun setup() {
        FakeNsd.discovery = null; FakeNsd.registration = null; FakeNsd.advertised = null
        FakeNsd.requests.clear(); FakeNsd.stopped = 0; FakeNsd.unregistered = 0
        nsd = NsdDiscovery(nsdTestContext()) { name, ips -> addresses[name] = ips }
    }
    @Test fun hostAdvertisesTheControlServiceAndUnregisters() {
        nsd.start(true)
        assertEquals(NsdDiscovery.TYPE, FakeNsd.advertised!!.serviceType)
        assertEquals(LanTransport.CONTROL, FakeNsd.advertised!!.port)
        nsd.stop(); assertEquals(1, FakeNsd.unregistered)
    }
    @Test fun discoveredHostIsResolvedRefreshedAndRemovedOnLoss() {
        nsd.start(false)
        val service = info()
        FakeNsd.discovery!!.onServiceFound(service); idle()
        FakeNsd.requests.last().second.onServiceResolved(service); idle()
        assertEquals(setOf(service.host), addresses["player"])
        nsd.refresh(); idle(); assertEquals(2, FakeNsd.requests.size)
        FakeNsd.discovery!!.onServiceLost(service); idle()
        FakeNsd.requests.last().second.onServiceResolved(service); idle()
        assertTrue(addresses["player"]!!.isEmpty())
        nsd.stop(); assertEquals(1, FakeNsd.stopped)
    }
    @Test fun resolvesAreSerializedAndFailuresAreRetried() {
        nsd.start(false)
        val a = info("a"); val b = info("b")
        FakeNsd.discovery!!.onServiceFound(a); FakeNsd.discovery!!.onServiceFound(b); idle()
        assertEquals(1, FakeNsd.requests.size)
        FakeNsd.requests[0].second.onResolveFailed(a, 3); idle()
        assertEquals("b", FakeNsd.requests[1].first.serviceName)
        FakeNsd.requests[1].second.onServiceResolved(b); idle()
        nsd.refresh(); idle()
        assertEquals("a", FakeNsd.requests.last().first.serviceName)
        nsd.stop()
    }
    @Test fun lateCallbacksFromStoppedSessionCannotResurrectHosts() {
        nsd.start(false)
        val old = FakeNsd.discovery!!
        val service = info()
        old.onServiceFound(service); idle()
        val request = FakeNsd.requests.last().second
        nsd.stop(); nsd.start(false)
        request.onServiceResolved(service); old.onServiceFound(info("stale")); idle()
        assertTrue(addresses.values.all { it.isEmpty() }); assertEquals(1, FakeNsd.requests.size)
        nsd.stop()
    }
    @Test fun unsupportedPortAndIpv6DoNotBecomeDiscoveryTargets() {
        nsd.start(false)
        val wrongPort = info(portNumber = 1234)
        FakeNsd.discovery!!.onServiceFound(wrongPort); idle()
        FakeNsd.requests.last().second.onServiceResolved(wrongPort); idle()
        assertNull(addresses["player"])
        val ipv6 = info("ipv6", "::1")
        FakeNsd.discovery!!.onServiceFound(ipv6); idle()
        FakeNsd.requests.last().second.onServiceResolved(ipv6); idle()
        assertTrue(addresses["ipv6"]!!.isEmpty())
        nsd.stop()
    }
}

/** Supply the fake system daemon without running the framework Binder constructor. */
internal fun nsdTestContext(): Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
    private val nsdManager = Shadow.newInstanceOf(NsdManager::class.java)
    override fun getSystemService(name: String): Any? = if (name == Context.NSD_SERVICE) nsdManager else super.getSystemService(name)
}