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

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

class QrCodeDecoder(
    private val contentResolver: ContentResolver
) {

    companion object {
        val DECODING_HINTS_MAP = mapOf(
            DecodeHintType.TRY_HARDER to true
        )
    }

    fun decodeQrFromUri(data: Uri): String {
        return decode(data)
    }

    private fun decode(data: Uri): String {
        var qrBitmap: Bitmap? = null
        try {
            qrBitmap = decodeBitmap(data)
            val pixels = IntArray(qrBitmap.height * qrBitmap.width)
            qrBitmap.getPixels(pixels, 0, qrBitmap.width, 0, 0, qrBitmap.width, qrBitmap.height)
            val source = RGBLuminanceSource(qrBitmap.width, qrBitmap.height, pixels)
            val hybridBinarizer = HybridBinarizer(source)
            val bBitmap = BinaryBitmap(hybridBinarizer)
            val reader = QRCodeReader()
            val temp = reader.decode(bBitmap, DECODING_HINTS_MAP)
            val textResult = temp.text

            if (textResult.isNullOrEmpty()) {
                throw QrException.decodeError()
            } else {
                return textResult
            }
        } finally {
            qrBitmap?.recycle()
        }
    }

    private fun decodeBitmap(data: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, data)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            contentResolver.openInputStream(data)?.use(BitmapFactory::decodeStream)
                ?: throw QrException.decodeError()
        }
}
