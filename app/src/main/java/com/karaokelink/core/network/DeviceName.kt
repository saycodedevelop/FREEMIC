package com.karaokelink.core.network

import android.os.Build

/** Public device model information; no additional permission or device identifier needed. */
internal object DeviceName {
    fun current(): String {
        fun clean(value: String) = value.trim().takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        val manufacturer = clean(Build.MANUFACTURER)
        val model = clean(Build.MODEL) ?: clean(Build.DEVICE) ?: "Android"
        return (if (manufacturer == null || model.startsWith(manufacturer, ignoreCase = true)) model
            else "$manufacturer $model").replace(Regex("[\\r\\n\\t]+"), " ").take(60)
    }
}
