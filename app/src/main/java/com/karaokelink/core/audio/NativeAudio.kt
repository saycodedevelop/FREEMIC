package com.karaokelink.core.audio

import com.karaokelink.domain.MicEffects

/** All lifecycle calls are serialized by the repository; callbacks never enter the JVM. */
class NativeAudio {
    companion object { init { System.loadLibrary("karaokelink") } }
    external fun startHost(deviceId: Int): Boolean
    external fun startMic(ip: String, session: Int, deviceId: Int): Boolean
    external fun allowPeer(session: Int, ip: String): Boolean
    external fun removePeer(session: Int)
    external fun stop()
    external fun metrics(): FloatArray
    external fun setEffects(gain: Float, gate: Float, echoMs: Float, feedback: Float,
                            echoMix: Float, reverb: Float, low: Float, mid: Float, high: Float)
    external fun setHostMix(gain: Float, delayMs: Int)
    fun effects(e: MicEffects) = setEffects(e.gain, e.gate, e.echoMs, e.feedback,
        e.echoMix, e.reverb, e.low, e.mid, e.high)
}