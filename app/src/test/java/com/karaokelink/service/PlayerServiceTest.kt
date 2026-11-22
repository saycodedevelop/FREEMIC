package com.karaokelink.service

import android.Manifest
import android.app.Service
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
@Config(sdk = [35], qualifiers = "th")
class PlayerServiceTest {
    class FakeRepository : SessionRepository {
        override val state = MutableStateFlow(SessionState(mode = Mode.HOST, peers = 2))
        var background = true
        var paused = 0
        var closed = 0
        override val continuesInBackground get() = background
        override suspend fun select(mode: Mode) { state.value = SessionState(mode = mode) }
        override suspend fun connect(ip: String) {}
        override fun effects(value: MicEffects) {}
        override fun hostMix(gain: Float, delayMs: Int) {}
        override fun inputDevice(id: Int) {}
        override suspend fun outputDevice(id: Int) {}
        override suspend fun close() { closed++; background = false; state.value = SessionState() }
        override suspend fun pause() { paused++; background = false; state.value = state.value.copy(peers = 0) }
        override fun destroy() {}
    }
    class TestService : PlayerService() {
        val fake = FakeRepository()
        override fun createRepository(): SessionRepository = fake
    }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun start(service: TestService) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent().setAction(PlayerService.START), 0, 1))
        idle()
    }
    @Test fun foregroundNotificationAndLeasePersistUntilExplicitStop() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        start(service)
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals("หยุดเครื่องเล่น", notification.actions.single().title)
        assertEquals(PlayerService.STOP, shadowOf(notification.actions.single().actionIntent).savedIntent.action)
        val wake = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wake.isHeld); assertEquals(2, service.fake.state.value.peers)
        service.onStartCommand(Intent().setAction(PlayerService.STOP), 0, 2); idle()
        assertEquals(1, service.fake.closed); assertEquals(Mode.HOME, service.fake.state.value.mode)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy(); assertFalse(wake.isHeld)
    }
    @Test fun unexpectedDestructionStopsOwnedSessionAndReleasesLease() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get(); start(service)
        val wake = ShadowPowerManager.getLatestWakeLock()
        controller.destroy()
        assertEquals(1, service.fake.paused); assertFalse(wake.isHeld)
    }
    @Test fun serviceDestructionNeverStopsANewMicrophoneRole() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get(); start(service)
        service.fake.state.value = SessionState(mode = Mode.MIC, peers = 1); idle()
        controller.destroy()
        assertEquals(0, service.fake.paused); assertEquals(1, service.fake.state.value.peers)
    }
    @Test fun processRestartDoesNotResumePlayerAutomatically() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertNull(shadowOf(service).lastForegroundNotification)
        controller.destroy(); assertEquals(0, service.fake.paused)
    }
    @Test fun audioDeviceFailureStopsTheServiceSession() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get(); start(service)
        service.fake.state.value = service.fake.state.value.copy(engineError = true); idle()
        assertEquals(1, service.fake.paused)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun manifestDeclaresMediaPlaybackService() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getServiceInfo(ComponentName(app, PlayerService::class.java), 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, info.foregroundServiceType)
        assertFalse(info.exported)
    }
}
