package com.karaokelink.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.result.ResultParser
import com.google.zxing.client.result.WifiParsedResult
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WifiQrViewTest {
    private fun read(view: WifiQrView): WifiParsedResult {
        view.layout(0, 0, 657, 720)
        val bitmap = Bitmap.createBitmap(657, 720, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(657 * 720)
        bitmap.getPixels(pixels, 0, 657, 0, 0, 657, 720)
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(657, 720, pixels))))
        bitmap.recycle()
        return ResultParser.parseResult(result) as WifiParsedResult
    }
    @Test fun renderedQrScansAndReplacesOldHotspotCredentials() {
        val view = WifiQrView(RuntimeEnvironment.getApplication())
        view.show("Vivo-hotspot", "oldpassword")
        assertEquals("oldpassword", read(view).password)
        view.show("Vivo-new", "newpassword")
        assertEquals("Vivo-new", read(view).ssid)
        assertEquals("newpassword", read(view).password)
        view.show(null, null)
        assertEquals(View.GONE, view.visibility)
    }
}
