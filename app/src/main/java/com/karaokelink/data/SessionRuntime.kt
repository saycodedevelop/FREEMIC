package com.karaokelink.data

import android.content.Context

/** One native engine per process. The microphone service outlives an Activity. Main-thread only. */
object SessionRuntime {
    private var current: AndroidSessionRepository? = null
    fun get(context: Context): AndroidSessionRepository = current ?: AndroidSessionRepository(context.applicationContext).also { current = it }
    fun release(repository: AndroidSessionRepository) {
        if (current === repository && !repository.continuesInBackground) {
            repository.destroy()
            current = null
        }
    }
}