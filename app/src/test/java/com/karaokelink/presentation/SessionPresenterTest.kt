package com.karaokelink.presentation

import com.karaokelink.domain.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "th")
class SessionPresenterTest {
    private val context: android.content.Context get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test fun microphoneOnlyPromisesScreenOffAfterConnectionAndService() {
        val waiting = SessionPresenter.describe(context, SessionState(mode = Mode.MIC), false)
        assertEquals(StatusTone.WAITING, waiting.tone)
        assertFalse(waiting.detail.contains("ปิดหน้าจอ"))
        val connected = SessionPresenter.describe(context, SessionState(mode = Mode.MIC, peers = 1), true)
        assertEquals("ไมค์พร้อมร้อง", connected.title)
        assertTrue(connected.detail.contains("ปิดหน้าจอ"))
    }
    @Test fun disconnectedMicrophoneDoesNotLookLive() {
        val state = SessionPresenter.describe(context, SessionState(mode = Mode.MIC, status = "Host ไม่ตอบกลับ กรุณาเชื่อมต่อใหม่"), false)
        assertEquals(StatusTone.ERROR, state.tone)
        assertEquals("ยังเชื่อมต่อไม่ได้", state.title)
    }
    @Test fun hostShowsUserTaskInsteadOfProtocolDetails() {
        val state = SessionPresenter.describe(context, SessionState(mode = Mode.HOST, status = "Host พร้อมรับไมค์ 2 เครื่อง • UDP 40102"), false)
        assertEquals("เครื่องเล่นพร้อมแล้ว", state.title)
        assertFalse(state.detail.contains("UDP"))
    }
    @Test fun deviceErrorTakesPrecedenceOverConnectedState() {
        val state = SessionPresenter.describe(context, SessionState(mode = Mode.MIC, peers = 1, engineError = true), true)
        assertEquals(StatusTone.ERROR, state.tone)
        assertEquals("ตรวจสอบอุปกรณ์เสียง", state.title)
    }
}