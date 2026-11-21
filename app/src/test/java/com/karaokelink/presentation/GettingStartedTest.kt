package com.karaokelink.presentation

import android.app.Dialog
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
@Config(sdk = [35])
class GettingStartedTest {
    class FirstRunActivity : MainActivity() {
        val fake = PlayerRegressionTest.FakeRepository()
        override fun createRepository(): SessionRepository = fake
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun click(dialog: Dialog, title: String) {
        all(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text.toString() == title }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun hasText(dialog: Dialog, text: String) = all(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == text }

    @Test fun firstRunGuideExplainsBothRolesAndCanBeCompleted() {
        val controller = Robolectric.buildActivity(FirstRunActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val guide = ShadowDialog.getLatestDialog()
        assertTrue(guide.isShowing)
        assertTrue(hasText(guide, "สองเครื่อง เวทีเดียวกัน"))
        click(guide, "ถัดไป")
        assertTrue(hasText(guide, "เปิดเพลงบนเครื่องนี้"))
        click(guide, "ย้อนกลับ")
        assertTrue(hasText(guide, "สองเครื่อง เวทีเดียวกัน"))
        click(guide, "ถัดไป"); click(guide, "ถัดไป")
        assertTrue(hasText(guide, "เชื่อมต่อ แล้วเริ่มร้อง"))
        click(guide, "เริ่มใช้งาน")
        assertFalse(guide.isShowing)
        assertTrue(activity.getSharedPreferences("karaokelink-ui", 0).getBoolean("welcomeComplete", false))
        assertEquals(Mode.HOME, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }
    @Test fun skipIsRememberedAndHelpCanBeOpenedAgain() {
        val controller = Robolectric.buildActivity(FirstRunActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val guide = ShadowDialog.getLatestDialog()
        click(guide, "ข้าม")
        assertTrue(activity.getSharedPreferences("karaokelink-ui", 0).getBoolean("welcomeComplete", false))
        all(activity.window.decorView).filterIsInstance<ImageButton>().single { it.contentDescription == "วิธีใช้งาน" }.performClick()
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        ShadowDialog.getLatestDialog().dismiss()
        controller.pause().stop().destroy()
    }
    @Test fun helpWhilePlayingPreservesBrowserAndSession() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "เปิดเครื่องเล่นเพลง" }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val browser = all(activity.window.decorView).filterIsInstance<WebView>().single()
        val transitions = activity.fake.selections
        all(activity.window.decorView).filterIsInstance<ImageButton>().single { it.contentDescription == "วิธีใช้งาน" }.performClick()
        val guide = ShadowDialog.getLatestDialog()
        click(guide, "ข้าม")
        assertSame(browser, all(activity.window.decorView).filterIsInstance<WebView>().single())
        assertEquals(transitions, activity.fake.selections)
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }
    @Test fun connectedMicrophoneShowsControlsAndChangingPlayerDoesNotStopAudio() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        activity.fake.state.value = SessionState(mode = Mode.MIC)
        shadowOf(Looper.getMainLooper()).idle()
        fun tagged(tag: String) = all(activity.window.decorView).single { it.tag == tag }
        assertEquals(View.VISIBLE, tagged("mic-discovery").visibility)
        assertEquals(View.GONE, tagged("mic-controls").visibility)
        activity.fake.background = true
        activity.fake.state.value = SessionState(mode = Mode.MIC, peers = 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, tagged("mic-discovery").visibility)
        assertEquals(View.VISIBLE, tagged("mic-controls").visibility)
        val transitions = activity.fake.selections
        all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "เปลี่ยนเครื่องเล่น" }.performClick()
        assertEquals(View.VISIBLE, tagged("mic-discovery").visibility)
        assertEquals(transitions, activity.fake.selections)
        assertEquals(1, activity.fake.state.value.peers)
        controller.pause().stop().destroy()
    }
}