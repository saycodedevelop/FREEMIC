package com.karaokelink.presentation

import java.net.URI
import java.net.URLEncoder

/** Keep HTTPS links inside the player; ordinary text becomes a karaoke search. */
object PlayerNavigation {
    const val HOME = "https://m.youtube.com/"
    fun resolve(raw: String, karaokeTerm: String = "คาราโอเกะ"): String? {
        val value = raw.trim()
        if (value.isEmpty()) return HOME
        if (value.contains("://") || value.startsWith("javascript:", true) || value.startsWith("intent:", true) || value.startsWith("data:", true)) {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            return value.takeIf { uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null }
        }
        val query = if (value.contains(karaokeTerm, ignoreCase = true)) value else "$value $karaokeTerm"
        return "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
    }
}