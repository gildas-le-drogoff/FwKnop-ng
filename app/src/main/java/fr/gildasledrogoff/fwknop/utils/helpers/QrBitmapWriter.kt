package fr.gildasledrogoff.fwknop.utils.helpers

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject

class QrBitmapWriter @Inject constructor() {
    fun encode(content: String, size: Int): Bitmap? {
        val matrix: BitMatrix = runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, HINTS)
        }.getOrNull() ?: return null
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        val HINTS = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        )
    }
}
