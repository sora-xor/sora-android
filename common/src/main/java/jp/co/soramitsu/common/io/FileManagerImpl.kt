/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.io

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import java.io.File
import java.io.FileOutputStream

class FileManagerImpl(private val context: Context) : FileManager {
    /**
     * /data/data/{package_name}/cache/
     */
    private val internalCacheDir: String by lazy { context.cacheDir.absolutePath }

    /**
     * /sdcard/Android/data/{package}/cache/
     */
    private val externalCacheDir: String by lazy {
        context.externalCacheDir?.absolutePath ?: internalCacheDir
    }

    /**
     * ONLY in debug mode
     * /sdcard/Android/media/{package}/
     */
    @Suppress("DEPRECATION")
    override val logStorageDir: String by lazy {
        context.externalMediaDirs.firstOrNull()?.absolutePath ?: externalCacheDir
    }

    override fun readInternalCacheFile(fileName: String): String? =
        File(internalCacheDir, fileName).takeIf { it.exists() }?.run { readText() }
            ?.takeIf { it.isNotEmpty() }

    override fun writeInternalCacheFile(fileName: String, content: String) =
        File(internalCacheDir, fileName).writeText(content)

    override fun readAssetFile(fileName: String): String =
        context.assets.open(fileName).bufferedReader().use { it.readText() }

    override fun writeExternalCacheBitmap(
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Uri =
        File(externalCacheDir, fileName).let {
            FileOutputStream(it).use { fos ->
                bitmap.compress(format, quality, fos)
            }
            FileProvider.getUriForFile(context, OptionsProvider.fileProviderAuthority, it)
        }
}
