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
     * True when [json] has the shape of an encrypted JSON export this app can restore (one
     * account, or the batch export of several). Nothing is decrypted.
     */
    fun isJsonBackupValid(json: String): Boolean

    /**
     * Reads an encrypted JSON export made by this app (or polkadot.js) into wallets held in
     * memory: one draft for a one-account export, one per account for a batch export. Each draft
     * carries the key pair only ([WalletDraft.Source.JSON]); the SORA address comes from the
     * public key computed from the private key in the file. Writes nothing. Throws
     * `JSON_BACKUP_WRONG_PASSWORD` or `JSON_BACKUP_INVALID`. The caller destroys the drafts.
     */
    suspend fun draftsFromJson(json: String, password: String, accountName: String): List<WalletDraft>

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
     * For an account that already signs on this phone: keeps the recovery phrase (or raw seed)
     * the person just entered as its backup, when no phrase (or seed) is stored for it. Add-only:
     * no stored value is replaced and no key value is written. It applies only next to a key set
     * stored under the address itself that proves the address, and only after the entered secret
     * is proven to re-derive that address. Returns true when a value was added. A draft read
     * from a backup file carries no phrase or seed and adds nothing.
     */
    suspend fun addMissingBackup(draft: WalletDraft): Boolean

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
     * [retrieveBackupMaterial] for a screen that is about to show the raw seed: one judgement of
     * the stored secrets answers both what may be shown and the seed itself
     * ([WalletBackupMaterial.seedHex]). As [retrieveSeed] does, the derived seed is cached where
     * no seed is stored yet (add-only; no stored value is replaced).
     */
    suspend fun retrieveBackupMaterialForSeed(soraAccount: SoraAccount): WalletBackupMaterial

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

    /**
     * The encrypted JSON export of [accounts] (one account, or a batch). It is made from each
     * account's stored key pair and needs no phrase or seed. Every account must be able to sign
     * (its stored key pair proves its address); otherwise `ACCOUNT_KEYS_NOT_EXPORTABLE` is thrown
     * and no file is made. [draftsFromJson] reads the result back.
     */
    suspend fun generateJson(accounts: List<SoraAccount>, password: String): String

    fun convertPassphraseToSeed(mnemonic: String): String

    /**
     * The SORA2 seed of a stored phrase of any BIP-39 length (12 to 24 words), read by the
     * library parser, so the separators used when it was stored do not matter. It does not make
     * 15-, 18- or 21-word phrases eligible for Nexus child derivation.
     */
    fun convertRetainedSoraPassphraseToSeed(mnemonic: String): String
}
