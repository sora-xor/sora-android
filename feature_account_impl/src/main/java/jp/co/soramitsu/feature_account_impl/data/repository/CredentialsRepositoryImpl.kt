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
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
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

class CredentialsRepositoryImpl constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant,
    private val sora2AddressCodec: Sora2AddressCodec,
    private val jsonSeedEncoder: JsonAccountsEncoder,
    private val storedKeyCrypto: StoredKeyCrypto = PackagedStoredKeyCrypto,
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
                seedHex = normalized,
                keypair = keyPair,
                source = WalletDraft.Source.RAW_SEED,
                wipeKeypair = ::wipe,
            )
        } finally {
            seed.fill(0)
        }
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

    /** The values a draft stores: its key pair and the one secret it was made from. */
    private fun desiredValues(draft: WalletDraft): Map<CredentialField, String> = buildMap {
        put(CredentialField.PRIVATE_KEY, draft.keypair.privateKey.toHexString())
        put(CredentialField.PUBLIC_KEY, draft.keypair.publicKey.toHexString())
        put(CredentialField.KEY_NONCE, draft.keypair.nonce.toHexString())
        val mnemonic = draft.mnemonic
        if (mnemonic != null) {
            put(CredentialField.MNEMONIC, mnemonic)
        } else {
            put(CredentialField.SEED, draft.seedHex)
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
            secretsJudge.judge(
                address = address,
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
            desired.forEach { (field, value) ->
                val backupMaterial =
                    field == CredentialField.MNEMONIC || field == CredentialField.SEED
                if (backupMaterial && stored.getValue(field) == StoredCredential.Absent) {
                    absent[field] = value
                }
            }
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
            requireRetainedSecretAgrees(draft, stored)
        }
        if (absent.isNotEmpty()) {
            credentialsPrefs.writeCredentialsIfAbsent(address, absent)
            FirebaseWrapper.log("Keys were created")
        } else {
            FirebaseWrapper.log("Retained keys were adopted")
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
            check(proven.canSign && proven.hasMatchingBackup) { "WALLET_REPAIR_DRAFT_UNPROVEN" }
            // Keep the old encrypted values on the device before anything is replaced. Without
            // that copy nothing is written.
            if (WalletUpgradeBackup.preserveWalletPreferencesCopy(REPAIR_COPY_REASON).isFailure) {
                throw SoraException.businessError(ResponseCode.ACCOUNT_REPAIR_COPY_FAILED)
            }
            credentialsPrefs.replaceCredentialsForRepair(address, desiredValues(draft))
            check(scopedVerdict(address, storedValues(address)).canSign) {
                "WALLET_KEYS_READBACK_MISMATCH"
            }
            FirebaseWrapper.log("Account keys were repaired from its recovery secret")
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
        if (material.seed == BackupMatch.ABSENT && material.recoveryPhrase?.wordCount in setOf(12, 24)) {
            WalletMutationCoordinator.withLock {
                if (
                    WalletRecoveryCapabilityGate.mode() ==
                    WalletRecoveryCapabilityGate.Mode.NORMAL
                ) {
                    // A cache of the derived seed: added only where no seed value is stored
                    // (an unreadable stored value is never replaced).
                    runCatching {
                        credentialsPrefs.writeCredentialsIfAbsent(
                            soraAccount.substrateAddress,
                            mapOf(CredentialField.SEED to seed),
                        )
                    }
                }
            }
        }
        return seed
    }

    /** Judges the stored phrase and seed of [soraAccount] as backup material. Reads only. */
    private suspend fun backupVerdict(soraAccount: SoraAccount): StoredSecretsVerdict {
        val keyPair = try {
            retrieveKeyPairOrNull(soraAccount)
        } catch (_: WalletDecryptionException) {
            null
        }
        return try {
            secretsJudge.judge(
                address = soraAccount.substrateAddress,
                keyPair = keyPair,
                mnemonic = readBackupMaterial { retrieveMnemonic(soraAccount) },
                storedSeed = readBackupMaterial { retrieveStoredSeed(soraAccount) },
            )
        } finally {
            keyPair?.privateKey?.fill(0)
            keyPair?.nonce?.fill(0)
        }
    }

    /** Null when the stored value cannot be decrypted; a Keystore failure still propagates. */
    private suspend fun readBackupMaterial(read: suspend () -> String): String? = try {
        read()
    } catch (_: WalletDecryptionException) {
        null
    }

    override suspend fun retrieveBackupMaterial(soraAccount: SoraAccount): WalletBackupMaterial {
        val verdict = backupVerdict(soraAccount)
        return WalletBackupMaterial(
            canSign = verdict.canSign,
            phraseWords = verdict.recoveryPhrase?.words.orEmpty(),
            seedHex = verdict.canonicalSeedHex.orEmpty(),
            storedPhraseDoesNotMatch = verdict.phrase == BackupMatch.DOES_NOT_MATCH,
            storedSeedDoesNotMatch = verdict.seed == BackupMatch.DOES_NOT_MATCH,
        )
    }

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
            ?: throw IllegalStateException("Keypair not found")

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
            // SORA v1 generated its phrases itself (words joined by single spaces) and could only
            // restore an identity from exactly that text, so the v1 identity always belongs to
            // the parsed words. A phrase stored with commas or extra spaces by 2.3.x therefore
            // derives the same v1 identity as the same words with spaces.
            val phrase = checkNotNull(RecoveryPhrase.parseOrNull(retrieveMnemonic(soraAccount))) {
                "MNEMONIC_NOT_AVAILABLE_FOR_IROHA_DERIVATION"
            }
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(phrase.text, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
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
            FirebaseWrapper.log("iroha ${data.address.isNotEmpty()} ${data.claimSignature.isNotEmpty()} ${data.publicKey.isNotEmpty()}}")
            irohaCash[soraAccount.substrateAddress] = data
            return data
        }
    }

    // TODO: modify for multiaccount variant
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
            FirebaseWrapper.log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override suspend fun generateJson(accounts: List<SoraAccount>, password: String): String {
        // Recovery export is part of the installed wallet's safety boundary. Never let a missing,
        // stale, or compromised remote configuration rewrite the chain identity embedded in it.
        val localGenesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
        if (accounts.size == 1) {
            accounts.first().let {
                val seed = exportSeed(it)
                val keys = retrieveKeyPair(it)

                val exportAccountData = JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed,
                    it.accountName,
                    it.substrateAddress
                )

                return jsonSeedEncoder.generate(
                    account = exportAccountData,
                    password = password,
                    genesisHash = localGenesis,
                )
            }
        } else {
            val accountsList = accounts.map {
                val seed = exportSeed(it)
                val keys = retrieveKeyPair(it)

                JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed,
                    it.accountName,
                    it.substrateAddress
                )
            }

            return jsonSeedEncoder.generate(accountsList, password, localGenesis)
        }
    }

    /**
     * The seed handed to the JSON encoder. The sr25519 export encodes the stored key pair and
     * does not use the seed, so an account whose stored phrase and seed do not re-derive it can
     * still be exported: the encrypted JSON is its backup.
     */
    private suspend fun exportSeed(soraAccount: SoraAccount): ByteArray =
        backupVerdict(soraAccount).canonicalSeedHex?.fromHex() ?: ByteArray(0)

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
