package com.karaokelink.presentation

import android.net.wifi.WifiManager
import android.os.Looper
import com.karaokelink.core.network.PlayerHotspotTest
import com.karaokelink.data.SessionRuntime
import com.karaokelink.domain.Mode
import com.karaokelink.domain.SessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "th", shadows = [PlayerHotspotTest.FakeWifi::class,
    PlayerHotspotTest.FakeReservation::class, PlayerHotspotTest.FakeConfiguration::class])
class PlayerHotspotRetentionTest {
    @Test fun destroyingPlayerActivityKeepsSessionOwnedHotspotAlive() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        val hotspot = SessionRuntime.hotspot(RuntimeEnvironment.getApplication())
        activity.fake.background = true
        activity.fake.state.value = SessionState(mode = Mode.HOST)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.fake.continuesInBackground)
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        PlayerHotspotTest.FakeWifi.denied = false
        PlayerHotspotTest.FakeReservation.closed = 0
        var destroyed = false
        try {
            hotspot.start()
            PlayerHotspotTest.FakeWifi.callback!!.onStarted(Shadow.newInstanceOf(WifiManager.LocalOnlyHotspotReservation::class.java))
            controller.pause().stop().destroy(); destroyed = true
            assertEquals(0, PlayerHotspotTest.FakeReservation.closed)
            assertEquals("KaraokeLink-Test", hotspot.state.ssid)
            assertSame(hotspot, SessionRuntime.hotspot(RuntimeEnvironment.getApplication()))
        } finally {
            SessionRuntime.stopHotspot()
            if (!destroyed) controller.pause().stop().destroy()
        }
        assertEquals(1, PlayerHotspotTest.FakeReservation.closed)
    }
}
