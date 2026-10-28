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

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteOutOfMemoryException
import java.io.IOException

/**
 * Classifies failures met while reading or verifying wallet storage.
 *
 * A transient failure (busy or locked Keystore, full disk, I/O error, locked database, low memory)
 * may succeed on a later attempt, so it must never be written down as a permanent recovery state.
 * Anything else is deterministic for the data that caused it.
 */
object WalletStorageFailures {

    fun isTransient(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            when (current) {
                is KeyMaterialUnavailableException -> return current.transient
                is SQLiteFullException,
                is SQLiteDiskIOException,
                is SQLiteDatabaseLockedException,
                is SQLiteCantOpenDatabaseException,
                is SQLiteAccessPermException,
                is SQLiteOutOfMemoryException,
                is IOException,
                is OutOfMemoryError -> return true
                // Decryption failures are about the stored bytes, not the device state.
                is WalletDecryptionException -> return false
            }
            current = current.cause
            depth += 1
        }
        return false
    }

    /** True when nothing can be decrypted on this device right now (Keystore unusable). */
    fun isKeyMaterialUnavailable(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is KeyMaterialUnavailableException) return true
            current = current.cause
            depth += 1
        }
        return false
    }

    private const val MAX_CAUSE_DEPTH = 8
}
