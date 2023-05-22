/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.io

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import jp.co.soramitsu.common.domain.OptionsProvider

class FileManagerImpl(private val context: Context) : FileManager {
    /**
     * /data/data/{package_name}/cache/
     */
    private val internalCacheDir: String by lazy { context.cacheDir.absolutePath }

    /**
     * /data/data/{package_name}/files/
     */
    private val internalFilesDir: String by lazy { context.filesDir.absolutePath }

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
        (
            context.externalMediaDirs.firstOrNull()?.absolutePath
                ?: externalCacheDir
            ) + File.separator + "logs.txt"
    }

    override fun readInternalCacheFile(fileName: String): String? =
        File(internalCacheDir, fileName).takeIf { it.exists() }?.run { readText() }
            ?.takeIf { it.isNotEmpty() }

    override fun readInternalCacheFileAsByteArray(fileName: String): ByteArray? =
        File(internalCacheDir, fileName).takeIf { it.exists() }?.run { readBytes() }
            ?.takeIf { it.isNotEmpty() }

    override fun readInternalCacheFileAsUri(fileName: String): Uri? =
        runCatching {
            FileProvider.getUriForFile(
                context,
                OptionsProvider.fileProviderAuthority,
                File(internalCacheDir, fileName)
            )
        }.getOrNull()

    override fun writeInternalCacheFile(fileName: String, content: String) =
        File(internalCacheDir, fileName).writeText(content)

    override fun writeInternalCacheFile(fileName: String, content: ByteArray) =
        File(internalCacheDir, fileName).writeBytes(content)

    override fun writeInternalFile(fileName: String, content: String) {
        File(internalFilesDir, fileName).writeText(content)
    }

    override fun existsInternalFile(fileName: String): Boolean {
        return runCatching { File(internalFilesDir, fileName).exists() }.getOrDefault(false)
    }

    override fun readInternalFile(fileName: String): String? =
        File(internalFilesDir, fileName).takeIf { it.exists() }?.run { readText() }
            ?.takeIf { it.isNotEmpty() }

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
                fos.flush()
            }
            FileProvider.getUriForFile(context, OptionsProvider.fileProviderAuthority, it)
        }

    override fun writeExternalCacheText(
        fileName: String,
        body: String
    ): Uri {
        return File(externalCacheDir, fileName).let {
            if (it.exists()) {
                it.delete()
            }

            it.createNewFile()
            it.appendText(body)
            FileProvider.getUriForFile(context, OptionsProvider.fileProviderAuthority, it)
        }
    }
}
