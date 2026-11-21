package com.karaokelink.presentation

import android.app.AlertDialog
import android.media.AudioManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import com.karaokelink.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerRegressionTest {
    class TestActivity : MainActivity() {
        val fake = FakeRepository()
        override fun createRepository(): SessionRepository = fake
    }
    class FakeRepository : SessionRepository {
        override val state = MutableStateFlow(SessionState())
        var pauses = 0
        var background = false
        var selections = 0
        var destructions = 0
        override val continuesInBackground: Boolean get() = background
        override suspend fun select(mode: Mode) { selections++; state.value = SessionState(mode = mode, status = "พร้อม") }
        override suspend fun connect(ip: String) {}
        override fun effects(value: MicEffects) {}
        override fun hostMix(gain: Float, delayMs: Int) {}
        override fun inputDevice(id: Int) {}
        override suspend fun outputDevice(id: Int) {}
        override suspend fun close() { state.value = SessionState() }
        override suspend fun pause() { pauses++; state.value = state.value.copy(peers = 0) }
        override fun destroy() { destructions++ }
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views(activity: TestActivity) = all(activity.window.decorView)
    private fun click(activity: TestActivity, label: String) {
        views(activity).filterIsInstance<Button>().single { it.text.toString() == label }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun browser(activity: TestActivity) = views(activity).filterIsInstance<WebView>().single()

    @Test fun hostKeepsOneFocusOwnerForEmbeddedMusic() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        assertEquals(PlayerNavigation.HOME, shadowOf(browser(activity)).lastLoadedUrl)
        // No competing native focus listener can close the session as WebView takes focus.
        val manager = activity.getSystemService(AudioManager::class.java)
        assertNull(shadowOf(manager).lastAudioFocusRequest)
        controller.pause().stop().destroy()
    }

    @Test fun switchingToBrowserAndBackKeepsHostAndSameWebView() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val original = browser(activity)
        controller.pause().stop()
        assertEquals(1, activity.fake.pauses)
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        assertSame(original, browser(activity))
        controller.pause().stop().destroy()
    }

    @Test fun leavingPlayerRequiresExplicitConfirmation() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        click(activity, "หน้าแรก")
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        click(activity, "หน้าแรก")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Mode.HOME, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }

    @Test fun songSearchLoadsInsideExistingPlayer() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val original = browser(activity)
        views(activity).filterIsInstance<EditText>().single().setText("เพลงทดสอบ")
        click(activity, "ค้นหา")
        assertSame(original, browser(activity))
        assertEquals(PlayerNavigation.resolve("เพลงทดสอบ"), shadowOf(original).lastLoadedUrl)
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }

    @Test fun rendererFailureCanReloadWithoutLeavingHost() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val original = browser(activity)
        assertTrue(original.webViewClient.onRenderProcessGone(original, object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }))
        click(activity, "โหลดใหม่")
        assertNotSame(original, browser(activity))
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }

    @Suppress("DEPRECATION")
    @Test fun backFromFullscreenKeepsPlayerAndHost() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val original = browser(activity)
        var hidden = false
        original.webChromeClient!!.onShowCustomView(View(activity), WebChromeClient.CustomViewCallback { hidden = true })
        activity.onBackPressed()
        assertTrue(hidden)
        assertSame(original, browser(activity))
        assertEquals(Mode.HOST, activity.fake.state.value.mode)
        controller.pause().stop().destroy()
    }

    @Test fun smallPhoneStillHasRoomForVideo() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (640 * density).toInt()
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, width, height)
        val viewport = browser(activity).parent as View
        // Robolectric does not lay out Chromium contents; the parent owns the video viewport.
        assertTrue("Video viewport is too small: ", viewport.height >= 180 * density)
        controller.pause().stop().destroy()
    }
    @Test
    @Config(qualifiers = "land")
    fun landscapeKeepsUsableVideoArea() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        val density = activity.resources.displayMetrics.density
        val width = (640 * density).toInt()
        val height = (360 * density).toInt()
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, width, height)
        assertTrue((browser(activity).parent as View).height >= 120 * density)
        controller.pause().stop().destroy()
    }

    @Test fun activityRecreationRestoresPlayerAndSongUrl() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        click(activity, "เปิดเครื่องเล่นเพลง")
        views(activity).filterIsInstance<EditText>().single().setText("https://example.com/song")
        click(activity, "ค้นหา")
        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        val restored = controller.get()
        assertEquals(Mode.HOST, restored.fake.state.value.mode)
        assertEquals("https://example.com/song", shadowOf(browser(restored)).lastLoadedUrl)
        controller.pause().stop().destroy()
    }
    @Test fun screenOffAndActivityDestructionKeepActiveMicrophoneAlive() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()
        activity.fake.state.value = SessionState(mode = Mode.MIC, peers = 1)
        activity.fake.background = true
        shadowOf(Looper.getMainLooper()).idle()
        val selections = activity.fake.selections
        assertEquals(0, activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.pause().stop()
        assertEquals(0, activity.fake.pauses)
        controller.start().resume().visible()
        assertEquals(selections, activity.fake.selections)
        assertEquals(1, activity.fake.state.value.peers)
        controller.pause().stop().destroy()
        assertEquals(0, activity.fake.destructions)
    }}