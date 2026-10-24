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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val SHARED_PREFERENCES_FILE = "sora_prefs"
private const val SORA_COMMON_PREFS = "sora_prefs_datastore"

/** The process-wide DataStore. DataStore allows one active instance per file and process. */
private val Context.soraPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SORA_COMMON_PREFS,
    produceMigrations = { listOf(SharedPreferencesMigration(it, SHARED_PREFERENCES_FILE)) },
)

class SoraPreferences(
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context.applicationContext.soraPreferencesDataStore)

    companion object {
        private const val MAXIMUM_BOOLEAN_SNAPSHOT_FIELDS = 32
        private const val MAXIMUM_PREFERENCE_KEY_LENGTH = 256

        /**
         * Builds a DataStore over the production file with the production migration, owned by
         * [scope]. Cancelling [scope] releases the file. Device tests use this to model separate
         * app processes; production code uses the process-wide instance from [SoraPreferences].
         */
        fun createDataStore(context: Context, scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, SHARED_PREFERENCES_FILE)),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile(SORA_COMMON_PREFS) },
            )
    }

    suspend fun putString(field: String, value: String) {
        dataStore.edit {
            it[stringPreferencesKey(field)] = value
        }
    }

    /**
     * Writes all [values] in one atomic edit, only if none of the keys holds a non-empty value.
     * Used to add wallet credentials without ever overwriting existing key material.
     */
    suspend fun putStringsIfAbsent(values: Map<String, String>) {
        dataStore.edit { preferences ->
            values.keys.forEach { field ->
                check(preferences[stringPreferencesKey(field)].isNullOrEmpty()) {
                    "WALLET_CREDENTIAL_ALREADY_PRESENT"
                }
            }
            values.forEach { (field, value) ->
                preferences[stringPreferencesKey(field)] = value
            }
        }
    }

    /**
     * Replaces all [values] in one atomic edit. The only caller is the repair of one account
     * with its own recovery phrase, which the person confirms explicitly and which first keeps a
     * copy of the old preference files (`CredentialsDatasource.replaceCredentialsForRepair`).
     * Nothing else may overwrite wallet credentials.
     */
    suspend fun replaceStringsForConfirmedRepair(values: Map<String, String>) {
        require(values.isNotEmpty() && values.values.none(String::isEmpty)) {
            "WALLET_CREDENTIAL_VALUE_EMPTY"
        }
        dataStore.edit { preferences ->
            values.forEach { (field, value) ->
                preferences[stringPreferencesKey(field)] = value
            }
        }
    }

    /** Publishes metadata only; original encrypted credentials are never rewritten or removed. */
    suspend fun completeLegacyAccountUpgrade(accountAddress: String) {
        check(accountAddress.isNotBlank()) { "LEGACY_ACCOUNT_ADDRESS_EMPTY" }
        dataStore.edit { preferences ->
            val selectedKey = stringPreferencesKey(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS)
            val legacyKey = stringPreferencesKey(WalletPreferenceKeys.LEGACY_ADDRESS)
            check(preferences[selectedKey].isNullOrBlank() || preferences[selectedKey] == accountAddress) {
                "LEGACY_ACCOUNT_SELECTION_CONFLICT"
            }
            check(preferences[legacyKey].isNullOrBlank() || preferences[legacyKey] == accountAddress) {
                "LEGACY_ACCOUNT_OWNER_CONFLICT"
            }
            check(preferences.asMap().keys.none { key ->
                WalletPreferenceKeys.scopedEvidencePrefixes.any { prefix ->
                    key.name.startsWith(prefix) && key.name.length > prefix.length
                }
            }) { "LEGACY_ACCOUNT_SCOPED_CREDENTIAL_CONFLICT" }
            listOf(WalletPreferenceKeys.NEEDS_MIGRATION, WalletPreferenceKeys.IS_MIGRATION_FETCHED)
                .forEach { prefix ->
                    val oldKey = booleanPreferencesKey(prefix)
                    if (preferences.contains(oldKey)) {
                        preferences[booleanPreferencesKey(prefix + accountAddress)] =
                            checkNotNull(preferences[oldKey])
                    }
                }
            preferences[legacyKey] = accountAddress
            preferences[selectedKey] = accountAddress
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

    /**
     * The ids of the key sets that only their own recovery phrase may bring back
     * ([WalletPreferenceKeys.RETAINED_NEEDS_PHRASE]). Reads only.
     */
    suspend fun retainedKeySetsNeedingPhrase(): Set<String> =
        WalletPreferenceKeys.decodeRetainedNeedsPhrase(
            dataStore.data.first()[stringPreferencesKey(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE)]
        )

    /**
     * Marks every stored key set whose id is not in [exceptIds] as needing its own recovery
     * phrase ([WalletPreferenceKeys.RETAINED_NEEDS_PHRASE]), in one edit. Earlier marks are
     * kept. Only the marker is written: no key value is read, changed, moved or removed, and
     * nothing is written at all when there is nothing new to mark.
     */
    suspend fun markKeySetsNeedingPhrase(exceptIds: Set<String>) {
        val marker = stringPreferencesKey(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE)
        val snapshot = dataStore.data.first()
        fun toMark(preferences: Preferences): Set<String> =
            preferences.asMap().keys
                .mapNotNull { key -> WalletPreferenceKeys.retainedKeySetId(key.name) }
                .toSet() - exceptIds - WalletPreferenceKeys.decodeRetainedNeedsPhrase(preferences[marker])
        if (toMark(snapshot).isEmpty()) return
        dataStore.edit { preferences ->
            val added = toMark(preferences)
            if (added.isNotEmpty()) {
                preferences[marker] = WalletPreferenceKeys.encodeRetainedNeedsPhrase(
                    WalletPreferenceKeys.decodeRetainedNeedsPhrase(preferences[marker]) + added
                )
            }
        }
    }

    /**
     * Takes [ids] out of [WalletPreferenceKeys.RETAINED_NEEDS_PHRASE], in one edit. Called only
     * after the person proved those key sets with their own recovery phrase, raw seed or backup
     * file: from then on the key set is theirs again, like any other wallet on this phone.
     * Only the marker is written (it is removed when no id is left): no key value is read,
     * changed, moved or removed, and nothing is written at all when none of [ids] is marked.
     */
    suspend fun unmarkKeySetsNeedingPhrase(ids: Set<String>) {
        if (ids.isEmpty()) return
        val marker = stringPreferencesKey(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE)
        val marked = WalletPreferenceKeys.decodeRetainedNeedsPhrase(dataStore.data.first()[marker])
        if (marked.none { it in ids }) return
        dataStore.edit { preferences ->
            val current = WalletPreferenceKeys.decodeRetainedNeedsPhrase(preferences[marker])
            val left = current - ids
            when {
                left.size == current.size -> Unit
                left.isEmpty() -> preferences.remove(marker)
                else -> preferences[marker] = WalletPreferenceKeys.encodeRetainedNeedsPhrase(left)
            }
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
        requiredCoverage: Set<String> = walletIds,
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
        // Key sets with no account row (for example an abandoned create-account flow) and a
        // stale release-era owner are retained untouched; they are not part of this account set.
        val covered = scopedWalletIds + listOfNotNull(legacyOwner)
        check(
            selectedAddress in walletIds &&
                state.strings[WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS] ==
                selectedAddress &&
                walletIds.containsAll(requiredCoverage) &&
                covered.containsAll(requiredCoverage)
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
                    val retained = preferences.asMap().filterKeys { key ->
                        WalletPreferenceKeys.isRetainedKeyMaterial(key.name) &&
                            key.name !in stringFields &&
                            key.name !in booleanFields
                    }
                    // Removing every account removes the PIN too. The key sets that stay are
                    // marked in this same edit: from now on only their own recovery phrase
                    // brings them back. Earlier marks are kept.
                    val needPhrase = WalletPreferenceKeys.decodeRetainedNeedsPhrase(
                        preferences[stringPreferencesKey(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE)]
                    ) + retained.keys.mapNotNull { key ->
                        WalletPreferenceKeys.retainedKeySetId(key.name)
                    }
                    preferences.clear()
                    retained.forEach { (key, value) ->
                        @Suppress("UNCHECKED_CAST")
                        preferences[key as Preferences.Key<Any>] = value
                    }
                    if (needPhrase.isNotEmpty()) {
                        preferences[
                            stringPreferencesKey(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE)
                        ] = WalletPreferenceKeys.encodeRetainedNeedsPhrase(needPhrase)
                    }
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
                preferences.asMap().keys.all { key ->
                    WalletPreferenceKeys.isKeptByRemoveAll(key.name) &&
                        key.name !in stringFields &&
                        key.name !in booleanFields
                }
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
            if (clearAll) {
                return WalletPreferenceState(
                    strings = strings.filterKeys { key ->
                        WalletPreferenceKeys.isRetainedKeyMaterial(key) && key !in stringFields
                    },
                    booleans = booleans.filterKeys { key ->
                        WalletPreferenceKeys.isRetainedKeyMaterial(key) && key !in booleanFields
                    },
                )
            }
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
