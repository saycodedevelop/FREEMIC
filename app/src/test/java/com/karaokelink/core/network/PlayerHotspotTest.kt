package com.karaokelink.core.network

import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], shadows = [PlayerHotspotTest.FakeWifi::class, PlayerHotspotTest.FakeReservation::class, PlayerHotspotTest.FakeConfiguration::class])
class PlayerHotspotTest {
    @Implements(WifiManager::class)
    class FakeWifi {
        companion object {
            var callback: WifiManager.LocalOnlyHotspotCallback? = null
            var requests = 0
            var denied = false
        }
        @Implementation fun startLocalOnlyHotspot(value: WifiManager.LocalOnlyHotspotCallback, handler: Handler) {
            if (denied) throw SecurityException("Denied")
            requests++; callback = value
        }
    }
    @Implements(WifiManager.LocalOnlyHotspotReservation::class)
    class FakeReservation {
        companion object { var closed = 0 }
        @Implementation fun close() { closed++ }
        @Implementation fun getSoftApConfiguration(): SoftApConfiguration =
            Shadow.newInstanceOf(SoftApConfiguration::class.java)
    }
    @Implements(SoftApConfiguration::class)
    class FakeConfiguration {
        @Implementation fun getSsid(): String = "KaraokeLink-Test"
        @Implementation fun getPassphrase(): String = "singalong123"
    }
    private lateinit var hotspot: PlayerHotspot
    private fun reservation(): WifiManager.LocalOnlyHotspotReservation =
        Shadow.newInstanceOf(WifiManager.LocalOnlyHotspotReservation::class.java)

    @Before fun setup() {
        FakeWifi.callback = null; FakeWifi.requests = 0; FakeWifi.denied = false; FakeReservation.closed = 0
        hotspot = PlayerHotspot(RuntimeEnvironment.getApplication())
    }

    @Test fun startIsIdempotentAndStopReleasesOnlyOwnedReservation() {
        hotspot.start(); hotspot.start()
        assertEquals(1, FakeWifi.requests); assertTrue(hotspot.state.starting)
        FakeWifi.callback!!.onStarted(reservation())
        assertEquals("KaraokeLink-Test", hotspot.state.ssid)
        assertEquals("singalong123", hotspot.state.password)
        assertFalse(hotspot.state.starting)
        hotspot.stop(); hotspot.stop()
        assertEquals(1, FakeReservation.closed); assertNull(hotspot.state.ssid)
    }
    @Test fun delayedStartAfterStopClosesReservationWithoutResurrectingHotspot() {
        hotspot.start()
        val old = FakeWifi.callback!!
        hotspot.stop(); hotspot.start()
        old.onStarted(reservation()); old.onFailed(0); old.onStopped()
        assertEquals(1, FakeReservation.closed); assertTrue(hotspot.state.starting)
        FakeWifi.callback!!.onStarted(reservation())
        assertEquals("KaraokeLink-Test", hotspot.state.ssid)
        hotspot.stop()
    }
    @Test fun platformFailureAndDeniedPermissionCanBeRetried() {
        FakeWifi.denied = true
        hotspot.start(); assertTrue(hotspot.state.failed); assertFalse(hotspot.state.starting)
        FakeWifi.denied = false
        hotspot.start(); FakeWifi.callback!!.onFailed(0)
        assertTrue(hotspot.state.failed)
        hotspot.start(); FakeWifi.callback!!.onStarted(reservation())
        assertNotNull(hotspot.state.ssid); hotspot.stop()
    }
    @Test fun systemStopClearsCredentialsAndAllowsAnotherStart() {
        hotspot.start(); FakeWifi.callback!!.onStarted(reservation())
        FakeWifi.callback!!.onStopped()
        assertNull(hotspot.state.password); assertNull(hotspot.state.ssid)
        hotspot.start(); assertEquals(2, FakeWifi.requests)
        hotspot.stop()
    }
}
