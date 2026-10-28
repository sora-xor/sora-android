/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

class QrCodeGenerator(
    private val firstColor: Int,
) {

    companion object {
        private const val RECEIVE_QR_SCALE_SIZE = 1024
        private const val PADDING_SIZE = 2
    }

    fun generateQrBitmap(input: String, backgroundColor: Int = Color.WHITE): Bitmap {
//        val hints = HashMap<EncodeHintType, String>()
//        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
//        val qrCode = Encoder.encode(input, ErrorCorrectionLevel.H, hints)
//        val byteMatrix = qrCode.matrix
//        val width = byteMatrix.width + PADDING_SIZE
//        val height = byteMatrix.height + PADDING_SIZE
//        val bitmap = androidx.core.graphics.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
                pixels[offset + x] = if (result[x, y]) firstColor else backgroundColor
            }
        }
        val bitmap = androidx.core.graphics.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, RECEIVE_QR_SCALE_SIZE, 0, 0, w, h)
        return bitmap
    }
}
