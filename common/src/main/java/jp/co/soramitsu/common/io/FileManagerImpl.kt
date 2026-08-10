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
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import jp.co.soramitsu.common.domain.OptionsProvider

internal fun isSafeAtomicCacheEntryForWrite(
    existsNoFollow: Boolean,
    isRegularFileNoFollow: Boolean,
    isSymbolicLinkNoFollow: Boolean,
): Boolean =
    !existsNoFollow || (isRegularFileNoFollow && !isSymbolicLinkNoFollow)

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

    override fun readInternalCacheFile(fileName: String, maxBytes: Int): String? {
        require(maxBytes > 0) { "INVALID_UTF8_PAYLOAD_LIMIT" }
        val file = internalCacheFile(fileName)
        val path = file.toPath()
        val attributes = readAttributesNoFollowOrNull(path) ?: return null
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw IOException("INTERNAL_CACHE_FILE_NOT_REGULAR:$fileName")
        }
        if (attributes.size() > maxBytes.toLong()) {
            throw IOException("UTF8_PAYLOAD_TOO_LARGE:$fileName")
        }
        // Preserve an existing empty entry so migration callers can classify it as malformed
        // retained cache rather than silently treating it as absent.
        return file.inputStream().use { readBoundedUtf8(it, maxBytes, fileName) }
    }

    override fun readInternalCacheFileAtomically(fileName: String): String? {
        val atomicFile = AtomicFile(internalCacheFile(fileName))
        return try {
            atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun readInternalCacheFileAtomically(fileName: String, maxBytes: Int): String? {
        require(maxBytes > 0) { "INVALID_UTF8_PAYLOAD_LIMIT" }
        val target = internalCacheFile(fileName)
        validateAtomicEntryIfPresent(target, fileName, maxBytes)
        validateAtomicEntryIfPresent(File("${target.path}.bak"), fileName, maxBytes)
        val atomicFile = AtomicFile(target)
        return try {
            atomicFile.openRead().use { readBoundedUtf8(it, maxBytes, fileName) }
        } catch (error: FileNotFoundException) {
            // AtomicFile can recover its .bak entry during openRead. Treat FileNotFoundException
            // as absence only when neither the base nor recovery entry exists; a directory or
            // unreadable entry is corruption and must reach the caller's fail-closed policy.
            val backup = File("${target.path}.bak")
            if (
                readAttributesNoFollowOrNull(target.toPath()) == null &&
                readAttributesNoFollowOrNull(backup.toPath()) == null
            ) {
                null
            } else {
                throw error
            }
        }
    }

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

    override fun writeInternalCacheFileAtomically(fileName: String, content: String) {
        val target = internalCacheFile(fileName)
        validateAtomicEntryIfPresent(target, fileName)
        validateAtomicEntryIfPresent(File("${target.path}.bak"), fileName)
        validateAtomicEntryIfPresent(File("${target.path}.new"), fileName)
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override fun writeInternalCacheFileAtomically(
        fileName: String,
        content: String,
        maxBytes: Int,
    ) {
        requireUtf8LengthAtMost(content, maxBytes, fileName)
        writeInternalCacheFileAtomically(fileName, content)
    }

    override fun listInternalCacheFileNames(prefix: String, maxEntries: Int): List<String> {
        require(prefix.isNotBlank() && prefix == File(prefix).name) {
            "INVALID_INTERNAL_CACHE_FILE_PREFIX"
        }
        require(maxEntries in 1..256) { "INVALID_INTERNAL_CACHE_LIST_LIMIT" }
        val result = ArrayList<String>(minOf(maxEntries, 16))
        Files.newDirectoryStream(File(internalCacheDir).toPath()).use { entries ->
            var inspected = 0
            for (entry in entries) {
                if (inspected >= maxEntries) break
                inspected += 1
                // Bound directory work itself, not only the number of names
                // matching the runtime prefix. Unrelated cache content must
                // not turn best-effort pruning into an unbounded startup scan.
                val name = entry.fileName.toString()
                if (!name.startsWith(prefix)) continue
                if (
                    Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(entry)
                ) {
                    result += name
                }
            }
        }
        return result
    }

    override fun deleteInternalCacheFile(fileName: String): Boolean {
        val path = internalCacheFile(fileName).toPath()
        if (
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(path)
        ) {
            return false
        }
        return Files.deleteIfExists(path)
    }

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

    override fun readAssetFile(fileName: String, maxBytes: Int): String =
        context.assets.open(fileName).use { readBoundedUtf8(it, maxBytes, fileName) }

    private fun internalCacheFile(fileName: String): File {
        require(fileName.isNotBlank() && fileName == File(fileName).name) {
            "INVALID_INTERNAL_CACHE_FILE_NAME"
        }
        return File(internalCacheDir, fileName)
    }

    private fun validateAtomicEntryIfPresent(
        file: File,
        sourceName: String,
        maxBytes: Int? = null,
    ) {
        val attributes = readAttributesNoFollowOrNull(file.toPath()) ?: return
        if (
            !isSafeAtomicCacheEntryForWrite(
                existsNoFollow = true,
                isRegularFileNoFollow = attributes.isRegularFile,
                isSymbolicLinkNoFollow = attributes.isSymbolicLink,
            )
        ) {
            throw IOException("INTERNAL_CACHE_FILE_NOT_REGULAR:$sourceName")
        }
        if (maxBytes != null && attributes.size() > maxBytes.toLong()) {
            throw IOException("UTF8_PAYLOAD_TOO_LARGE:$sourceName")
        }
    }

    private fun readAttributesNoFollowOrNull(
        path: java.nio.file.Path,
    ): BasicFileAttributes? = try {
        Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
    } catch (_: NoSuchFileException) {
        null
    }

    private fun readBoundedUtf8(
        input: InputStream,
        maxBytes: Int,
        sourceName: String,
    ): String {
        require(maxBytes > 0) { "INVALID_UTF8_PAYLOAD_LIMIT" }
        val output = ByteArrayOutputStream(minOf(maxBytes, IO_BUFFER_BYTES))
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var total = 0
        while (true) {
            // Read at most one byte beyond the limit, so oversize streams are rejected without
            // buffering the rest of their body.
            val readLimit = minOf(buffer.size, maxBytes - total + 1)
            val count = input.read(buffer, 0, readLimit)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) {
                throw IOException("UTF8_PAYLOAD_TOO_LARGE:$sourceName")
            }
            output.write(buffer, 0, count)
        }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    }

    private fun requireUtf8LengthAtMost(
        content: String,
        maxBytes: Int,
        sourceName: String,
    ) {
        require(maxBytes > 0) { "INVALID_UTF8_PAYLOAD_LIMIT" }
        var index = 0
        var encodedBytes = 0L
        while (index < content.length) {
            val character = content[index]
            val width = when {
                character.code <= 0x7f -> 1
                character.code <= 0x7ff -> 2
                Character.isHighSurrogate(character) -> {
                    if (
                        index + 1 >= content.length ||
                        !Character.isLowSurrogate(content[index + 1])
                    ) {
                        throw IOException("UTF8_PAYLOAD_INVALID:$sourceName")
                    }
                    index += 1
                    4
                }
                Character.isLowSurrogate(character) ->
                    throw IOException("UTF8_PAYLOAD_INVALID:$sourceName")
                else -> 3
            }
            encodedBytes += width
            if (encodedBytes > maxBytes.toLong()) {
                throw IOException("UTF8_PAYLOAD_TOO_LARGE:$sourceName")
            }
            index += 1
        }
    }

    private companion object {
        const val IO_BUFFER_BYTES = 8 * 1024
    }

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
