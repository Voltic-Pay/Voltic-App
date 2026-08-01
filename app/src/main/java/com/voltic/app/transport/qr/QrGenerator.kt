package com.voltic.app.transport.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.core.graphics.createBitmap

object  QrGenerator {

    /**
     * Encodes raw text into a QR Code Bitmap image.
     *
     * Builds the whole pixel buffer in memory first, then writes it to the
     * bitmap in one setPixels() call — much faster than setting 262k+
     * individual pixels one at a time (which is what was causing the
     * generate-screen slowness).
     */
    fun generateBitmap(content: String, sizePx: Int = 512): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx
        )

        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = createBitmap(sizePx, sizePx)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }
}