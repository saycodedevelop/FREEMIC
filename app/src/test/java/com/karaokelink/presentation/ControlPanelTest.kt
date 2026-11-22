package com.karaokelink.presentation

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.karaokelink.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "th")
class ControlPanelTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun click(view: View, text: String) {
        all(view).filterIsInstance<Button>().single { it.text == text }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun mixerCanOpenAndCloseWithoutReplacingPlayerOrRestartingHost() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity.window.decorView, "เปิดเครื่องเล่นเพลง")
        val browser = all(activity.window.decorView).filterIsInstance<WebView>().single()
        val selected = activity.fake.selections
        click(activity.window.decorView, "ตั้งค่าเสียง")
        val sheet = ShadowDialog.getLatestDialog()
        assertTrue(sheet.isShowing)
        assertTrue(all(sheet.window!!.decorView).any { it.tag == "settings-sheet" })
        assertTrue(all(sheet.window!!.decorView).filterIsInstance<TextView>().any { it.text == "มิกเซอร์เสียง" })
        click(sheet.window!!.decorView, "เสร็จแล้ว")
        assertFalse(sheet.isShowing)
        assertSame(browser, all(activity.window.decorView).filterIsInstance<WebView>().single())
        assertEquals(selected, activity.fake.selections)
        controller.pause().stop().destroy()
    }
    @Test fun changingRoleDismissesSettingsFromPreviousRole() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity.window.decorView, "เปิดเครื่องเล่นเพลง")
        click(activity.window.decorView, "ตั้งค่าเสียง")
        val sheet = ShadowDialog.getLatestDialog()
        assertTrue(sheet.isShowing)
        activity.fake.state.value = SessionState(mode = Mode.MIC)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(sheet.isShowing)
        click(activity.window.decorView, "ตั้งค่าไมค์และอุปกรณ์")
        val microphoneSheet = ShadowDialog.getLatestDialog()
        assertTrue(microphoneSheet.isShowing)
        assertNotSame(sheet, microphoneSheet)
        activity.fake.state.value = SessionState(mode = Mode.HOME)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(microphoneSheet.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun configurationCallbackPreservesMixerAndPlayer() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity.window.decorView, "เปิดเครื่องเล่นเพลง")
        val browser = all(activity.window.decorView).filterIsInstance<WebView>().single()
        val selected = activity.fake.selections
        click(activity.window.decorView, "ตั้งค่าเสียง")
        val sheet = ShadowDialog.getLatestDialog()
        val configuration = android.content.res.Configuration(activity.resources.configuration).apply {
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        // The test subclass is not the manifest Activity with configChanges enabled.
        // Deliver the callback handled by MainActivity during a real rotation.
        activity.onConfigurationChanged(configuration)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(sheet.isShowing)
        assertSame(browser, all(activity.window.decorView).filterIsInstance<WebView>().single())
        assertEquals(selected, activity.fake.selections)
        click(sheet.window!!.decorView, "เสร็จแล้ว")
        assertFalse(sheet.isShowing)
        controller.pause().stop().destroy()
    }
    @Test fun lowLatencyToggleDoesNotRestartPlayerAndIsRemembered() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity.window.decorView, "เปิดเครื่องเล่นเพลง")
        val selections = activity.fake.selections
        click(activity.window.decorView, "ตั้งค่าเสียง")
        val dialog = ShadowDialog.getLatestDialog()
        val toggle = all(dialog.window!!.decorView).filterIsInstance<android.widget.Switch>().single()
        toggle.isChecked = false
        assertFalse(activity.fake.fast)
        assertEquals(selections, activity.fake.selections)
        click(dialog.window!!.decorView, "เสร็จแล้ว")
        click(activity.window.decorView, "ตั้งค่าเสียง")
        assertFalse(all(ShadowDialog.getLatestDialog().window!!.decorView).filterIsInstance<android.widget.Switch>().single().isChecked)
        controller.pause().stop().destroy()
    }

    @Test fun microphoneConfigurationIsAvailableBeforeConnecting() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        activity.fake.state.value = SessionState(mode = Mode.MIC)
        shadowOf(Looper.getMainLooper()).idle()
        click(activity.window.decorView, "ตั้งค่าไมค์และอุปกรณ์")
        val sheet = ShadowDialog.getLatestDialog()
        assertTrue(sheet.isShowing)
        assertTrue(all(sheet.window!!.decorView).filterIsInstance<Button>().any { it.text == "เลือกไมค์ในเครื่อง / สาย / USB" })
        all(sheet.window!!.decorView).filterIsInstance<ImageButton>().single { it.contentDescription == "ปิดหน้าต่าง" }.performClick()
        assertFalse(sheet.isShowing)
        assertEquals(Mode.MIC, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }
    @Test fun microphoneHasOneStatusSurfaceAndStopReturnsToDiscovery() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        activity.fake.background = true
        activity.fake.state.value = SessionState(mode = Mode.MIC, peers = 1)
        shadowOf(Looper.getMainLooper()).idle()
        fun tagged(tag: String) = all(activity.window.decorView).single { it.tag == tag }
        assertEquals(View.GONE, tagged("session-status").visibility)
        assertEquals(View.VISIBLE, tagged("microphone-session").visibility)
        click(activity.window.decorView, "หยุดส่งเสียงไมค์")
        assertFalse(activity.fake.background)
        assertEquals(0, activity.fake.state.value.peers)
        assertEquals(View.VISIBLE, tagged("mic-discovery").visibility)
        assertEquals(View.GONE, tagged("microphone-session").visibility)
        controller.pause().stop().destroy()
    }
}