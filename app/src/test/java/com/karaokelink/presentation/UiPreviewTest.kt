package com.karaokelink.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import com.karaokelink.domain.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Real Android/Skia rendering artifacts for visual review, not a browser mockup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiPreviewTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun render(view: View, name: String, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/reports/ui-preview/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun renderHomeLiveMicrophoneAndGuide() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val density = activity.resources.displayMetrics.density
        val width = (393*density).toInt(); val height = (852*density).toInt()
        render(activity.window.decorView, "home", width, height)
        for (label in listOf("เปิดเครื่องเล่นเพลง", "ใช้เครื่องนี้เป็นไมค์")) {
            val action = all(activity.window.decorView).filterIsInstance<Button>().single { it.text == label }
            val position = IntArray(2); action.getLocationInWindow(position)
            assertTrue("Role selection must be visible without scrolling: $label", position[1]+action.height <= height)
            assertTrue(action.height >= 48*density)
        }
        all(activity.window.decorView).filterIsInstance<ImageButton>().single { it.contentDescription == "วิธีใช้งาน" }.performClick()
        val guide = ShadowDialog.getLatestDialog()
        render(guide.window!!.decorView, "getting-started", width, minOf((680*density).toInt(), (height*.88f).toInt()))
        guide.dismiss()
        activity.fake.background = true
        activity.fake.state.value = SessionState(mode = Mode.MIC, peers = 1, vu = 0.62f)
        shadowOf(Looper.getMainLooper()).idle()
        render(activity.window.decorView, "microphone", width, height)
        activity.fake.background = false
        activity.fake.state.value = SessionState(mode = Mode.HOST, peers = 1)
        shadowOf(Looper.getMainLooper()).idle()
        all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "ตั้งค่าเสียง" }.performClick()
        val mixer = ShadowDialog.getLatestDialog()
        render(mixer.window!!.decorView, "mixer", width, minOf((720*density).toInt(), (height*.86f).toInt()))
        mixer.dismiss()
        controller.pause().stop().destroy()
    }
}