/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

class QrCodeGenerator(
    private val firstColor: Int,
    private val secondColor: Int
) {

    companion object {
        private const val RECEIVE_QR_SCALE_SIZE = 1024
        private const val PADDING_SIZE = 2
    }

    fun generateQrBitmap(input: String): Bitmap {
//        val hints = HashMap<EncodeHintType, String>()
//        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
//        val qrCode = Encoder.encode(input, ErrorCorrectionLevel.H, hints)
//        val byteMatrix = qrCode.matrix
//        val width = byteMatrix.width + PADDING_SIZE
//        val height = byteMatrix.height + PADDING_SIZE
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        for (y in 0 until height) {
//            for (x in 0 until width) {
//                if (y == 0 || y > byteMatrix.height || x == 0 || x > byteMatrix.width) {
//                    bitmap.setPixel(x, y, secondColor)
//                } else {
//                    bitmap.setPixel(x, y, if (byteMatrix.get(x - PADDING_SIZE / 2, y - PADDING_SIZE / 2).toInt() == 1) firstColor else secondColor)
//                }
//            }
//        }
//        return Bitmap.createScaledBitmap(bitmap, RECEIVE_QR_SCALE_SIZE, RECEIVE_QR_SCALE_SIZE, false)
//

        val hints = HashMap<EncodeHintType, String>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val result = MultiFormatWriter().encode(
            input, BarcodeFormat.QR_CODE, RECEIVE_QR_SCALE_SIZE, RECEIVE_QR_SCALE_SIZE, hints
        )
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) firstColor else secondColor
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, RECEIVE_QR_SCALE_SIZE, 0, 0, w, h)
        return bitmap
    }
}
