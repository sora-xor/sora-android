/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException

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
        val qrBitmap = MediaStore.Images.Media.getBitmap(contentResolver, data)
        val pixels = IntArray(qrBitmap.height * qrBitmap.width)
        qrBitmap.getPixels(pixels, 0, qrBitmap.width, 0, 0, qrBitmap.width, qrBitmap.height)
        qrBitmap.recycle()
        val source = RGBLuminanceSource(qrBitmap.width, qrBitmap.height, pixels)
        val hybridBinarizer = HybridBinarizer(source)
        val bBitmap = BinaryBitmap(hybridBinarizer)
        val reader = QRCodeReader()
        val textResult = reader.decode(bBitmap, DECODING_HINTS_MAP).text

        if (textResult.isNullOrEmpty()) {
            throw QrException.decodeError()
        } else {
            return textResult
        }
    }
}
