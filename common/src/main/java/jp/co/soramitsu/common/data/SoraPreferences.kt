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

package jp.co.soramitsu.common.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SoraPreferences(
    context: Context,
) {

    companion object {
        private const val SHARED_PREFERENCES_FILE = "sora_prefs"
        private const val SORA_COMMON_PREFS = "sora_prefs_datastore"
        private const val MAXIMUM_BOOLEAN_SNAPSHOT_FIELDS = 32
        private const val MAXIMUM_PREFERENCE_KEY_LENGTH = 256
    }

    private val Context.dataStorePreferences: DataStore<Preferences> by preferencesDataStore(
        name = SORA_COMMON_PREFS,
        produceMigrations = {
            listOf(
                SharedPreferencesMigration(it, SHARED_PREFERENCES_FILE)
            )
        }
    )

    private val dataStore = context.dataStorePreferences

    suspend fun putString(field: String, value: String) {
        dataStore.edit {
            it[stringPreferencesKey(field)] = value
        }
    }

    suspend fun clear(field: String) {
        dataStore.edit {
            it.remove(stringPreferencesKey(field))
        }
    }

    suspend fun getOrPutInt(field: String, value: Int): Int =
        dataStore.data.map {
            val key = intPreferencesKey(field)
            val result = if (it.contains(key)) {
                it[key] ?: value
            } else {
                it.toMutablePreferences()[key] = value
                value
            }
            result
        }.first()

    suspend fun getString(field: String): String =
        dataStore.data.map {
            it[stringPreferencesKey(field)] ?: ""
        }.first()

    fun getBooleanFlow(field: String, defaultValue: Boolean): Flow<Boolean> =
        dataStore.data.map {
            it[booleanPreferencesKey(field)] ?: defaultValue
        }

    /**
     * Observes a group of Boolean preferences from the same immutable DataStore snapshot.
     * Missing keys are omitted so callers can distinguish an explicit false value from a value
     * that has never been persisted. Combining independent per-key flows could otherwise publish
     * a capability state that never existed atomically.
     */
    fun getBooleanSnapshotFlow(fields: Set<String>): Flow<Map<String, Boolean>> {
        require(fields.isNotEmpty() && fields.size <= MAXIMUM_BOOLEAN_SNAPSHOT_FIELDS) {
            "BOOLEAN_PREFERENCE_SNAPSHOT_FIELDS_INVALID"
        }
        require(fields.all { it.isNotBlank() && it.length <= MAXIMUM_PREFERENCE_KEY_LENGTH }) {
            "BOOLEAN_PREFERENCE_SNAPSHOT_KEY_INVALID"
        }
        val keys = fields.associateWith(::booleanPreferencesKey)
        return dataStore.data.map { preferences ->
            buildMap {
                keys.forEach { (field, key) ->
                    if (preferences.contains(key)) {
                        preferences[key]?.let { value -> put(field, value) }
                    }
                }
            }
        }
    }

    suspend fun getBooleanSnapshot(fields: Set<String>): Map<String, Boolean> =
        getBooleanSnapshotFlow(fields).first()

    suspend fun getBoolean(field: String, defaultValue: Boolean = false): Boolean =
        dataStore.data.map {
            it[booleanPreferencesKey(field)] ?: defaultValue
        }.first()

    suspend fun putBoolean(field: String, value: Boolean) {
        dataStore.edit {
            it[booleanPreferencesKey(field)] = value
        }
    }

    suspend fun putBooleans(values: Map<String, Boolean>) {
        dataStore.edit { preferences ->
            values.forEach { (field, value) ->
                preferences[booleanPreferencesKey(field)] = value
            }
        }
    }

    suspend fun containsBoolean(field: String): Boolean =
        dataStore.data.map {
            it.contains(booleanPreferencesKey(field))
        }.first()

    suspend fun getInt(field: String, defaultValue: Int): Int =
        dataStore.data.map {
            it[intPreferencesKey(field)] ?: defaultValue
        }.first()

    fun getIntFlow(field: String, defaultValue: Int): Flow<Int> =
        dataStore.data.map {
            it[intPreferencesKey(field)] ?: defaultValue
        }

    suspend fun putInt(field: String, value: Int) {
        dataStore.edit {
            it[intPreferencesKey(field)] = value
        }
    }

    suspend fun getLong(field: String, defaultValue: Long): Long =
        dataStore.data.map {
            it[longPreferencesKey(field)] ?: defaultValue
        }.first()

    suspend fun putLong(field: String, value: Long) {
        dataStore.edit {
            it[longPreferencesKey(field)] = value
        }
    }

    suspend fun getFloat(field: String, defaultValue: Float): Float =
        dataStore.data.map {
            it[floatPreferencesKey(field)] ?: defaultValue
        }.first()

    suspend fun putFloat(field: String, value: Float) {
        dataStore.edit {
            it[floatPreferencesKey(field)] = value
        }
    }

    suspend fun getDouble(field: String, defaultValue: Double): Double =
        dataStore.data.map {
            java.lang.Double.longBitsToDouble(
                it[longPreferencesKey(field)] ?: java.lang.Double.doubleToLongBits(defaultValue)
            )
        }.first()

    suspend fun putDouble(field: String, value: Double) {
        dataStore.edit {
            it[longPreferencesKey(field)] = java.lang.Double.doubleToRawLongBits(value)
        }
    }

    suspend fun previewWalletDeletion(
        stringFields: Set<String>,
        booleanFields: Set<String>,
        selectedAddress: String,
        clearAll: Boolean,
    ): WalletPreferenceIntegrity.Hashes {
        val preferences = dataStore.data.first()
        val before = preferences.walletState()
        val after = before.afterDeletion(
            stringFields = stringFields,
            booleanFields = booleanFields,
            selectedAddress = selectedAddress,
            clearAll = clearAll,
        )
        return WalletPreferenceIntegrity.Hashes(
            before = before.hash(),
            after = after.hash(),
        )
    }

    suspend fun requireWalletPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
    ) {
        check(walletIds.isNotEmpty()) { "WALLET_PREFERENCE_ACCOUNT_SET_EMPTY" }
        val state = dataStore.data.first().walletState()
        val scopedWalletIds = buildSet {
            state.strings.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    WalletPreferenceKeys.encryptedCredentialPrefixes
                        .firstOrNull { prefix ->
                            key.length > prefix.length && key.startsWith(prefix)
                        }
                        ?.let { prefix -> add(key.removePrefix(prefix)) }
                }
            }
            state.booleans.forEach { (key, value) ->
                if (
                    value &&
                    key.length > WalletPreferenceKeys.WATCH_ONLY.length &&
                    key.startsWith(WalletPreferenceKeys.WATCH_ONLY)
                ) {
                    add(key.removePrefix(WalletPreferenceKeys.WATCH_ONLY))
                }
            }
        }
        val legacyOwner = state.strings[WalletPreferenceKeys.LEGACY_ADDRESS]
            ?.takeIf(String::isNotBlank)
            ?.takeIf {
                WalletPreferenceKeys.encryptedCredentialPrefixes.any { key ->
                    !state.strings[key].isNullOrBlank()
                }
            }
        val covered = scopedWalletIds + listOfNotNull(legacyOwner)
        check(
            selectedAddress in walletIds &&
                state.strings[WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS] ==
                selectedAddress &&
                covered.containsAll(walletIds) &&
                scopedWalletIds.all { it in walletIds } &&
                (legacyOwner == null || legacyOwner in walletIds)
        ) { "WALLET_PREFERENCE_COVERAGE_MISMATCH" }
    }

    /**
     * Applies the preference half of a journaled wallet deletion as one durable DataStore edit.
     * The Android Keystore and wrapped AES key live outside this DataStore and are intentionally
     * retained.
     */
    suspend fun commitWalletDeletion(
        stringFields: Set<String>,
        booleanFields: Set<String>,
        selectedAddress: String,
        clearAll: Boolean,
        expectedBeforeHash: String,
        expectedAfterHash: String,
    ) {
        check(
            WalletPreferenceIntegrity.isSha256(expectedBeforeHash) &&
                WalletPreferenceIntegrity.isSha256(expectedAfterHash)
        ) { "WALLET_DELETION_PREFERENCE_HASH_INVALID" }
        dataStore.edit { preferences ->
            val beforeHash = preferences.walletState().hash()
            if (beforeHash != expectedAfterHash) {
                check(beforeHash == expectedBeforeHash) {
                    "WALLET_DELETION_PREFERENCES_CHANGED"
                }
                if (clearAll) {
                    preferences.clear()
                } else {
                    stringFields.forEach { field ->
                        preferences.remove(stringPreferencesKey(field))
                    }
                    booleanFields.forEach { field ->
                        preferences.remove(booleanPreferencesKey(field))
                    }
                    preferences[
                        stringPreferencesKey(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS)
                    ] = selectedAddress
                }
                check(preferences.walletState().hash() == expectedAfterHash) {
                    "WALLET_DELETION_PREFERENCES_RESULT_MISMATCH"
                }
            }
        }
    }

    suspend fun walletDeletionPreferencesMatch(
        stringFields: Set<String>,
        booleanFields: Set<String>,
        selectedAddress: String,
        clearAll: Boolean,
        expectedAfterHash: String,
    ): Boolean = dataStore.data.map { preferences ->
        preferences.walletState().hash() == expectedAfterHash &&
            if (clearAll) {
                preferences.asMap().isEmpty()
            } else {
                stringFields.none { preferences.contains(stringPreferencesKey(it)) } &&
                    booleanFields.none { preferences.contains(booleanPreferencesKey(it)) } &&
                    preferences[
                        stringPreferencesKey(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS)
                    ] == selectedAddress
            }
    }.first()

    private fun Preferences.walletState(): WalletPreferenceState {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        asMap().forEach { (key, value) ->
            if (WalletPreferenceKeys.isWalletStateKey(key.name)) {
                when (value) {
                    is String -> strings[key.name] = value
                    is Boolean -> booleans[key.name] = value
                    else -> error("WALLET_PREFERENCE_TYPE_INVALID")
                }
            }
        }
        return WalletPreferenceState(strings, booleans)
    }

    private data class WalletPreferenceState(
        val strings: Map<String, String>,
        val booleans: Map<String, Boolean>,
    ) {
        fun hash(): String = WalletPreferenceIntegrity.hash(strings, booleans)

        fun afterDeletion(
            stringFields: Set<String>,
            booleanFields: Set<String>,
            selectedAddress: String,
            clearAll: Boolean,
        ): WalletPreferenceState {
            if (clearAll) return WalletPreferenceState(emptyMap(), emptyMap())
            val remainingStrings = strings.toMutableMap().apply {
                stringFields.forEach(::remove)
                this[WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS] = selectedAddress
            }
            val remainingBooleans = booleans.toMutableMap().apply {
                booleanFields.forEach(::remove)
            }
            return WalletPreferenceState(remainingStrings, remainingBooleans)
        }
    }
}
