package com.karaokelink.presentation

import com.karaokelink.domain.Mode
import com.karaokelink.domain.SessionState

enum class StatusTone { READY, WAITING, ERROR }
data class SessionPresentation(val title: String, val detail: String, val tone: StatusTone)

/** User-facing connection language, separate from protocol/debug messages. */
object SessionPresenter {
    fun describe(state: SessionState, backgroundMic: Boolean): SessionPresentation {
        if (state.engineError) return SessionPresentation("ตรวจสอบอุปกรณ์เสียง", "เชื่อมต่ออุปกรณ์อีกครั้ง แล้วลองเริ่มไมค์ใหม่", StatusTone.ERROR)
        return when (state.mode) {
            Mode.HOME -> SessionPresentation("พร้อมเริ่มใช้งาน", "ใช้มือถือสองเครื่องบน Wi-Fi เดียวกัน", StatusTone.READY)
            Mode.HOST -> {
                val failed = listOf("ไม่สำเร็จ", "เครือข่าย:").any { state.status.contains(it) }
                when {
                    failed -> SessionPresentation("เชื่อมต่อเครื่องเล่นไม่สำเร็จ", "ตรวจ Wi-Fi และอุปกรณ์เสียง แล้วลองอีกครั้ง", StatusTone.ERROR)
                    state.peers > 0 -> SessionPresentation("เชื่อมต่อไมค์ ${state.peers}/2 เครื่อง", "พร้อมเปิดเพลงและร้องได้เลย • แตะดูวิธีเชื่อมไมค์", StatusTone.READY)
                    else -> SessionPresentation("เครื่องเล่นพร้อมแล้ว", "รอเครื่องไมค์ • แตะดูวิธีเชื่อมต่อ", StatusTone.WAITING)
                }
            }
            Mode.MIC -> when {
                state.peers > 0 -> SessionPresentation("ไมค์พร้อมร้อง", if (backgroundMic) "ส่งเสียงอยู่ • ปิดหน้าจอร้องต่อได้" else "เชื่อมต่อเครื่องเล่นแล้ว", StatusTone.READY)
                listOf("ไม่ตอบกลับ", "ครบ 2", "ไม่ได้", "ไม่สำเร็จ", "เครือข่าย:").any { state.status.contains(it) } ->
                    SessionPresentation("ยังเชื่อมต่อไม่ได้", state.status, StatusTone.ERROR)
                backgroundMic -> SessionPresentation("กำลังเชื่อมต่อ…", "รอสักครู่ กำลังติดต่อเครื่องเล่น", StatusTone.WAITING)
                else -> SessionPresentation("เลือกเครื่องเล่นเพื่อเริ่มร้อง", "เปิดเครื่องเล่นบนมือถืออีกเครื่องก่อน", StatusTone.WAITING)
            }
        }
    }
}