package com.karaokelink.presentation

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.karaokelink.R
import com.karaokelink.domain.*
import com.karaokelink.service.MicrophoneService
import com.karaokelink.service.MicrophoneServiceTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLanguageTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun allFiveLanguagesRenderRolesGuideErrorsAndNotifications() {
        val cases = listOf(
            "en" to "Start music player", "th" to "เปิดเครื่องเล่นเพลง",
            "zh-rCN" to "启动音乐播放器", "vi" to "Mở máy phát nhạc", "in" to "Buka pemutar musik")
        for ((qualifier, expected) in cases) {
            RuntimeEnvironment.setQualifiers("$qualifier-w393dp-h852dp-xhdpi")
            val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
            val activity = controller.get()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(expected, activity.getString(R.string.start_player))
            val decor = activity.window.decorView
            val density = activity.resources.displayMetrics.density
            val width = (393 * density).toInt(); val height = (852 * density).toInt()
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, width, height)
            for (id in listOf(R.string.start_player, R.string.use_mic)) {
                val button = all(decor).filterIsInstance<Button>().single { it.text == activity.getString(id) }
                val position = IntArray(2); button.getLocationInWindow(position)
                assertTrue("$qualifier: role button is below screen", position[1] + button.height <= height)
                assertTrue(button.height >= 48 * density)
            }
            all(decor).single { it.tag == "language-picker" }.performClick()
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(6, picker.listView.adapter.count)
            picker.dismiss()
            val guide = GettingStarted.show(activity) {}
            assertTrue(all(guide.window!!.decorView).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.setup_title) })
            guide.dismiss()
            val error = SessionPresenter.describe(activity, SessionState(mode = Mode.MIC, status = "Host มีไมค์ครบ 2 เครื่องแล้ว"), false)
            assertEquals(StatusTone.ERROR, error.tone)
            assertEquals(activity.getString(R.string.error_full), error.detail)
            val status = SessionPresenter.describe(activity, SessionState(mode = Mode.HOST, peers = 2), false)
            assertTrue(status.title.contains("2"))
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
            val serviceController = Robolectric.buildService(MicrophoneServiceTest.TestService::class.java).create()
            val service = serviceController.get()
            service.onStartCommand(Intent().setAction(MicrophoneService.START).putExtra("ip", "192.168.1.10"), 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            val notification = shadowOf(service).lastForegroundNotification
            assertEquals(activity.getString(R.string.stop_mic), notification.actions.single().title)
            serviceController.destroy()
            controller.pause().stop().destroy()
        }
    }

    @Test fun unsupportedSystemLanguageFallsBackToEnglish() {
        RuntimeEnvironment.setQualifiers("fr")
        assertEquals("Start music player", RuntimeEnvironment.getApplication().getString(R.string.start_player))
    }

    @Test fun android13LanguageSelectionUsesSystemAppLocalesAndCanReset() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        val manager = activity.getSystemService(LocaleManager::class.java)
        for (tag in listOf("th", "en", "zh-CN", "vi", "id", "")) {
            AppLanguage.select(activity, tag)
            assertEquals(tag, manager.applicationLocales.toLanguageTags())
        }
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [28], qualifiers = "en")
    fun olderAndroidPersistsLanguageForActivityAndServiceContextsAndResets() {
        val controller = Robolectric.buildActivity(PlayerRegressionTest.TestActivity::class.java).setup()
        val activity = controller.get()
        val application: Context = activity.applicationContext
        AppLanguage.select(activity, "vi")
        assertEquals("vi", AppLanguage.selected(application))
        assertEquals("Mở máy phát nhạc", AppLanguage.wrap(application).getString(R.string.start_player))
        val serviceController = Robolectric.buildService(MicrophoneServiceTest.TestService::class.java).create()
        assertEquals("Dừng micrô", serviceController.get().getString(R.string.stop_mic))
        serviceController.destroy()
        AppLanguage.select(activity, "")
        assertEquals("Start music player", AppLanguage.wrap(application).getString(R.string.start_player))
        controller.pause().stop().destroy()
    }
}
