package com.karaokelink.core.network

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.result.ResultParser
import com.google.zxing.client.result.WifiParsedResult
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test

class WifiQrCodeTest {
    private fun decode(ssid: String, password: String?): WifiParsedResult {
        val matrix = WifiQrCode.encode(ssid, password)
        val size = matrix.width * 8
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size / 8, index / size / 8]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
        return ResultParser.parseResult(result) as WifiParsedResult
    }
    @Test fun standardWifiQrRoundTripsSsidAndPassword() {
        val result = decode("KaraokeLink-Test", "singalong123")
        assertEquals("KaraokeLink-Test", result.ssid)
        assertEquals("singalong123", result.password)
        assertEquals("WPA", result.networkEncryption)
    }
    @Test fun unicodeAndReservedCharactersSurviveRealQrDecoding() {
        val ssid = "ไมค์;中文:Vivo,\\\"WiFi"
        val password = "p@ss;word:with,\\\"quotes"
        val result = decode(ssid, password)
        assertEquals(ssid, result.ssid); assertEquals(password, result.password)
    }
    @Test fun openNetworkIsMarkedAsUnsecured() {
        val result = decode("Open WiFi", null)
        assertEquals("nopass", result.networkEncryption)
        assertTrue(result.password.isNullOrEmpty())
    }
}
