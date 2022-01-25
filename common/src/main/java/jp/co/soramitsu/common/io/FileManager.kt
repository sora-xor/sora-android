/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.io

import android.graphics.Bitmap
import android.net.Uri

interface FileManager {
    fun readAssetFile(fileName: String): String
    fun readInternalCacheFile(fileName: String): String?
    fun writeInternalCacheFile(fileName: String, content: String)
    fun writeExternalCacheBitmap(bitmap: Bitmap, fileName: String, format: Bitmap.CompressFormat, quality: Int): Uri
    val logStorageDir: String
}
