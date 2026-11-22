package com.karaokelink.domain

import kotlinx.coroutines.flow.StateFlow

enum class Mode { HOME, MIC, HOST }
data class Host(val ip: String, val name: String, val seenAt: Long)
data class SessionState(
    val mode: Mode = Mode.HOME,
    val hosts: List<Host> = emptyList(),
    val status: String = "พร้อมใช้งาน",
    val rttMs: Long? = null,
    val vu: Float = 0f,
    val peers: Int = 0,
    val underruns: Int = 0,
    val queuedMs: Int = 0,
    val engineError: Boolean = false
)
data class MicEffects(
    val gain: Float = 1f, val gate: Float = 0f,
    val echoMs: Float = 140f, val feedback: Float = 0.2f,
    val echoMix: Float = 0f, val reverb: Float = 0f,
    val low: Float = 1f, val mid: Float = 1f, val high: Float = 1f
)
interface SessionRepository {
    val state: StateFlow<SessionState>
    val continuesInBackground: Boolean get() = false
    suspend fun select(mode: Mode)
    suspend fun connect(ip: String)
    fun effects(value: MicEffects)
    fun lowLatency(enabled: Boolean) {}
    fun hostMix(gain: Float, delayMs: Int)
    fun inputDevice(id: Int)
    suspend fun outputDevice(id: Int)
    suspend fun pause()
    fun destroy()
    suspend fun close()
}