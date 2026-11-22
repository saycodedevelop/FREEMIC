package com.karaokelink.data

import android.content.Context
import com.karaokelink.core.network.PlayerHotspot

/** One native engine per process. Active audio services outlive an Activity. Main-thread only. */
object SessionRuntime {
    private var current: AndroidSessionRepository? = null
    private var playerHotspot: PlayerHotspot? = null
    internal fun hotspot(context: Context): PlayerHotspot = playerHotspot ?: PlayerHotspot(context.applicationContext).also { playerHotspot = it }
    internal fun stopHotspot() { playerHotspot?.stop() }
    fun get(context: Context): AndroidSessionRepository = current ?: AndroidSessionRepository(context.applicationContext).also { current = it }
    fun release(repository: AndroidSessionRepository) {
        if (current === repository && !repository.continuesInBackground) {
            repository.destroy()
            current = null
        }
    }
}