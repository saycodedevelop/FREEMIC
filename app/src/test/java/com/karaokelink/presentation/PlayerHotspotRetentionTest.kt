package com.karaokelink.presentation

import android.net.wifi.WifiManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.karaokelink.core.network.PlayerHotspotTest
import com.karaokelink.data.SessionRuntime
import com.karaokelink.domain.Mode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "th", shadows = [PlayerHotspotTest.FakeWifi::class,
    PlayerHotspotTest.FakeReservation::class, PlayerHotspotTest.FakeConfiguration::class])
class PlayerHotspotRetentionTest {
    @Before fun freshApplicationHotspot() {
        // Robolectric creates a new Application per test but does not reset our Kotlin singleton.
        // Its cached WifiManager must belong to this test's Application and shadow configuration.
        SessionRuntime.stopHotspot()
        ReflectionHelpers.setStaticField(SessionRuntime::class.java, "playerHotspot", null)
        PlayerHotspotTest.FakeWifi.callback = null
        PlayerHotspotTest.FakeWifi.requests = 0
        PlayerHotspotTest.FakeWifi.denied = false
        PlayerHotspotTest.FakeReservation.closed = 0
    }

    @After fun releaseApplicationHotspot() {
        SessionRuntime.stopHotspot()
        ReflectionHelpers.setStaticField(SessionRuntime::class.java, "playerHotspot", null)
        PlayerHotspotTest.FakeWifi.callback = null
    }

    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun destroyingPlayerActivityKeepsSessionOwnedHotspotAlive() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        // Follow the user action rather than racing Activity startup with direct fake state writes.
        all(activity.window.decorView).filterIsInstance<Button>()
            .single { it.text == "เปิดเครื่องเล่นเพลง" }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.fake.continuesInBackground)
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        val hotspot = SessionRuntime.hotspot(RuntimeEnvironment.getApplication())
        var destroyed = false
        try {
            hotspot.start()
            assertTrue("Hotspot request must be pending in this test", hotspot.state.starting)
            assertEquals("A new request must reach this test's WifiManager", 1, PlayerHotspotTest.FakeWifi.requests)
            val callback = requireNotNull(PlayerHotspotTest.FakeWifi.callback)
            callback.onStarted(Shadow.newInstanceOf(WifiManager.LocalOnlyHotspotReservation::class.java))
            assertEquals("Hotspot must be active before destroying the Activity", "KaraokeLink-Test", hotspot.state.ssid)
            assertEquals(0, PlayerHotspotTest.FakeReservation.closed)
            val selections = activity.fake.selections
            controller.pause().stop().destroy(); destroyed = true
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Activity shutdown must not reselect the role", selections, activity.fake.selections)
            assertEquals("Activity shutdown must not close the session-owned hotspot", 0, PlayerHotspotTest.FakeReservation.closed)
            assertEquals("KaraokeLink-Test", hotspot.state.ssid)
            assertSame(hotspot, SessionRuntime.hotspot(RuntimeEnvironment.getApplication()))
        } finally {
            SessionRuntime.stopHotspot()
            if (!destroyed) controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals("Explicit session stop must release exactly one reservation", 1, PlayerHotspotTest.FakeReservation.closed)
    }
}
