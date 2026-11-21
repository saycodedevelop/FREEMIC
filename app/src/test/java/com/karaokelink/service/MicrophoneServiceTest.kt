package com.karaokelink.service

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import com.karaokelink.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MicrophoneServiceTest {
    class FakeRepository : SessionRepository {
        override val state = MutableStateFlow(SessionState(mode = Mode.MIC))
        var background = true
        var connected = 0
        var paused = 0
        var beforeCapture: () -> Unit = {}
        override val continuesInBackground: Boolean get() = background
        override suspend fun connect(ip: String) { beforeCapture(); connected++; state.value = state.value.copy(peers = 1) }
        override suspend fun pause() { paused++; background = false; state.value = state.value.copy(peers = 0) }
        override suspend fun select(mode: Mode) { state.value = SessionState(mode = mode) }
        override fun effects(value: MicEffects) {}
        override fun hostMix(gain: Float, delayMs: Int) {}
        override fun inputDevice(id: Int) {}
        override suspend fun outputDevice(id: Int) {}
        override suspend fun close() { pause() }
        override fun destroy() {}
    }
    class TestService : MicrophoneService() {
        val fake = FakeRepository()
        override fun createRepository(): SessionRepository = fake
    }
    private fun startIntent() = Intent().setAction(MicrophoneService.START).putExtra("ip", "192.168.1.10")
    private fun grant() { shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) }

    @Test fun foregroundNotificationPrecedesCaptureAndStopReleasesWakeLock() {
        grant()
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        service.fake.beforeCapture = { assertNotNull(shadowOf(service).lastForegroundNotification) }
        assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(startIntent(), 0, 1))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, service.fake.connected)
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals("หยุดไมค์", notification.actions.single().title)
        assertEquals(MicrophoneService.STOP, shadowOf(notification.actions.single().actionIntent).savedIntent.action)
        val wake = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wake.isHeld)
        service.onStartCommand(Intent().setAction(MicrophoneService.STOP), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, service.fake.paused)
        assertFalse(service.fake.background)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        assertFalse(wake.isHeld)
        assertFalse(MicrophoneService.running)
    }
    @Test fun missingMicrophonePermissionNeverStartsCapture() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, service.fake.connected)
        assertNull(shadowOf(service).lastForegroundNotification)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun processRestartDoesNotAutomaticallyRecord() {
        grant()
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertEquals(0, service.fake.connected)
        controller.destroy()
    }
    @Test fun connectionFailureStopsForegroundService() {
        grant()
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        service.onStartCommand(startIntent(), 0, 1)
        service.fake.background = false
        service.fake.state.value = service.fake.state.value.copy(peers = 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun manifestDeclaresMicrophoneForegroundService() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getServiceInfo(ComponentName(app, MicrophoneService::class.java), 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        assertFalse(info.exported)
    }
}