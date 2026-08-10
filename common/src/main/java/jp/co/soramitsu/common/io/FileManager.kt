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

import android.graphics.Bitmap
import android.net.Uri

interface FileManager {
    fun readAssetFile(fileName: String): String
    /** Reads a UTF-8 asset and rejects it after observing at most [maxBytes] plus one byte. */
    fun readAssetFile(fileName: String, maxBytes: Int): String
    fun readInternalCacheFile(fileName: String): String?
    /** Reads a legacy UTF-8 cache entry with an encoded-byte ceiling. */
    fun readInternalCacheFile(fileName: String, maxBytes: Int): String?
    /**
     * Reads a cache file written by [writeInternalCacheFileAtomically]. Unlike the legacy cache
     * reader, an existing zero-byte file is returned as an empty string so callers can reject a
     * corrupt publication instead of mistaking it for an absent cache.
     */
    fun readInternalCacheFileAtomically(fileName: String): String?
    /** Reads an AtomicFile-backed UTF-8 cache entry with an encoded-byte ceiling. */
    fun readInternalCacheFileAtomically(fileName: String, maxBytes: Int): String?
    fun readInternalCacheFileAsUri(fileName: String): Uri?
    fun readInternalCacheFileAsByteArray(fileName: String): ByteArray?
    fun readInternalFile(fileName: String): String?
    fun writeInternalCacheFile(fileName: String, content: String)
    /** Writes one complete cache value with Android's crash-safe AtomicFile protocol. */
    fun writeInternalCacheFileAtomically(fileName: String, content: String)
    /** Atomically writes a UTF-8 value only when its encoded form fits [maxBytes]. */
    fun writeInternalCacheFileAtomically(fileName: String, content: String, maxBytes: Int)
    /**
     * Inspects at most [maxEntries] directory entries and returns the regular, non-symlink names
     * among that bounded window which match [prefix].
     */
    fun listInternalCacheFileNames(prefix: String, maxEntries: Int): List<String>
    /** Deletes only a regular, non-symlink cache entry with an exact basename. */
    fun deleteInternalCacheFile(fileName: String): Boolean
    fun writeInternalCacheFile(fileName: String, content: ByteArray)
    fun writeInternalFile(fileName: String, content: String)
    fun writeExternalCacheBitmap(bitmap: Bitmap, fileName: String, format: Bitmap.CompressFormat, quality: Int): Uri
    fun writeExternalCacheText(fileName: String, body: String): Uri
    val logStorageDir: String
    fun existsInternalFile(fileName: String): Boolean
}
