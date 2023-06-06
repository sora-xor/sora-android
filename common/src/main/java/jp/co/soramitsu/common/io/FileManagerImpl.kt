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
