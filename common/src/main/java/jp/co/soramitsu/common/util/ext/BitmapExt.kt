/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

private const val IMAGE_NAME = "image.png"

fun Bitmap.saveToTempFile(context: Context): File? {
    val mediaStorageDir = File(context.externalCacheDir!!.absolutePath + IMAGE_NAME)

    val outputStream = FileOutputStream(mediaStorageDir)
    compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    outputStream.close()
    return mediaStorageDir
}