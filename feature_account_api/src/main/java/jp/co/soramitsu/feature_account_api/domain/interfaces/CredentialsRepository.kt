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

import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

interface CredentialsRepository {

    suspend fun isMnemonicValid(mnemonic: String): Boolean

    suspend fun isRawSeedValid(rawSeed: String): Boolean

    /**
     * Proves that the key material already stored on this device can be unlocked here, without
     * creating or writing anything. Throws `KeyMaterialUnavailableException` (transient or not).
     */
    suspend fun requireKeyMaterialReadable()

    /** A new random 24-word wallet held in memory only. Writes nothing. */
    suspend fun createDraft(accountName: String): WalletDraft

    /**
     * Derives the SORA2 wallet for an imported 12-, 15-, 18-, 21- or 24-word phrase in memory.
     * Writes nothing. Throws [jp.co.soramitsu.common.domain.SoraException] for an invalid phrase.
     */
    suspend fun draftFromMnemonic(mnemonic: String, accountName: String): WalletDraft

    /** Derives the SORA2 wallet for an imported raw seed in memory. Writes nothing. */
    suspend fun draftFromRawSeed(rawSeed: String, accountName: String): WalletDraft

    /**
     * Stores the draft's keys for its address without ever replacing a stored value.
     * - When the signing key already stored for that address proves the address (it is the key
     *   of the address and signs a random challenge), the stored key set is adopted unchanged,
     *   whatever else is stored next to it; the draft's own phrase or seed is added only where
     *   none is stored. This adopts a key set left by an unfinished flow or a lost database.
     * - Otherwise only absent values are written, and every stored value must agree with the
     *   draft (the same phrase words whatever the separators; a seed that is the phrase's 32-byte
     *   seed or its full 64-byte derivation). If a stored value differs or cannot be read,
     *   nothing is written and `ACCOUNT_STORAGE_CONFLICT` is thrown; [repairWithDraft] is the
     *   confirmed way forward.
     * The caller adds the account row afterwards. When this phone cannot unlock the key material
     * stored here, `WALLET_KEYS_UNAVAILABLE` is thrown and nothing is written.
     */
    suspend fun commitDraft(draft: WalletDraft): SoraAccount

    /** The signing key stored under [address] itself is the key of that address and signs. */
    suspend fun storedKeyProvesAddress(address: String): Boolean

    /**
     * The explicit, confirmed repair of one account with its own recovery phrase or seed: keeps a
     * copy of the old encrypted preference files in `no_backup/wallet-quarantine-*`, then writes
     * the draft's proven key pair and secret over the values stored for the draft's address, and
     * reads them back. A stored key that already proves the address is never replaced (the call
     * then behaves like [commitDraft]). Without the copy nothing is written
     * (`ACCOUNT_REPAIR_COPY_FAILED`). Only the caller's confirmation flow may call this.
     */
    suspend fun repairWithDraft(draft: WalletDraft): SoraAccount

    /** Encrypted polkadot.js JSON for a draft, for a Google Drive backup made before commit. */
    suspend fun generateJsonForDraft(draft: WalletDraft, password: String): String

    suspend fun saveMnemonic(mnemonic: String, soraAccount: SoraAccount)

    suspend fun retrieveMnemonic(soraAccount: SoraAccount): String

    /**
     * The 32-byte seed (hex) that re-derives this account, for display, export and backup: the
     * stored seed (a 64-byte cached seed is returned as its canonical 32 bytes; stored bytes are
     * not changed) or the seed of the stored phrase. Throws when neither re-derives the account.
     */
    suspend fun retrieveSeed(soraAccount: SoraAccount): String

    /**
     * The backup material of [soraAccount] that may be shown: only a stored phrase or seed that
     * re-derives its address. Reads only.
     */
    suspend fun retrieveBackupMaterial(soraAccount: SoraAccount): WalletBackupMaterial

    /**
     * Reads only the legacy stored seed. Unlike [retrieveSeed], this never derives or writes one.
     * Upgrade verification uses it so inspecting an installed wallet cannot alter its secret store.
     */
    suspend fun retrieveStoredSeed(soraAccount: SoraAccount): String

    /**
     * Reads the durable, explicit watch-only marker. The absence of a key, phrase, or seed is not
     * itself a watch-only declaration.
     */
    suspend fun isExplicitWatchOnly(soraAccount: SoraAccount): Boolean

    suspend fun setExplicitWatchOnly(soraAccount: SoraAccount, watchOnly: Boolean)

    /**
     * Returns a stored signing key when one belongs to [soraAccount], or null when no complete key
     * exists. Decryption/Keystore failures still propagate and must never be treated as absence.
     */
    suspend fun retrieveKeyPairOrNull(soraAccount: SoraAccount): Sr25519Keypair?

    suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair

    suspend fun saveKeyPair(key: Sr25519Keypair, soraAccount: SoraAccount)

    suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData

    suspend fun getAddressForMigration(): String

    suspend fun generateJson(accounts: List<SoraAccount>, password: String): String

    fun convertPassphraseToSeed(mnemonic: String): String

    /**
     * The SORA2 seed of a stored phrase of any BIP-39 length (12 to 24 words), read by the
     * library parser, so the separators used when it was stored do not matter. It does not make
     * 15-, 18- or 21-word phrases eligible for Nexus child derivation.
     */
    fun convertRetainedSoraPassphraseToSeed(mnemonic: String): String
}
