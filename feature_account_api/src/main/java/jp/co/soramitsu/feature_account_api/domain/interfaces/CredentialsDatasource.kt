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

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity

enum class CredentialField {
    PRIVATE_KEY,
    PUBLIC_KEY,
    KEY_NONCE,
    MNEMONIC,
    SEED,
}

sealed interface StoredCredential {
    data object Absent : StoredCredential

    class Present(val value: String) : StoredCredential

    data object Unreadable : StoredCredential
}

interface CredentialsDatasource {

    suspend fun getAddress(): String

    suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String)

    suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair?

    suspend fun saveMnemonic(mnemonic: String, suffixAddress: String)

    suspend fun retrieveMnemonic(suffixAddress: String): String

    suspend fun saveSeed(seed: String, suffixAddress: String)

    suspend fun retrieveSeed(suffixAddress: String): String

    /**
     * Reads one stored credential for [address] without writing anything. A value that is stored
     * but cannot be decrypted is reported as [StoredCredential.Unreadable]; a Keystore failure is
     * thrown, never reported as absence.
     */
    suspend fun readStoredCredential(field: CredentialField, address: String): StoredCredential

    /**
     * Writes [values] for [address] in one atomic preference edit, and only where no value is
     * stored yet. If any of the fields became non-empty in the meantime, nothing is written and
     * an [IllegalStateException] with `WALLET_CREDENTIAL_ALREADY_PRESENT` is thrown.
     */
    suspend fun writeCredentialsIfAbsent(address: String, values: Map<CredentialField, String>)

    /**
     * Replaces the stored [values] of [address] in one atomic preference edit. Used only by the
     * repair of one account with its own recovery phrase or seed, after the person confirmed it
     * and after a copy of the old preference files was kept on the device. The values written
     * must have been proven to derive [address] by the caller.
     */
    suspend fun replaceCredentialsForRepair(address: String, values: Map<CredentialField, String>)

    /**
     * A watch-only account must be explicitly marked. Missing or unreadable signing material is
     * never enough to infer this state during a production upgrade.
     */
    suspend fun isExplicitWatchOnly(suffixAddress: String): Boolean

    suspend fun setExplicitWatchOnly(suffixAddress: String, watchOnly: Boolean)

    /**
     * Proves that the key material already stored on this device can be unlocked (the Keystore
     * alias and the wrapped AES key match). Writes and creates nothing. Throws a
     * `KeyMaterialUnavailableException` (transient or not) otherwise.
     */
    suspend fun requireKeyMaterialReadable()

    /**
     * The key sets that only their own recovery phrase may bring back: the address a set is
     * stored under, or `WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID` for the release-era
     * unsuffixed set. They were left on the phone by "Remove all accounts". Reads only.
     */
    suspend fun retainedKeySetsNeedingPhrase(): Set<String> = emptySet()

    /**
     * Marks every stored key set whose id is not in [exceptIds] as needing its own recovery
     * phrase, in one edit; earlier marks are kept. Writes only the marker, never a key value.
     */
    suspend fun markKeySetsNeedingPhrase(exceptIds: Set<String>) = Unit

    /**
     * Takes [ids] out of the marker again, in one edit, after their wallets were proven with
     * their own recovery phrase, raw seed or backup file. Writes only the marker.
     */
    suspend fun unmarkKeySetsNeedingPhrase(ids: Set<String>) = Unit

    /**
     * Checks that every id in [requiredCoverage] holds stored credentials and that the selection
     * is one of [walletIds]. Key sets of other addresses (without an account) are ignored.
     */
    suspend fun requireWalletPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
        requiredCoverage: Set<String> = walletIds,
    )

    /**
     * Executes and verifies the preference half of an already journaled explicit deletion.
     * Implementations must use one atomic preference transaction and must not touch the shared
     * Android Keystore alias.
     */
    suspend fun previewWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
    ): WalletPreferenceIntegrity.Hashes

    suspend fun commitWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
        expectedBeforeHash: String,
        expectedAfterHash: String,
    )
}
