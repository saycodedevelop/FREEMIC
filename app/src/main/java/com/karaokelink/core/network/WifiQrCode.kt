package com.karaokelink.core.network

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** Standard Wi-Fi QR, generated locally; credentials never leave the phone. */
internal object WifiQrCode {
    private fun escape(value: String): String = buildString {
        for (character in value) {
            if (character in "\\;,:\"") append('\\')
            append(character)
        }
    }
    fun payload(ssid: String, password: String?): String {
        require(ssid.isNotBlank())
        val type = if (password.isNullOrEmpty()) "nopass" else "WPA"
        return "WIFI:T:$type;S:${escape(ssid)};P:${escape(password.orEmpty())};;"
    }
    fun encode(ssid: String, password: String?): BitMatrix =
        QRCodeWriter().encode(payload(ssid, password), BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
}
