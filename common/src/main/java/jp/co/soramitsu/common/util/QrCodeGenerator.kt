/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
                pixels[offset + x] = if (result[x, y]) firstColor else backgroundColor
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, RECEIVE_QR_SCALE_SIZE, 0, 0, w, h)
        return bitmap
    }
}
