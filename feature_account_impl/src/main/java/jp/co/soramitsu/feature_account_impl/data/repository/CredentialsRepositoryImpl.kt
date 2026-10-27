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

package jp.co.soramitsu.feature_account_impl.data.repository

import java.security.MessageDigest
import java.text.Normalizer
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SigningKeyUnavailableException
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletStorageFailures
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.ext.didToAccountId
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_account_impl.domain.keys.BackupMatch
import jp.co.soramitsu.feature_account_impl.domain.keys.PackagedStoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.SigningKeyVerdict
import jp.co.soramitsu.feature_account_impl.domain.keys.Sr25519PublicKeyDerivation
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredSecretsVerdict
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredWalletSecretsJudge
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CredentialsRepositoryImpl constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant,
    private val sora2AddressCodec: Sora2AddressCodec,
    private val jsonSeedEncoder: JsonAccountsEncoder,
    private val storedKeyCrypto: StoredKeyCrypto = PackagedStoredKeyCrypto,
    /** Where scrypt (the encrypted JSON export and its import) runs: never the main thread. */
    private val keyWorkDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CredentialsRepository {

    private val irohaCash = mutableMapOf<String, IrohaData>()
    private val secretsJudge = StoredWalletSecretsJudge(sora2AddressCodec, storedKeyCrypto)

    // SORA2 accepts every BIP39 length, as 3.8.x did. The phrase is read by the library parser
    // (the same one that derives the keys), so commas and extra spaces between words are fine.
    override suspend fun isMnemonicValid(mnemonic: String): Boolean =
        RecoveryPhrase.parseOrNull(mnemonic) != null

    override suspend fun isRawSeedValid(rawSeed: String): Boolean {
        return runCatching { rawSeed.fromHex().size == 32 }.getOrDefault(false)
    }

    // A draft can only be stored while normal wallet access is allowed. Checking before a phrase
    // is shown means nobody writes down, confirms or backs up a wallet that cannot be added.
    override suspend fun createDraft(accountName: String): WalletDraft {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        return draftFromPhrase(
            mnemonic = MnemonicCreator.randomMnemonic(Mnemonic.Length.TWENTY_FOUR),
            accountName = accountName,
            source = WalletDraft.Source.GENERATED,
        )
    }

    override suspend fun draftFromMnemonic(mnemonic: String, accountName: String): WalletDraft {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        if (RecoveryPhrase.parseOrNull(mnemonic) == null) {
            throw SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID)
        }
        val parsed = runCatching { MnemonicCreator.fromWords(mnemonic) }
            .getOrElse { throw SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID) }
        return draftFromPhrase(parsed, accountName, WalletDraft.Source.MNEMONIC)
    }

    override suspend fun draftFromRawSeed(rawSeed: String, accountName: String): WalletDraft {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val normalized = rawSeed.removeHexPrefix()
        if (!isRawSeedValid(normalized)) {
            throw SoraException.businessError(ResponseCode.RAW_SEED_IS_NOT_VALID)
        }
        val seed = normalized.fromHex()
        try {
            val keyPair = generateSr25519(seed)
            return WalletDraft(
                address = soraAddress(keyPair),
                accountName = accountName,
                mnemonic = null,
                // The 32 bytes as lower-case hex, whatever was typed: the typed text may carry
                // spaces or line breaks (the reader skips them), and those are not stored.
                seedHex = StoredSecretHex.encode(seed),
                keypair = keyPair,
                source = WalletDraft.Source.RAW_SEED,
                wipeKeypair = ::wipe,
            )
        } finally {
            seed.fill(0)
        }
    }

    override fun isJsonBackupValid(json: String): Boolean = jsonSeedEncoder.looksLikeExport(json)

    override suspend fun draftsFromJson(
        json: String,
        password: String,
        accountName: String,
    ): List<WalletDraft> = withContext(keyWorkDispatcher) {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        // Opening the file is scrypt (N = 32768, r = 8): about a second per derivation on a
        // phone, once for a one-account file and once more per account for a batch. It never
        // runs on the caller's thread.
        val imported = try {
            jsonSeedEncoder.decode(json, password)
        } catch (_: JsonAccountsEncoder.JsonImportException.WrongPassword) {
            throw SoraException.businessError(ResponseCode.JSON_BACKUP_WRONG_PASSWORD)
        } catch (_: JsonAccountsEncoder.JsonImportException) {
            throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
        }
        val drafts = mutableListOf<WalletDraft>()
        try {
            imported.forEach { account ->
                val keyPair = account.keypair
                // The file is trusted for nothing: the public key is computed from the private
                // key it holds, and that public key gives the SORA address. The address and
                // network written in the file are ignored (the same key has one SORA address).
                val derived = Sr25519PublicKeyDerivation.publicKeyOrNull(keyPair.privateKey)
                if (derived == null || !MessageDigest.isEqual(derived, keyPair.publicKey)) {
                    throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
                }
                val address = sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)
                    ?: throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
                drafts += WalletDraft(
                    address = address,
                    // One account: the name the person typed. A batch: each account's own name.
                    accountName = if (imported.size == 1) {
                        accountName.ifBlank { account.name.orEmpty() }
                    } else {
                        account.name ?: accountName
                    },
                    mnemonic = null,
                    seedHex = "",
                    keypair = keyPair,
                    source = WalletDraft.Source.JSON,
                    wipeKeypair = ::wipe,
                )
            }
        } catch (error: Throwable) {
            drafts.forEach(WalletDraft::destroy)
            imported.forEach { wipe(it.keypair) }
            throw error
        }
        drafts
    }

    private fun draftFromPhrase(
        mnemonic: Mnemonic,
        accountName: String,
        source: WalletDraft.Source,
    ): WalletDraft {
        // Unchanged SORA2 derivation: BIP39 entropy -> PBKDF2 mini-secret -> sr25519, SS58 69.
        val seed = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null).seed
        try {
            val keyPair = generateSr25519(seed)
            return WalletDraft(
                address = soraAddress(keyPair),
                accountName = accountName,
                mnemonic = mnemonic.words,
                seedHex = seed.toHexString(),
                keypair = keyPair,
                source = source,
                wipeKeypair = ::wipe,
            )
        } finally {
            seed.fill(0)
        }
    }

    private fun wipe(keyPair: Sr25519Keypair) {
        keyPair.privateKey.fill(0)
        keyPair.nonce.fill(0)
    }

    // The packaged library: SubstrateKeypairFactory.generate(sr25519, seed), as before.
    private fun generateSr25519(seed: ByteArray): Sr25519Keypair =
        requireNotNull(storedKeyCrypto.generateKeypair(seed)) { "SORA_KEY_TYPE_MISMATCH" }

    private fun soraAddress(keyPair: Sr25519Keypair): String =
        requireNotNull(sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)) {
            "SORA_ADDRESS_DERIVATION_FAILED"
        }

    override suspend fun commitDraft(draft: WalletDraft): SoraAccount =
        WalletMutationCoordinator.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            try {
                commitDraftLocked(draft)
            } catch (error: KeyMaterialUnavailableException) {
                if (error.transient) throw error
                // This phone cannot unlock the key material already stored here (for example the
                // app files came from another phone, whose Keystore key never moves). New keys
                // could not be stored either. Record the state so the recovery options (set up
                // this device again, keeping the old data aside) are offered.
                WalletUpgradeBackup.noteKeyMaterialUnavailable()
                throw SoraException.businessError(ResponseCode.WALLET_KEYS_UNAVAILABLE)
            }
        }

    override suspend fun requireKeyMaterialReadable() {
        credentialsPrefs.requireKeyMaterialReadable()
    }

    /**
     * The values a draft stores: its key pair and the one secret it was made from. A draft read
     * from an encrypted JSON export has no such secret and stores the key pair only.
     */
    private fun desiredValues(draft: WalletDraft): Map<CredentialField, String> = buildMap {
        put(CredentialField.PRIVATE_KEY, draft.keypair.privateKey.toHexString())
        put(CredentialField.PUBLIC_KEY, draft.keypair.publicKey.toHexString())
        put(CredentialField.KEY_NONCE, draft.keypair.nonce.toHexString())
        val mnemonic = draft.mnemonic
        when {
            mnemonic != null -> put(CredentialField.MNEMONIC, mnemonic)
            draft.source != WalletDraft.Source.JSON -> put(CredentialField.SEED, draft.seedHex)
        }
    }

    private suspend fun storedValues(address: String): Map<CredentialField, StoredCredential> =
        CredentialField.entries.associateWith { field ->
            credentialsPrefs.readStoredCredential(field, address)
        }

    /** What the values stored under [address] itself prove. Reads only. */
    private fun scopedVerdict(
        address: String,
        stored: Map<CredentialField, StoredCredential>,
    ): StoredSecretsVerdict {
        fun text(field: CredentialField): String? = when (val value = stored.getValue(field)) {
            StoredCredential.Absent -> ""
            StoredCredential.Unreadable -> null
            is StoredCredential.Present -> value.value
        }
        val privateKey = text(CredentialField.PRIVATE_KEY)?.let(StoredSecretHex::decodeOrNull)
        val publicKey = text(CredentialField.PUBLIC_KEY)?.let(StoredSecretHex::decodeOrNull)
        val nonce = text(CredentialField.KEY_NONCE)?.let(StoredSecretHex::decodeOrNull)
        val keyPair = if (
            privateKey != null && publicKey != null && nonce != null &&
            privateKey.isNotEmpty() && publicKey.isNotEmpty()
        ) {
            Sr25519Keypair(privateKey, publicKey, nonce)
        } else {
            null
        }
        return try {
            // The signing decision comes first and from the key pair alone.
            val signing = secretsJudge.judgeSigningKey(address, keyPair)
            secretsJudge.judgeBackup(
                address = address,
                signing = signing,
                keyPair = keyPair,
                mnemonic = text(CredentialField.MNEMONIC),
                storedSeed = text(CredentialField.SEED),
            )
        } finally {
            privateKey?.fill(0)
            nonce?.fill(0)
        }
    }

    /**
     * Stores a draft's keys for its address without ever replacing a stored value.
     *
     * - When the signing key already stored for the address proves that address (it is the key
     *   of the address and signs a random challenge), the stored key set is adopted as it is.
     *   Other stored material may differ from the draft (a seed cached as 64 bytes, a phrase
     *   stored with commas, a nonce that is not the freshly derived one, a phrase that belongs
     *   to another wallet): none of that is touched. The draft's own phrase or seed is added
     *   only where nothing is stored.
     * - Otherwise the missing values are added. Every value already stored must agree with the
     *   draft (key bytes equal; the same phrase words; a seed that is the phrase's 32-byte seed
     *   or its full 64-byte derivation). A value that differs or cannot be read stops with
     *   `ACCOUNT_STORAGE_CONFLICT` and nothing is written; [repairWithDraft] is the explicit,
     *   confirmed way forward.
     */
    private suspend fun commitDraftLocked(draft: WalletDraft): SoraAccount {
        val address = draft.address
        val desired = desiredValues(draft)
        if (credentialsPrefs.isExplicitWatchOnly(address)) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
        }
        val stored = storedValues(address)
        val absent = mutableMapOf<CredentialField, String>()
        if (scopedVerdict(address, stored).canSign) {
            absent += missingBackupOf(draft, stored)
        } else {
            desired.forEach { (field, value) ->
                when (val current = stored.getValue(field)) {
                    StoredCredential.Absent -> absent[field] = value
                    StoredCredential.Unreadable ->
                        throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                    is StoredCredential.Present -> if (!sameCredential(field, current.value, value)) {
                        throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                    }
                }
            }
            // The secret this draft does not write must still agree when it is already stored.
            // A JSON draft has no phrase or seed to compare: stored backup material is left as
            // it is and is judged on its own.
            if (draft.source != WalletDraft.Source.JSON) {
                requireRetainedSecretAgrees(draft, stored)
            }
        }
        if (absent.isNotEmpty()) {
            credentialsPrefs.writeCredentialsIfAbsent(address, absent)
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.KEYS_CREATED)
        } else {
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.RETAINED_KEYS_ADOPTED)
        }
        // Read back: whatever is stored now must sign for this address.
        check(scopedVerdict(address, storedValues(address)).canSign) {
            "WALLET_KEYS_READBACK_MISMATCH"
        }
        return SoraAccount(address, draft.accountName)
    }

    override suspend fun storedKeyProvesAddress(address: String): Boolean {
        if (credentialsPrefs.isExplicitWatchOnly(address)) return false
        return scopedVerdict(address, storedValues(address)).canSign
    }

    override suspend fun addMissingBackup(draft: WalletDraft): Boolean =
        WalletMutationCoordinator.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            val address = draft.address
            if (credentialsPrefs.isExplicitWatchOnly(address)) return@withLock false
            val stored = storedValues(address)
            // Only next to the account's own key set, and only when that key set proves the
            // address. (A release-era account keeps its unsuffixed values as they are.)
            if (!scopedVerdict(address, stored).canSign) return@withLock false
            val missing = missingBackupOf(draft, stored)
            val (field, value) = missing.entries.singleOrNull() ?: return@withLock false
            // The entered secret is proven on its own to re-derive this address before it is
            // kept as the account's backup.
            val entered = secretsJudge.judgeBackup(
                address = address,
                signing = SigningKeyVerdict(null),
                keyPair = null,
                mnemonic = if (field == CredentialField.MNEMONIC) value else "",
                storedSeed = if (field == CredentialField.SEED) value else "",
            )
            check(entered.hasMatchingBackup) { "WALLET_BACKUP_DRAFT_UNPROVEN" }
            credentialsPrefs.writeCredentialsIfAbsent(address, missing)
            // Read back: the key still signs and the added value is a proven backup.
            val after = scopedVerdict(address, storedValues(address))
            check(
                after.canSign && when (field) {
                    CredentialField.MNEMONIC -> after.phrase == BackupMatch.MATCHES
                    else -> after.seed == BackupMatch.MATCHES
                }
            ) { "WALLET_KEYS_READBACK_MISMATCH" }
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.RECOVERY_BACKUP_ADDED)
            true
        }

    /**
     * The draft's own phrase (or raw seed) when the account has none: nothing is stored for
     * that field under the address, and nothing reaches the account through the release-era
     * (unsuffixed) entry either. A value that is stored is never shadowed by a second one, in
     * whatever form it is stored: identities derived from the stored phrase text (SORA v1 claim,
     * SORA 3 accounts) must keep seeing the text they were first derived from. A draft read
     * from a backup file has no phrase or seed.
     */
    private suspend fun missingBackupOf(
        draft: WalletDraft,
        stored: Map<CredentialField, StoredCredential>,
    ): Map<CredentialField, String> {
        val mnemonic = draft.mnemonic
        val (field, value) = when {
            draft.source == WalletDraft.Source.JSON -> return emptyMap()
            mnemonic != null -> CredentialField.MNEMONIC to mnemonic
            else -> CredentialField.SEED to draft.seedHex
        }
        if (stored.getValue(field) != StoredCredential.Absent) return emptyMap()
        val account = backupVerdict(SoraAccount(draft.address, draft.accountName))
        val absentForAccount = when (field) {
            CredentialField.MNEMONIC -> account.phrase == BackupMatch.ABSENT
            else -> account.seed == BackupMatch.ABSENT
        }
        return if (absentForAccount) mapOf(field to value) else emptyMap()
    }

    override suspend fun repairWithDraft(draft: WalletDraft): SoraAccount =
        WalletMutationCoordinator.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            val address = draft.address
            if (credentialsPrefs.isExplicitWatchOnly(address)) {
                throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
            }
            // A stored key that already proves the address is never replaced.
            if (scopedVerdict(address, storedValues(address)).canSign) {
                return@withLock commitDraftLocked(draft)
            }
            // The values about to be written are proven first: the draft's key pair must be the
            // key of this address and sign for it.
            val proven = secretsJudge.judge(
                address = address,
                keyPair = draft.keypair,
                mnemonic = draft.mnemonic.orEmpty(),
                storedSeed = if (draft.mnemonic == null) draft.seedHex else "",
            )
            // A phrase or seed draft must also re-derive the address; a JSON draft is the key
            // pair itself and has nothing else to prove.
            check(
                proven.canSign &&
                    (proven.hasMatchingBackup || draft.source == WalletDraft.Source.JSON)
            ) { "WALLET_REPAIR_DRAFT_UNPROVEN" }
            // Keep the old encrypted values on the device before anything is replaced. Without
            // that copy nothing is written.
            if (WalletUpgradeBackup.preserveWalletPreferencesCopy(REPAIR_COPY_REASON).isFailure) {
                throw SoraException.businessError(ResponseCode.ACCOUNT_REPAIR_COPY_FAILED)
            }
            credentialsPrefs.replaceCredentialsForRepair(address, desiredValues(draft))
            check(scopedVerdict(address, storedValues(address)).canSign) {
                "WALLET_KEYS_READBACK_MISMATCH"
            }
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.ACCOUNT_KEYS_REPAIRED)
            SoraAccount(address, draft.accountName)
        }

    private fun requireRetainedSecretAgrees(
        draft: WalletDraft,
        stored: Map<CredentialField, StoredCredential>,
    ) {
        val draftPhrase = draft.mnemonic?.let(RecoveryPhrase::parseOrNull)
        if (draft.mnemonic != null) {
            when (val seed = stored.getValue(CredentialField.SEED)) {
                StoredCredential.Absent -> Unit
                StoredCredential.Unreadable ->
                    throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                is StoredCredential.Present -> if (
                    draftPhrase == null ||
                    !StoredSecretHex.seedBelongsToPhrase(seed.value, draftPhrase)
                ) {
                    throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                }
            }
        } else {
            when (val mnemonic = stored.getValue(CredentialField.MNEMONIC)) {
                StoredCredential.Absent -> Unit
                StoredCredential.Unreadable ->
                    throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                is StoredCredential.Present -> {
                    val storedPhrase = RecoveryPhrase.parseOrNull(mnemonic.value)
                    if (
                        storedPhrase == null ||
                        !StoredSecretHex.seedBelongsToPhrase(draft.seedHex, storedPhrase)
                    ) {
                        throw SoraException.businessError(ResponseCode.ACCOUNT_STORAGE_CONFLICT)
                    }
                }
            }
        }
    }

    private fun sameCredential(field: CredentialField, stored: String, expected: String): Boolean =
        when (field) {
            // The parsed words decide, so "a, b" and "a b" are the same phrase.
            CredentialField.MNEMONIC -> RecoveryPhrase.sameWords(stored, expected)
            // Key bytes and a typed raw seed: the same bytes (prefix and letter case ignored).
            else -> StoredSecretHex.sameBytes(stored, expected)
        }

    override suspend fun generateJsonForDraft(draft: WalletDraft, password: String): String {
        val seed = draft.seedHex.fromHex()
        return try {
            jsonSeedEncoder.generate(
                account = JsonAccountsEncoder.ExportAccount(
                    keypair = draft.keypair,
                    seed = seed,
                    name = draft.accountName,
                    address = draft.address,
                ),
                password = password,
                genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            )
        } finally {
            seed.fill(0)
        }
    }

    override suspend fun saveMnemonic(
        mnemonic: String,
        soraAccount: SoraAccount,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.saveMnemonic(mnemonic, soraAccount.substrateAddress)
    }

    override suspend fun retrieveMnemonic(soraAccount: SoraAccount): String {
        val current = try {
            credentialsPrefs.retrieveMnemonic(soraAccount.substrateAddress)
        } catch (error: WalletDecryptionException) {
            return releaseEraValueOrThrow(soraAccount.substrateAddress, error) {
                credentialsPrefs.retrieveMnemonic("")
            }
        }
        if (current.isNotEmpty()) return current
        return if (legacyEmptySuffixBelongsTo(soraAccount.substrateAddress)) {
            credentialsPrefs.retrieveMnemonic("")
        } else {
            ""
        }
    }

    /**
     * The 32-byte seed (hex) that re-derives this account, for display, export and backup:
     * the stored seed when it derives the address (a seed cached as 64 bytes by 3.6.0.0 to
     * 3.8.2.0 is returned as its canonical first 32 bytes; the stored value stays as it is), or
     * the seed of the stored phrase when that phrase derives the address. A stored seed or
     * phrase that belongs to another wallet is never returned.
     */
    override suspend fun retrieveSeed(soraAccount: SoraAccount): String {
        val material = backupVerdict(soraAccount)
        val seed = checkNotNull(material.canonicalSeedHex) {
            "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
        }
        cacheDerivedSeed(soraAccount, material, seed)
        return seed
    }

    /**
     * A cache of the seed a phrase derives: added only where no seed value is stored (an
     * unreadable stored value is never replaced).
     */
    private suspend fun cacheDerivedSeed(
        soraAccount: SoraAccount,
        material: StoredSecretsVerdict,
        seed: String,
    ) {
        if (material.seed == BackupMatch.ABSENT && material.recoveryPhrase?.wordCount in setOf(12, 24)) {
            WalletMutationCoordinator.withLock {
                if (
                    WalletRecoveryCapabilityGate.mode() ==
                    WalletRecoveryCapabilityGate.Mode.NORMAL
                ) {
                    runCatching {
                        credentialsPrefs.writeCredentialsIfAbsent(
                            soraAccount.substrateAddress,
                            mapOf(CredentialField.SEED to seed),
                        )
                    }
                }
            }
        }
    }

    /**
     * What the stored secrets of [soraAccount] prove. Reads only. The signing decision is made
     * first, from the key pair alone; the phrase and the seed are read afterwards and judged as
     * backup material. An explicit watch-only account never counts as able to sign.
     */
    private suspend fun backupVerdict(soraAccount: SoraAccount): StoredSecretsVerdict {
        val address = soraAccount.substrateAddress
        val keyPair = try {
            retrieveKeyPairOrNull(soraAccount)
        } catch (_: WalletDecryptionException) {
            null
        }
        return try {
            val signing = if (credentialsPrefs.isExplicitWatchOnly(address)) {
                SigningKeyVerdict("WATCH_ONLY")
            } else {
                secretsJudge.judgeSigningKey(address, keyPair)
            }
            secretsJudge.judgeBackup(
                address = address,
                signing = signing,
                keyPair = keyPair,
                mnemonic = readBackupMaterial { retrieveMnemonic(soraAccount) },
                storedSeed = readBackupMaterial { retrieveStoredSeed(soraAccount) },
            )
        } finally {
            keyPair?.privateKey?.fill(0)
            keyPair?.nonce?.fill(0)
        }
    }

    /**
     * Null when the stored value cannot be read: that is a fact about the value and has its own
     * status. Only a Keystore or storage failure, which says nothing about the account, is
     * rethrown.
     */
    private suspend fun readBackupMaterial(read: suspend () -> String): String? = try {
        read()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: OutOfMemoryError) {
        throw error
    } catch (error: Throwable) {
        if (
            WalletStorageFailures.isTransient(error) ||
            WalletStorageFailures.isKeyMaterialUnavailable(error)
        ) {
            throw error
        }
        null
    }

    override suspend fun retrieveBackupMaterial(soraAccount: SoraAccount): WalletBackupMaterial =
        backupMaterial(backupVerdict(soraAccount))

    override suspend fun retrieveBackupMaterialForSeed(
        soraAccount: SoraAccount,
    ): WalletBackupMaterial {
        // One judgement for the screen: what may be shown, and the seed itself.
        val verdict = backupVerdict(soraAccount)
        verdict.canonicalSeedHex?.let { seed -> cacheDerivedSeed(soraAccount, verdict, seed) }
        return backupMaterial(verdict)
    }

    private fun backupMaterial(verdict: StoredSecretsVerdict) = WalletBackupMaterial(
        canSign = verdict.canSign,
        phraseWords = verdict.recoveryPhrase?.words.orEmpty(),
        seedHex = verdict.canonicalSeedHex.orEmpty(),
        storedPhraseDoesNotMatch = verdict.phrase == BackupMatch.DOES_NOT_MATCH,
        storedSeedDoesNotMatch = verdict.seed == BackupMatch.DOES_NOT_MATCH,
        storedPhraseUnreadable = verdict.phrase == BackupMatch.UNREADABLE,
        storedSeedUnreadable = verdict.seed == BackupMatch.UNREADABLE,
    )

    override suspend fun retrieveStoredSeed(soraAccount: SoraAccount): String {
        val current = try {
            credentialsPrefs.retrieveSeed(soraAccount.substrateAddress)
        } catch (error: WalletDecryptionException) {
            return releaseEraValueOrThrow(soraAccount.substrateAddress, error) {
                credentialsPrefs.retrieveSeed("")
            }
        }
        if (current.isNotEmpty()) return current
        return if (legacyEmptySuffixBelongsTo(soraAccount.substrateAddress)) {
            credentialsPrefs.retrieveSeed("")
        } else {
            ""
        }
    }

    override suspend fun isExplicitWatchOnly(soraAccount: SoraAccount): Boolean =
        credentialsPrefs.isExplicitWatchOnly(soraAccount.substrateAddress)

    override suspend fun setExplicitWatchOnly(
        soraAccount: SoraAccount,
        watchOnly: Boolean,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.setExplicitWatchOnly(soraAccount.substrateAddress, watchOnly)
    }

    override suspend fun retrieveKeyPairOrNull(soraAccount: SoraAccount): Sr25519Keypair? {
        credentialsPrefs.retrieveKeys(soraAccount.substrateAddress)?.let { return it }
        val legacy = credentialsPrefs.retrieveKeys("") ?: return null
        if (sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey) == soraAccount.substrateAddress) {
            return legacy
        }
        legacy.privateKey.fill(0)
        legacy.nonce.fill(0)
        return null
    }

    override suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair =
        retrieveKeyPairOrNull(soraAccount)
            // Typed, so a confirm screen can say that this account cannot sign on this phone
            // instead of asking for another try. Still an IllegalStateException for callers.
            ?: throw SigningKeyUnavailableException("Keypair not found")

    override suspend fun saveKeyPair(
        key: Sr25519Keypair,
        soraAccount: SoraAccount,
    ) = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        credentialsPrefs.saveKeys(key, soraAccount.substrateAddress)
    }

    override suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData {
        if (irohaCash.containsKey(soraAccount.substrateAddress)) {
            return requireNotNull(irohaCash[soraAccount.substrateAddress]) { "Iroha cash failure" }
        } else {
            // The SORA v1 identity is derived from the phrase TEXT, and every release derived
            // it from the text exactly as it is stored. That identity must not change, so the
            // stored text is used as it is (only its validity is checked with the parsed
            // words). A phrase imported by this version is stored as its words joined by single
            // spaces, so a new wallet always gets the canonical identity.
            val mnemonic = retrieveMnemonic(soraAccount)
            check(RecoveryPhrase.parseOrNull(mnemonic) != null) {
                "MNEMONIC_NOT_AVAILABLE_FOR_IROHA_DERIVATION"
            }
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
            val seed =
                cryptoAssistant.generateScryptSeedForEd25519(entropy, Const.SORA, purpose, "")
            val keys = cryptoAssistant.generateEd25519Keys(seed)
            val did = "did:sora:${keys.public.encoded.toHexString().substring(0, 20)}"
            val irohaAddress = did.didToAccountId()
            val message = irohaAddress + keys.public.encoded.toHexString()
            val signature = cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), keys)
            val data = IrohaData(
                address = irohaAddress,
                claimSignature = signature.toHexString(),
                publicKey = keys.public.encoded.toHexString()
            )
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.LEGACY_CREDENTIALS_READ)
            irohaCash[soraAccount.substrateAddress] = data
            return data
        }
    }

    // Reads the legacy single-account identity; current accounts use their own stored addresses.
    override suspend fun getAddressForMigration(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            val keyPair = credentialsPrefs.retrieveKeys("")
            address = try {
                sora2AddressCodec.toSoraAddressOrNull(keyPair?.publicKey).orEmpty()
            } finally {
                keyPair?.privateKey?.fill(0)
                keyPair?.nonce?.fill(0)
            }
            FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.ADDRESS_RECREATED)
        }
        return address
    }

    override suspend fun generateJson(
        accounts: List<SoraAccount>,
        password: String,
    ): String = withContext(keyWorkDispatcher) {
        // Recovery export is part of the installed wallet's safety boundary. Never let a missing,
        // stale, or compromised remote configuration rewrite the chain identity embedded in it.
        val localGenesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
        // Every account is proven before anything is encoded, so a file is either a backup of
        // all the chosen accounts or it is not made. Key pairs read so far are wiped when a
        // later account is refused.
        val exported = mutableListOf<JsonAccountsEncoder.ExportAccount>()
        try {
            accounts.forEach { exported += exportAccount(it) }
            if (exported.size == 1) {
                jsonSeedEncoder.generate(
                    account = exported.single(),
                    password = password,
                    genesisHash = localGenesis,
                )
            } else {
                jsonSeedEncoder.generate(exported, password, localGenesis)
            }
        } finally {
            exported.forEach { account ->
                (account.keypair as? Sr25519Keypair)?.let(::wipe)
                account.seed.fill(0)
            }
        }
    }

    /**
     * One account for the JSON encoder. The export is made from the stored key pair, so it is
     * the backup of an account only when that key pair is proven to be the key of the account's
     * address and to sign: otherwise `ACCOUNT_KEYS_NOT_EXPORTABLE`, and nothing unproven is ever
     * handed to the native encoder. The seed is not needed (the sr25519 export never used it),
     * so an account without a matching phrase or seed exports all the same.
     */
    private suspend fun exportAccount(soraAccount: SoraAccount): JsonAccountsEncoder.ExportAccount {
        val verdict = backupVerdict(soraAccount)
        if (!verdict.canSign) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE)
        }
        return JsonAccountsEncoder.ExportAccount(
            keypair = retrieveKeyPair(soraAccount),
            seed = verdict.canonicalSeedHex?.fromHex() ?: ByteArray(0),
            name = soraAccount.accountName,
            address = soraAccount.substrateAddress,
        )
    }

    override fun convertPassphraseToSeed(mnemonic: String): String = derivePassphraseToSeed(mnemonic)

    override fun convertRetainedSoraPassphraseToSeed(mnemonic: String): String =
        derivePassphraseToSeed(mnemonic)

    private fun derivePassphraseToSeed(mnemonic: String): String {
        // Parsed once by the library parser; the parsed words give the count and the seed.
        val phrase = requireNotNull(RecoveryPhrase.parseOrNull(mnemonic)) {
            "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
        }
        // The SORA2 seed: the first 32 bytes of the phrase's PBKDF2 derivation (what
        // SubstrateSeedFactory.deriveSeed32 returns for the same words).
        val seed = phrase.miniSeed()
        return try {
            seed.toHexString()
        } finally {
            seed.fill(0)
        }
    }

    private companion object {
        const val REPAIR_COPY_REASON = "repaired-account"
    }

    /**
     * Release-era accounts used unsuffixed encrypted preference keys. They remain read-only
     * compatibility input for this release and are accepted only when the stored public key
     * re-derives the exact requested SORA2 address.
     */
    /**
     * This account's own value cannot be decrypted. A non-empty release-era (unsuffixed) value
     * belongs to the same wallet when the release-era keys derive this address, so it is returned
     * instead; otherwise the original error is rethrown. Reads only.
     */
    private suspend fun releaseEraValueOrThrow(
        substrateAddress: String,
        error: WalletDecryptionException,
        read: suspend () -> String,
    ): String {
        if (legacyEmptySuffixBelongsTo(substrateAddress)) {
            val value = read()
            if (value.isNotEmpty()) return value
        }
        throw error
    }

    private suspend fun legacyEmptySuffixBelongsTo(substrateAddress: String): Boolean {
        // An unreadable release-era set cannot belong to any account; a Keystore failure still
        // propagates.
        val legacy = try {
            credentialsPrefs.retrieveKeys("")
        } catch (_: WalletDecryptionException) {
            null
        } ?: return false
        return try {
            sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey) == substrateAddress
        } finally {
            legacy.privateKey.fill(0)
            legacy.nonce.fill(0)
        }
    }
}
