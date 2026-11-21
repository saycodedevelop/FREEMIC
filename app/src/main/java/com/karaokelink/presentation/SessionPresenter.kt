package com.karaokelink.presentation

import android.content.Context
import com.karaokelink.R
import com.karaokelink.domain.Mode
import com.karaokelink.domain.SessionState

enum class StatusTone { READY, WAITING, ERROR }
data class SessionPresentation(val title: String, val detail: String, val tone: StatusTone)

/** Translate diagnostic state at the UI boundary, never expose raw backend errors. */
object SessionPresenter {
    fun describe(context: Context, state: SessionState, backgroundMic: Boolean): SessionPresentation {
        fun message(title: Int, detail: Int, tone: StatusTone) =
            SessionPresentation(context.getString(title), context.getString(detail), tone)
        if (state.engineError) return message(R.string.check_audio, R.string.retry_audio, StatusTone.ERROR)
        return when (state.mode) {
            Mode.HOME -> message(R.string.ready, R.string.two_phones_wifi, StatusTone.READY)
            Mode.HOST -> {
                val failed = listOf("ไม่สำเร็จ", "เครือข่าย:").any { state.status.contains(it) }
                when {
                    failed -> message(R.string.player_failed, R.string.check_wifi_audio, StatusTone.ERROR)
                    state.peers > 0 -> SessionPresentation(context.getString(R.string.connected_mics, state.peers), context.getString(R.string.ready_sing), StatusTone.READY)
                    else -> message(R.string.player_ready, R.string.waiting_mic, StatusTone.WAITING)
                }
            }
            Mode.MIC -> when {
                state.peers > 0 -> message(R.string.mic_ready, if (backgroundMic) R.string.streaming else R.string.connected_player, StatusTone.READY)
                listOf("ไม่ตอบกลับ", "ครบ 2", "ไม่ได้", "ไม่สำเร็จ", "เครือข่าย:", "กรุณากรอก").any { state.status.contains(it) } -> {
                    val detail = when {
                        state.status.contains("ครบ 2") -> R.string.error_full
                        state.status.contains("ไม่ตอบกลับ") -> R.string.error_timeout
                        state.status.contains("กรุณากรอก") -> R.string.ip_invalid
                        state.status.contains("ไมค์") -> R.string.error_mic
                        else -> R.string.error_network
                    }
                    message(R.string.connection_failed, detail, StatusTone.ERROR)
                }
                backgroundMic -> message(R.string.connecting, R.string.contacting_player, StatusTone.WAITING)
                else -> message(R.string.choose_player, R.string.open_other_player, StatusTone.WAITING)
            }
        }
    }
}
