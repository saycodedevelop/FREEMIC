package com.karaokelink.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.karaokelink.core.network.WifiQrCode
import com.google.zxing.common.BitMatrix
import kotlin.math.min

/** Draw whole modules on a white quiet zone so scaling never blurs the QR edges. */
internal class WifiQrView(context: Context) : View(context) {
    private val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
    private var matrix: BitMatrix? = null
    private var credentials: Pair<String, String?>? = null

    fun show(ssid: String?, password: String?) {
        if (ssid == null) {
            matrix = null; credentials = null; visibility = GONE
        } else {
            val next = ssid to password
            if (next != credentials) { matrix = WifiQrCode.encode(ssid, password); credentials = next }
            visibility = VISIBLE
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val code = matrix ?: return
        canvas.drawColor(Color.WHITE)
        val scale = min(width / code.width, height / code.height)
        if (scale < 1) return
        val left = (width - scale * code.width) / 2
        val top = (height - scale * code.height) / 2
        for (y in 0 until code.height) for (x in 0 until code.width) {
            if (code[x, y]) canvas.drawRect((left + x * scale).toFloat(), (top + y * scale).toFloat(),
                (left + (x + 1) * scale).toFloat(), (top + (y + 1) * scale).toFloat(), paint)
        }
    }
}
