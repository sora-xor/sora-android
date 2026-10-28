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
