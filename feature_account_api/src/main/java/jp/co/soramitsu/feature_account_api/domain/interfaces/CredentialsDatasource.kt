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
