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

package jp.co.soramitsu.feature_account_impl.data.repository

import android.os.SystemClock
import androidx.room.withTransaction
import java.util.UUID
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletSelectionPolicy
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.WalletDeletionIntegrity
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletDeletionTargetLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.WalletStorageFailures
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletOptions
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionResult
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionTarget
import jp.co.soramitsu.feature_account_api.domain.model.WalletMutationSnapshot
import jp.co.soramitsu.feature_account_impl.domain.keys.BackupMatch
import jp.co.soramitsu.feature_account_impl.domain.keys.NexusChildAccounts
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredSecretsVerdict
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredWalletSecretsJudge
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Singleton
class UserRepositorySr25519Crypto @Inject constructor() : StoredKeyCrypto {

    override fun generateKeypair(seed: ByteArray): Sr25519Keypair? =
        SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            seed,
        ) as? Sr25519Keypair

    override fun signAndVerify(
        keypair: Sr25519Keypair,
        challenge: ByteArray,
        expectedPublicKey: ByteArray,
    ): Boolean {
        val signature = SignWrapper.sign(
            MultiChainEncryption.Substrate(SubstrateOptionsProvider.encryptionType),
            challenge,
            keypair,
        ).signature
        return try {
            Sr25519JNI.verify(signature, challenge, expectedPublicKey)
        } finally {
            signature.fill(0)
        }
    }
}

class UserRepositoryImpl(
    private val userDatasource: UserDatasource,
    private val credentialsDatasource: CredentialsDatasource,
    private val db: AppDatabase,
    coroutineManager: CoroutineManager,
    private val languagesHolder: LanguagesHolder,
    private val sora2AddressCodec: Sora2AddressCodec,
    private val userRepositorySr25519Crypto: UserRepositorySr25519Crypto,
) : UserRepository {

    private companion object {
        const val DELETION_PREVIEW_TTL_MILLIS = 2 * 60 * 1_000L
        val SELECTABLE_STATES = setOf("VERIFIED", "UNAVAILABLE")
        val DELETABLE_SECRET_SOURCES = setOf(
            StoredSecretsVerdict.SOURCE_MNEMONIC,
            StoredSecretsVerdict.SOURCE_MNEMONIC_UNSUPPORTED,
            StoredSecretsVerdict.SOURCE_RAW_SEED,
            StoredSecretsVerdict.SOURCE_LEGACY_SECRET,
        )
        const val UNAVAILABLE_STATE = "UNAVAILABLE"
        const val REMOVED_UNAVAILABLE_COPY_REASON = "removed-unavailable-account"
        const val REMOVED_WITHOUT_BACKUP_COPY_REASON = "removed-account-without-backup"
        const val RETAINED_WALLET_NEEDS_RECOVERY_PHRASE = "RETAINED_WALLET_NEEDS_RECOVERY_PHRASE"
        const val MAX_WALLET_ID_LENGTH = 256
    }

    private val currentSoraAccount = MutableStateFlow<SoraAccount?>(null)

    private val mutex = WalletMutationCoordinator
    private val secretsJudge = StoredWalletSecretsJudge(sora2AddressCodec, userRepositorySr25519Crypto)
    private val deletionPreviews = mutableMapOf<String, WalletDeletionPreview>()

    init {
        if (
            WalletUpgradeBackup.blockingFailure() == null &&
            WalletRecoveryCapabilityGate.mayResumeWalletDeletion()
        ) {
            coroutineManager.applicationScope.launch {
                try {
                    mutex.withLock {
                        resumePendingWalletDeletionLocked()
                        initCurSoraAccount()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Initialization is speculative. Splash's migration call repeats the
                    // locked read and routes a decryption/storage failure to recovery. An
                    // uncaught application-scope exception here would crash before that UI.
                    currentSoraAccount.value = null
                }
            }
        }
    }

    private suspend fun initCurSoraAccount() {
        var curAddress = userDatasource.getCurAccountAddress()
        var accounts = walletAccounts()
        if (curAddress.isEmpty() &&
            userDatasource.retrieveRegistratrionState() == OnboardingState.REGISTRATION_FINISHED
        ) {
            restoreLegacySingleAccount(accounts)?.let { restored ->
                curAddress = restored.substrateAddress
                accounts = listOf(restored)
            }
        }
        if (accounts.isEmpty()) {
            // A selection that names no account (for example after the database was lost) is
            // ignored; the retained keys are set aside and onboarding offers to restore them.
            currentSoraAccount.value = null
            return
        }
        val selected = accounts.firstOrNull { it.substrateAddress == curAddress }
            ?: fallbackSelection(accounts)
        currentSoraAccount.value = SoraAccountMapper.map(selected)
    }

    private fun isWalletId(walletId: String): Boolean =
        walletId.isNotBlank() && walletId.length <= MAX_WALLET_ID_LENGTH

    /**
     * Account rows that can hold a wallet. A blank row (written by an old upgrade that could not
     * derive an address) is ignored everywhere, including account removal, and never deleted.
     * The same rows are used by the upgrade gate, so deletion hashes and counts agree with it.
     */
    private suspend fun walletAccounts(): List<SoraAccountLocal> =
        db.accountDao().getAccounts().filter { isWalletId(it.substrateAddress) }

    private suspend fun walletIdentities(): List<WalletIdentityLocal> =
        db.walletIdentityDao().getWallets().filter { isWalletId(it.walletId) }

    private suspend fun walletNetworkAccounts(): List<NetworkAccountLocal> =
        db.walletIdentityDao().getAllNetworkAccounts().filter { isWalletId(it.walletId) }

    /** [mnemonic] and [rawSeed] are "" when absent and null when the stored value is unreadable. */
    private class StoredSecrets(
        val keyPair: Sr25519Keypair?,
        val mnemonic: String?,
        val rawSeed: String?,
    ) {
        fun wipe() {
            keyPair?.privateKey?.fill(0)
            keyPair?.nonce?.fill(0)
        }
    }

    /**
     * The stored secrets of [address]: its own (address-suffixed) values, or the release-era
     * unsuffixed values when their public key derives exactly [address]. This is the same rule
     * the credentials repository and the upgrade verification use. Reads only.
     */
    private suspend fun readSecrets(address: String): StoredSecrets {
        val scopedKeys = credentialsDatasource.retrieveKeys(address)
        val legacyKeys = if (scopedKeys == null) {
            releaseEraKeysOwnedBy(address)
        } else {
            null
        }
        var legacyOwnedChecked: Boolean? = null
        suspend fun legacyOwned(): Boolean = legacyOwnedChecked
            ?: (if (scopedKeys == null) legacyKeys != null else releaseEraOwnerIs(address))
                .also { legacyOwnedChecked = it }
        try {
            // The signing key was read first, on its own. Backup material that cannot be read
            // is reported as unreadable (null): it limits what is offered as a backup and never
            // decides whether the account can sign. Only a Keystore or storage failure, which
            // says nothing about the account, is rethrown.
            val mnemonic = readableOrNull {
                credentialsDatasource.retrieveMnemonic(address).takeIf(String::isNotEmpty)
                    ?: if (legacyOwned()) credentialsDatasource.retrieveMnemonic("") else ""
            }
            val rawSeed = readableOrNull {
                credentialsDatasource.retrieveSeed(address).takeIf(String::isNotEmpty)
                    ?: if (legacyOwned()) credentialsDatasource.retrieveSeed("") else ""
            }
            return StoredSecrets(scopedKeys ?: legacyKeys, mnemonic, rawSeed)
        } catch (error: Throwable) {
            (scopedKeys ?: legacyKeys)?.let { keys ->
                keys.privateKey.fill(0)
                keys.nonce.fill(0)
            }
            throw error
        }
    }

    private suspend fun readableOrNull(read: suspend () -> String): String? = try {
        read()
    } catch (error: CancellationException) {
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

    /**
     * What the stored secrets of [address] prove (see [StoredWalletSecretsJudge]): first the
     * signing decision from the key pair alone, then the phrase and seed as backup material.
     */
    private fun judge(address: String, secrets: StoredSecrets): StoredSecretsVerdict {
        val signing = secretsJudge.judgeSigningKey(address, secrets.keyPair)
        return secretsJudge.judgeBackup(
            address = address,
            signing = signing,
            keyPair = secrets.keyPair,
            mnemonic = secrets.mnemonic,
            storedSeed = secrets.rawSeed,
        )
    }

    private suspend fun releaseEraKeysOwnedBy(address: String): Sr25519Keypair? {
        val legacy = try {
            credentialsDatasource.retrieveKeys("")
        } catch (_: WalletDecryptionException) {
            null
        } ?: return null
        if (sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey) == address) return legacy
        legacy.privateKey.fill(0)
        legacy.nonce.fill(0)
        return null
    }

    private suspend fun releaseEraOwnerIs(address: String): Boolean =
        releaseEraKeysOwnedBy(address)?.let { keys ->
            keys.privateKey.fill(0)
            keys.nonce.fill(0)
            true
        } ?: false

    /**
     * The stored selection is empty or names no account (for example a crash between deleting an
     * account and selecting the next one in an older release). Select a deterministic existing
     * account. The choice is saved only while normal wallet access is allowed; it is not key
     * material.
     */
    private suspend fun fallbackSelection(accounts: List<SoraAccountLocal>): SoraAccountLocal {
        val legacyOwner = runCatching { credentialsDatasource.getAddress() }.getOrNull()
        val fallback = WalletSelectionPolicy.fallback(
            walletIds = accounts.map(SoraAccountLocal::substrateAddress),
            legacyOwner = legacyOwner,
        )
        val selected = accounts.first { it.substrateAddress == fallback }
        if (WalletRecoveryCapabilityGate.mode() == WalletRecoveryCapabilityGate.Mode.NORMAL) {
            userDatasource.setCurAccountAddress(selected.substrateAddress)
        }
        return selected
    }

    /**
     * Replaces the release-era single-account promotion removed when network identities landed.
     * Only metadata is added; unsuffixed mnemonic/seed/key ciphertext remains authoritative.
     * If the process stops after the Room insert, the same verified account resumes here.
     */
    private suspend fun restoreLegacySingleAccount(
        accounts: List<SoraAccountLocal>,
    ): SoraAccountLocal? {
        if (accounts.size > 1 || db.walletIdentityDao().getWallets().isNotEmpty() ||
            db.walletIdentityDao().getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1) != null
        ) return null
        // An unreadable set is recorded by retainedLegacyStep; no unsuffixed keys means nothing to do.
        val keys = retainedLegacyStep { credentialsDatasource.retrieveKeys("") } ?: return null
        try {
            // Release-era material that does not verify is set aside untouched (not restored);
            // the person can still restore that wallet from its recovery phrase, and the recovery
            // screens list it. A transient Keystore or storage failure is rethrown so the next
            // attempt can restore it.
            val address = sora2AddressCodec.toSoraAddressOrNull(keys.publicKey)
            val recordedAddress = credentialsDatasource.getAddress()
            if (address == null) {
                unrestorableLegacyWallet(owner = recordedAddress)
                return null
            }
            if (recordedAddress.isNotBlank() && recordedAddress != address) {
                unrestorableLegacyWallet(owner = recordedAddress)
                return null
            }
            if (accounts.isNotEmpty() && accounts.single().substrateAddress != address) {
                unrestorableLegacyWallet(owner = address)
                return null
            }
            retainedLegacyStep {
                // The stored key alone decides: it must be the key of this address and sign.
                // The release-era phrase or seed is judged later, only as backup material.
                val verdict = secretsJudge.judge(
                    address = address,
                    keyPair = keys,
                    mnemonic = "",
                    storedSeed = "",
                )
                check(verdict.canSign) { verdict.signingRefusal.orEmpty() }
            } ?: run {
                unrestorableLegacyWallet(owner = address)
                return null
            }
            val account = accounts.singleOrNull()
                ?: SoraAccountLocal(address, userDatasource.getAccountName())
            if (accounts.isEmpty()) {
                // WalletMutationCoordinator is held by every caller. Inserting only when
                // absent avoids REPLACE and its foreign-key cascade on a retained account.
                db.accountDao().insertSoraAccount(account)
            }
            db.cardsHubDao().insertMissing(
                CardHubType.entries.filter { it.boundToAccount }.map { type ->
                    CardHubLocal(
                        cardId = type.hubName,
                        accountAddress = address,
                        visibility = type != CardHubType.BACKUP,
                        sortOrder = type.order,
                        collapsed = false,
                    )
                }
            )
            defaultGlobalCards()
            userDatasource.completeLegacyAccountUpgrade(address)
            return account
        } finally {
            keys.privateKey.fill(0)
            keys.nonce.fill(0)
        }
    }

    private fun unrestorableLegacyWallet(owner: String?) {
        WalletUpgradeBackup.noteUnrestorableLegacyWallet(owner)
    }

    /** Runs [block]; returns null for a deterministic failure and rethrows a transient one. */
    private suspend fun <T> retainedLegacyStep(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (WalletStorageFailures.isTransient(error) ||
            WalletStorageFailures.isKeyMaterialUnavailable(error)
        ) {
            throw error
        }
        // Recorded for the recovery screens; the material itself is left untouched.
        unrestorableLegacyWallet(owner = null)
        null
    }

    override suspend fun getCurSoraAccount(): SoraAccount = mutex.withLock {
        resumePendingWalletDeletionIfAllowed()
        if (currentSoraAccount.value == null) {
            initCurSoraAccount()
        }
        requireNotNull(currentSoraAccount.value)
    }

    override suspend fun <T> withWalletMutationLocked(
        block: suspend (WalletMutationSnapshot) -> T,
    ): T = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        resumePendingWalletDeletionLocked()
        if (currentSoraAccount.value == null) {
            initCurSoraAccount()
        }
        block(
            WalletMutationSnapshot(
                selectedAccount = currentSoraAccount.value,
                onboardingState = userDatasource.retrieveRegistratrionState(),
            )
        )
    }

    override suspend fun <T> withWalletMigrationLocked(
        block: suspend (WalletMutationSnapshot) -> T,
    ): T = mutex.withLock {
        WalletRecoveryCapabilityGate.requireMigrationWriteAllowed()
        resumePendingWalletDeletionIfAllowed()
        if (currentSoraAccount.value == null) {
            initCurSoraAccount()
        }
        block(
            WalletMutationSnapshot(
                selectedAccount = currentSoraAccount.value,
                onboardingState = userDatasource.retrieveRegistratrionState(),
            )
        )
    }

    override suspend fun setCurSoraAccount(soraAccount: SoraAccount) {
        mutex.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            resumePendingWalletDeletionLocked()
            // An account whose keys are unavailable on this device may still be selected; it is
            // read-only (signing checks its own identity state).
            check(
                db.accountDao().getAccount(soraAccount.substrateAddress) != null &&
                    db.walletIdentityDao().getWallet(soraAccount.substrateAddress)
                        ?.migrationState in SELECTABLE_STATES
            ) { "SELECTED_WALLET_NOT_VERIFIED" }
            userDatasource.setCurAccountAddress(soraAccount.substrateAddress)
            currentSoraAccount.value = soraAccount
        }
    }

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        currentSoraAccount.asStateFlow()
            .filterNotNull()
            .distinctUntilChangedBy { it.substrateAddress }
            .onEach {
                db.referralsDao().clearTable()
            }

    override fun flowSoraAccountsList(): Flow<List<SoraAccount>> =
        db.accountDao().flowAccounts().map { list ->
            list.filter { it.substrateAddress.isNotBlank() }.map {
                SoraAccountMapper.map(it)
            }
        }

    override fun flowUnavailableWalletIds(): Flow<Set<String>> =
        db.accountDao().flowAccountsNotVerified()
            .combine(WalletRecoveryCapabilityGate.modeFlow) { ids, mode ->
                // While wallet changes are off for the whole app, no account is labelled: none
                // can sign, be repaired or be removed, and after an interrupted upgrade check
                // "not verified" only means "not checked yet". The app-wide banner speaks.
                if (WalletRecoveryCapabilityGate.isUserMutationBlocked(mode)) {
                    emptySet()
                } else {
                    ids.filter(::isWalletId).toSet()
                }
            }
            .distinctUntilChanged()

    override fun flowSelectedAccountSigning(): Flow<SelectedAccountSigning> =
        // The selection as this repository holds it (no side effect on collection), with the
        // accounts that are not verified to sign, read from the database on every change.
        flow { emitAll(flowUnavailableWalletIds()) }
            .combine(currentSoraAccount) { readOnly, account ->
                if (account == null) {
                    SelectedAccountSigning.NONE
                } else {
                    SelectedAccountSigning(
                        address = account.substrateAddress,
                        readOnly = account.substrateAddress in readOnly,
                    )
                }
            }
            .distinctUntilChanged()
            // A screen only labels with this; the signer checks the key itself. A failed read
            // (for example a database that is closed for recovery) shows no label.
            .catch { emit(SelectedAccountSigning.NONE) }

    override suspend fun soraAccountsList(): List<SoraAccount> =
        mutex.withLock {
            resumePendingWalletDeletionIfAllowed()
            walletAccounts().map {
                SoraAccountMapper.map(it)
            }
        }

    override suspend fun getSoraAccountsCount(): Int = mutex.withLock {
        resumePendingWalletDeletionIfAllowed()
        walletAccounts().size
    }

    override suspend fun insertSoraAccount(
        soraAccount: SoraAccount,
        newAccount: Boolean,
    ) = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        resumePendingWalletDeletionLocked()
        insertSoraAccountLocked(soraAccount, newAccount)
    }

    private class VerifiedWalletModel(
        val identity: WalletIdentityLocal,
        val networkAccounts: List<NetworkAccountLocal>,
    )

    /**
     * Judges the stored secrets of [soraAccount] and returns the identity and network accounts to
     * record. The stored signing key alone decides whether the account is verified: it must be
     * the key of this address and sign a random challenge. The stored phrase and seed only decide
     * the recorded backup label and whether Nexus accounts are derived. Reads only.
     */
    private suspend fun verifiedWalletModel(soraAccount: SoraAccount): VerifiedWalletModel {
        val secrets = readSecrets(soraAccount.substrateAddress)
        val keyPair = secrets.keyPair
        try {
            val explicitWatchOnly =
                credentialsDatasource.isExplicitWatchOnly(soraAccount.substrateAddress)
            val watchOnlyPublicKey = if (explicitWatchOnly) {
                check(
                    keyPair == null && secrets.mnemonic == "" && secrets.rawSeed == ""
                ) { "WATCH_ONLY_SECRET_CONFLICT" }
                sora2AddressCodec.soraPublicKeyOrNull(soraAccount.substrateAddress)
                    ?: error("WATCH_ONLY_ADDRESS_INVALID")
            } else {
                null
            }
            val verdict = if (keyPair != null) judge(soraAccount.substrateAddress, secrets) else null
            if (verdict != null) {
                check(verdict.canSign) { verdict.signingRefusal.orEmpty() }
            }
            val verifiedPublicKey = keyPair?.publicKey ?: watchOnlyPublicKey
            if (verifiedPublicKey != null) {
                check(
                    sora2AddressCodec.toSoraAddressOrNull(verifiedPublicKey) ==
                        soraAccount.substrateAddress
                ) { "SORA_ADDRESS_MISMATCH" }
            }
            val secretSource = when {
                explicitWatchOnly -> "WATCH_ONLY"
                verdict != null -> verdict.secretSource
                else -> "SECRET_MISSING"
            }
            val migrationState =
                if (
                    verifiedPublicKey != null &&
                    secretSource != "SECRET_MISSING"
                ) "VERIFIED"
                else "PENDING_VERIFICATION"
            val networkAccounts = mutableListOf(
                NetworkAccountLocal(
                    walletId = soraAccount.substrateAddress,
                    networkId = "sora2",
                    publicKey = verifiedPublicKey?.toHex().orEmpty(),
                    address = soraAccount.substrateAddress,
                    derivationPath = "",
                    derivationVersion = 1,
                    enabled = true,
                )
            )
            // Nexus accounts belong only to a 12- or 24-word phrase that re-derives this SORA2
            // address. One that is already recorded as a derivation of this phrase keeps its
            // identity exactly; a missing one is derived from the canonical words.
            val nexusPhrase = verdict?.recoveryPhrase
                ?.takeIf { secretSource == StoredSecretsVerdict.SOURCE_MNEMONIC }
            if (migrationState == "VERIFIED" && nexusPhrase != null) {
                // This comes after the signing decision and can never undo it: if it fails for
                // a reason other than storage, the account is still added as verified and the
                // next start prepares the Nexus accounts.
                networkAccounts += try {
                    NexusChildAccounts.prepare(
                        walletId = soraAccount.substrateAddress,
                        storedPhraseText = secrets.mnemonic.orEmpty(),
                        phrase = nexusPhrase,
                        existing = db.walletIdentityDao()
                            .getNetworkAccounts(soraAccount.substrateAddress)
                            .associateBy(NetworkAccountLocal::networkId),
                        derivationVersion = 1,
                    )
                } catch (error: CancellationException) {
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
                    emptyList()
                }
            }
            return VerifiedWalletModel(
                identity = WalletIdentityLocal(
                    walletId = soraAccount.substrateAddress,
                    displayName = soraAccount.accountName,
                    secretSource = secretSource,
                    migrationState = migrationState,
                    derivationVersion = 1,
                ),
                networkAccounts = networkAccounts,
            )
        } finally {
            secrets.wipe()
        }
    }

    private suspend fun insertSoraAccountLocked(
        soraAccount: SoraAccount,
        newAccount: Boolean,
    ) {
        // The label of the stored backup material is a fact proven from the stored values each
        // time; a label recorded earlier never stops an account whose stored key signs.
        val model = verifiedWalletModel(soraAccount)

        db.withTransaction {
            db.accountDao().insertSoraAccount(
                SoraAccountMapper.map(soraAccount)
            )
            db.walletIdentityDao().upsertWallet(model.identity)
            db.walletIdentityDao().upsertNetworkAccounts(model.networkAccounts)
            db.cardsHubDao().insert(
                CardHubType.entries
                    .filter { it.boundToAccount }
                    .mapIndexed { _, type ->
                        CardHubLocal(
                            cardId = type.hubName,
                            accountAddress = soraAccount.substrateAddress,
                            visibility = if (type == CardHubType.BACKUP) newAccount else true,
                            sortOrder = type.order,
                            collapsed = false,
                        )
                    }
            )
        }
        defaultGlobalCards()
    }

    override suspend fun updateAccountName(soraAccount: SoraAccount, newName: String) {
        mutex.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            resumePendingWalletDeletionLocked()
            db.withTransaction {
                db.accountDao().updateAccountName(newName, soraAccount.substrateAddress)
                db.walletIdentityDao().updateWalletName(soraAccount.substrateAddress, newName)
            }
            check(
                db.walletIdentityDao().getWallet(soraAccount.substrateAddress)
                    ?.migrationState in SELECTABLE_STATES
            ) { "SELECTED_WALLET_NOT_VERIFIED" }
            val updated = soraAccount.copy(accountName = newName)
            userDatasource.setCurAccountAddress(updated.substrateAddress)
            currentSoraAccount.value = updated
        }
    }

    override suspend fun savePin(pin: String) {
        markKeySetsOlderThanThePin()
        userDatasource.savePin(pin)
    }

    /**
     * A PIN that is set while no PIN is stored (the first set-up, or the set-up after "Remove
     * all accounts") was never proven by whoever used this phone before. It must not open what
     * was on the phone before it: every key set that no account uses is marked, before the PIN
     * is saved, as needing its own recovery phrase. Nothing is written when no such key set
     * exists, and accounts are never affected.
     *
     * Changing a PIN (the old one was just entered) marks nothing. Neither does a new PIN in
     * place of a stored one that cannot be read: that state comes from damaged data, not from a
     * hand-over, and marking then could leave a wallet without a phrase with no way back.
     */
    private suspend fun markKeySetsOlderThanThePin() {
        val pinStored = try {
            userDatasource.retrievePin().isNotEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (
                WalletStorageFailures.isTransient(error) ||
                WalletStorageFailures.isKeyMaterialUnavailable(error)
            ) {
                // Not known right now: the PIN is not saved either, and the person tries again.
                throw error
            }
            // A PIN is stored; it cannot be read.
            true
        }
        if (pinStored) return
        val accounts = walletAccounts().map(SoraAccountLocal::substrateAddress).toSet()
        // The release-era unsuffixed set is in use when its recorded owner is an account.
        val releaseEraInUse = credentialsDatasource.getAddress().let { it.isNotBlank() && it in accounts }
        credentialsDatasource.markKeySetsNeedingPhrase(
            exceptIds = if (releaseEraInUse) {
                accounts + WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID
            } else {
                accounts
            },
        )
    }

    override suspend fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override suspend fun saveRegistrationState(onboardingState: OnboardingState) {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        userDatasource.saveRegistrationState(onboardingState)
    }

    override suspend fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override suspend fun createWalletDeletionPreview(
        targetWalletIds: List<String>,
    ): WalletDeletionPreview = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        resumePendingWalletDeletionLocked()
        check(db.walletIdentityDao().getActiveDeletionOperation() == null) {
            "WALLET_DELETION_ALREADY_ACTIVE"
        }
        val accounts = walletAccounts()
        check(accounts.isNotEmpty()) { "WALLET_DELETION_NO_WALLETS" }
        check(accounts.size <= WalletDeletionContract.MAX_TARGET_WALLETS) {
            "WALLET_DELETION_WALLET_LIMIT"
        }
        val installedIds = accounts.map { it.substrateAddress }.toSet()
        val targetIds = targetWalletIds.toSet()
        check(
            targetWalletIds.isNotEmpty() &&
                targetIds.size == targetWalletIds.size &&
                installedIds.containsAll(targetIds)
        ) { "WALLET_DELETION_TARGET_INVALID" }
        requireNoUnresolvedDeletionTransactions(
            walletIds = targetIds,
            unresolvedCode = "WALLET_DELETION_PENDING_TRANSACTIONS",
        )
        val scope = if (targetIds == installedIds) {
            WalletDeletionScope.ALL
        } else {
            check(targetIds.size == 1 && installedIds.size > 1) {
                "WALLET_DELETION_SCOPE_INVALID"
            }
            WalletDeletionScope.SINGLE
        }
        val migrationJournal = db.walletIdentityDao().getMigrationJournal(
            WalletMigrationIds.NETWORK_ACCOUNTS_V1
        )
        check(migrationJournal == null || migrationJournal.state == "VERIFIED") {
            "WALLET_DELETION_MIGRATION_ACTIVE"
        }
        if (currentSoraAccount.value == null) initCurSoraAccount()
        val selectedBefore = requireNotNull(currentSoraAccount.value).substrateAddress
        check(selectedBefore in installedIds) { "WALLET_DELETION_SELECTION_INVALID" }
        requireDeletionWalletSecrets(accounts, targetIds)
        requireDeletionPreferenceCoverage(
            walletIds = installedIds,
            selectedAddress = selectedBefore,
            targetIds = targetIds,
        )
        val selectedAfter = when {
            scope == WalletDeletionScope.ALL -> ""
            selectedBefore !in targetIds -> selectedBefore
            else -> accounts
                .asSequence()
                .filter { it.substrateAddress !in targetIds }
                .sortedBy { it.substrateAddress }
                .first()
                .substrateAddress
        }
        // A release-era owner that is not an account is a set-aside key set: it is neither a
        // deletion target nor a reason to refuse deleting the accounts the person chose.
        val scopeWire = scope.toWire()
        val snapshotHash = walletDeletionSnapshotHash(
            selectedWalletId = selectedBefore,
            scope = scopeWire,
            targetWalletIds = targetIds,
        )
        val removeLegacyUnsuffixed = releaseEraSetRemovableWith(targetIds)
        val preferenceHashes =
            deletionPreferenceHashes(
                walletIds = targetIds,
                selectedAfter = selectedAfter,
                removeLegacyUnsuffixed = removeLegacyUnsuffixed,
                clearAll = scope == WalletDeletionScope.ALL,
            )
        val preview = WalletDeletionPreview(
            previewId = UUID.randomUUID().toString(),
            scope = scope,
            targets = accounts
                .filter { it.substrateAddress in targetIds }
                .sortedBy { it.substrateAddress }
                .map {
                    WalletDeletionTarget(
                        walletId = it.substrateAddress,
                        displayName = it.accountName,
                    )
                },
            selectedBefore = selectedBefore,
            selectedAfter = selectedAfter,
            snapshotHash = snapshotHash,
            beforePreferencesHash = preferenceHashes.before,
            afterPreferencesHash = preferenceHashes.after,
            removeLegacyUnsuffixed = removeLegacyUnsuffixed,
            expiresAtElapsedRealtime =
                SystemClock.elapsedRealtime() + DELETION_PREVIEW_TTL_MILLIS,
        )
        deletionPreviews.clear()
        deletionPreviews[preview.previewId] = preview
        preview
    }

    override suspend fun confirmAndExecuteWalletDeletion(
        preview: WalletDeletionPreview,
    ): WalletDeletionResult = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val retainedPreview = deletionPreviews.remove(preview.previewId)
        check(retainedPreview == preview) { "WALLET_DELETION_PREVIEW_UNKNOWN" }
        check(SystemClock.elapsedRealtime() <= preview.expiresAtElapsedRealtime) {
            "WALLET_DELETION_PREVIEW_EXPIRED"
        }
        check(db.walletIdentityDao().getActiveDeletionOperation() == null) {
            "WALLET_DELETION_ALREADY_ACTIVE"
        }
        val targetIds = preview.targets.map(WalletDeletionTarget::walletId).toSet()
        val scopeWire = preview.scope.toWire()
        val requestedAt = System.currentTimeMillis()
        deletionRequire(
            userDatasource.getCurAccountAddress() == preview.selectedBefore &&
                currentSoraAccount.value?.substrateAddress == preview.selectedBefore,
            "WALLET_DELETION_SELECTION_CHANGED",
        )
        deletionRequire(
            releaseEraSetRemovableWith(targetIds) == preview.removeLegacyUnsuffixed,
            "WALLET_DELETION_LEGACY_OWNER_CHANGED",
        )
        val confirmedPreferenceHashes =
            deletionPreferenceHashes(
                walletIds = targetIds,
                selectedAfter = preview.selectedAfter,
                removeLegacyUnsuffixed = preview.removeLegacyUnsuffixed,
                clearAll = preview.scope == WalletDeletionScope.ALL,
            )
        deletionRequire(
            confirmedPreferenceHashes.before == preview.beforePreferencesHash &&
                confirmedPreferenceHashes.after == preview.afterPreferencesHash,
            "WALLET_DELETION_PREFERENCES_CHANGED",
        )
        val targetIdentities = walletIdentities().filter { it.walletId in targetIds }
        val copyReason = when {
            // The keys of a read-only account could not be verified, so nothing shows that they
            // exist anywhere else.
            targetIdentities.any { it.migrationState == UNAVAILABLE_STATE } ->
                REMOVED_UNAVAILABLE_COPY_REASON
            // An account that signs, but nothing stored on this phone is a proven backup of it
            // (only its key pair is usable; its stored phrase or seed belongs to another wallet
            // or cannot be read). No recovery phrase can be shown before it is removed, and the
            // removal deletes the only key and whatever is stored next to it.
            targetIdentities.any { removalLosesTheOnlyCopy(it) } ->
                REMOVED_WITHOUT_BACKUP_COPY_REASON
            else -> null
        }
        if (copyReason != null) {
            // Keep a copy of the encrypted preference files on the device before the confirmed
            // removal; without that copy nothing is removed.
            deletionRequire(
                WalletUpgradeBackup.preserveWalletPreferencesCopy(copyReason).isSuccess,
                "WALLET_DELETION_PRESERVE_COPY_FAILED",
            )
        }

        db.withTransaction {
            val accounts = walletAccounts()
            val installedIds = accounts.map { it.substrateAddress }.toSet()
            deletionRequire(
                targetIds.isNotEmpty() &&
                    targetIds.size == preview.targets.size &&
                    installedIds.containsAll(targetIds),
                "WALLET_DELETION_TARGET_CHANGED",
            )
            requireNoUnresolvedDeletionTransactions(
                walletIds = targetIds,
                unresolvedCode = "WALLET_DELETION_PENDING_TRANSACTION_RACE",
            )
            deletionRequire(
                when (preview.scope) {
                    WalletDeletionScope.ALL -> targetIds == installedIds
                    WalletDeletionScope.SINGLE ->
                        targetIds.size == 1 && installedIds.size > 1
                },
                "WALLET_DELETION_SCOPE_CHANGED",
            )
            deletionRequire(
                walletDeletionSnapshotHash(
                    selectedWalletId = preview.selectedBefore,
                    scope = scopeWire,
                    targetWalletIds = targetIds,
                ) == preview.snapshotHash,
                "WALLET_DELETION_PREVIEW_STALE",
            )
            val remainingAccounts = accounts.filter {
                it.substrateAddress !in targetIds
            }
            val remainingIdentities = walletIdentities().filter {
                it.walletId !in targetIds
            }
            val remainingNetworkAccounts =
                walletNetworkAccounts().filter {
                    it.walletId !in targetIds
                }
            val afterSnapshotHash = walletDeletionSnapshotHash(
                accounts = remainingAccounts,
                identities = remainingIdentities,
                networkAccounts = remainingNetworkAccounts,
                selectedWalletId = preview.selectedAfter,
                scope = scopeWire,
                targetWalletIds = targetIds,
            )
            val operationBase = WalletDeletionOperationLocal(
                operationId = UUID.randomUUID().toString(),
                activeSlot = WalletDeletionContract.ACTIVE_SLOT,
                formatVersion = WalletDeletionContract.FORMAT_VERSION,
                scope = scopeWire,
                phase = WalletDeletionContract.PHASE_CONFIRMED,
                expectedWalletCount = accounts.size,
                targetCount = targetIds.size,
                selectedBefore = preview.selectedBefore,
                selectedAfter = preview.selectedAfter,
                beforeSnapshotHash = preview.snapshotHash,
                afterSnapshotHash = afterSnapshotHash,
                beforePreferencesHash = preview.beforePreferencesHash,
                afterPreferencesHash = preview.afterPreferencesHash,
                requestDigest = "",
                removeLegacyUnsuffixed = preview.removeLegacyUnsuffixed,
                requestedAt = requestedAt,
                updatedAt = requestedAt,
                failureCode = null,
            )
            val operation = operationBase.copy(
                requestDigest = WalletDeletionIntegrity.requestDigest(
                    operationBase,
                    targetIds,
                )
            )
            db.walletIdentityDao().beginDeletionOperation(
                operation = operation,
                targets = targetIds.sorted().map { walletId ->
                    WalletDeletionTargetLocal(operation.operationId, walletId)
                },
            )
        }
        resumePendingWalletDeletionLocked()
        WalletDeletionResult(
            scope = preview.scope,
            selectedAfter = preview.selectedAfter,
        )
    }

    override suspend fun resumePendingWalletDeletion() {
        mutex.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            resumePendingWalletDeletionLocked()
        }
    }

    override suspend fun walletDeletionFailureCode(): String? =
        db.walletIdentityDao().getActiveDeletionOperation()?.failureCode

    override suspend fun defaultGlobalCards() {
        val count = db.globalCardsHubDao().count()
        if (count == 0) {
            db.globalCardsHubDao().insert(
                CardHubType.entries
                    .filter { !it.boundToAccount }
                    .map { cardType ->
                        GlobalCardHubLocal(
                            cardId = cardType.hubName,
                            visibility = true,
                            sortOrder = cardType.order,
                            collapsed = false,
                        )
                    }
            )
        }
    }

    private suspend fun resumePendingWalletDeletionLocked() {
        var activeOperation = db.walletIdentityDao().getActiveDeletionOperation()
        while (activeOperation != null) {
            activeOperation.failureCode?.let { failureCode ->
                throw WalletDeletionInvariantException(failureCode)
            }
            val operationId = activeOperation.operationId
            try {
                val targets = loadAndValidateDeletionTargets(activeOperation)
                when (activeOperation.phase) {
                    WalletDeletionContract.PHASE_CONFIRMED ->
                        commitDeletionDatabasePhase(activeOperation, targets)
                    WalletDeletionContract.PHASE_DATABASE_COMMITTED ->
                        commitDeletionPreferencesPhase(activeOperation, targets)
                    WalletDeletionContract.PHASE_PREFERENCES_COMMITTED ->
                        finishDeletion(activeOperation, targets)
                    else -> throw WalletDeletionInvariantException(
                        "WALLET_DELETION_PHASE_INVALID"
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: WalletDeletionInvariantException) {
                runCatching {
                    db.walletIdentityDao().recordDeletionFailure(
                        operationId = operationId,
                        failureCode = error.code,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                throw error
            }
            activeOperation = db.walletIdentityDao().getActiveDeletionOperation()
        }
    }

    private suspend fun resumePendingWalletDeletionIfAllowed() {
        if (WalletRecoveryCapabilityGate.mayResumeWalletDeletion()) {
            resumePendingWalletDeletionLocked()
        }
    }

    private suspend fun loadAndValidateDeletionTargets(
        operation: WalletDeletionOperationLocal,
    ): Set<String> {
        val targetRows = db.walletIdentityDao().getDeletionTargets(operation.operationId)
        val targets = targetRows.map(WalletDeletionTargetLocal::walletId).toSet()
        deletionRequire(
            runCatching { UUID.fromString(operation.operationId) }.isSuccess &&
                operation.activeSlot == WalletDeletionContract.ACTIVE_SLOT &&
                operation.formatVersion == WalletDeletionContract.FORMAT_VERSION &&
                operation.scope in setOf(
                    WalletDeletionContract.SCOPE_SINGLE,
                    WalletDeletionContract.SCOPE_ALL,
                ) &&
                operation.phase in setOf(
                    WalletDeletionContract.PHASE_CONFIRMED,
                    WalletDeletionContract.PHASE_DATABASE_COMMITTED,
                    WalletDeletionContract.PHASE_PREFERENCES_COMMITTED,
                ) &&
                operation.expectedWalletCount >= operation.targetCount &&
                operation.expectedWalletCount <=
                WalletDeletionContract.MAX_TARGET_WALLETS &&
                operation.targetCount > 0 &&
                targets.size == operation.targetCount &&
                targetRows.size == targets.size &&
                targetRows.all { it.operationId == operation.operationId } &&
                targets.all { it.isNotBlank() && it.length <= 256 } &&
                operation.failureCode == null &&
                operation.selectedBefore.isNotBlank() &&
                operation.selectedBefore.length <= 256 &&
                operation.selectedAfter.length <= 256 &&
                operation.requestedAt > 0L &&
                operation.updatedAt >= operation.requestedAt &&
                WalletDeletionIntegrity.isSha256(operation.beforeSnapshotHash) &&
                WalletDeletionIntegrity.isSha256(operation.afterSnapshotHash) &&
                WalletPreferenceIntegrity.isSha256(
                    operation.beforePreferencesHash
                ) &&
                WalletPreferenceIntegrity.isSha256(
                    operation.afterPreferencesHash
                ) &&
                operation.requestDigest ==
                WalletDeletionIntegrity.requestDigest(operation, targets),
            "WALLET_DELETION_JOURNAL_INVALID",
        )
        deletionRequire(
            when (operation.scope) {
                WalletDeletionContract.SCOPE_ALL ->
                    operation.targetCount == operation.expectedWalletCount &&
                        operation.selectedAfter.isEmpty()
                WalletDeletionContract.SCOPE_SINGLE ->
                    targets.size == 1 &&
                        operation.selectedAfter.isNotBlank() &&
                        operation.selectedAfter !in targets
                else -> false
            },
            "WALLET_DELETION_SCOPE_INVALID",
        )
        return targets
    }

    private suspend fun commitDeletionDatabasePhase(
        expectedOperation: WalletDeletionOperationLocal,
        expectedTargets: Set<String>,
    ) {
        val currentAccounts = walletAccounts()
        requireDeletionWalletSecrets(currentAccounts, expectedTargets)
        requireDeletionPreferenceCoverage(
            walletIds = currentAccounts.map(SoraAccountLocal::substrateAddress).toSet(),
            selectedAddress = expectedOperation.selectedBefore,
            targetIds = expectedTargets,
        )
        val preferenceHashes =
            deletionPreferenceHashes(
                walletIds = expectedTargets,
                selectedAfter = expectedOperation.selectedAfter,
                removeLegacyUnsuffixed = expectedOperation.removeLegacyUnsuffixed,
                clearAll =
                    expectedOperation.scope == WalletDeletionContract.SCOPE_ALL,
            )
        deletionRequire(
            preferenceHashes.before == expectedOperation.beforePreferencesHash &&
                preferenceHashes.after == expectedOperation.afterPreferencesHash,
            "WALLET_DELETION_PREFERENCES_CHANGED",
        )
        db.withTransaction {
            val operation = db.walletIdentityDao().getActiveDeletionOperation()
                ?: throw WalletDeletionInvariantException(
                    "WALLET_DELETION_OPERATION_MISSING"
                )
            deletionRequire(
                operation == expectedOperation,
                "WALLET_DELETION_OPERATION_CHANGED",
            )
            val targets = loadAndValidateDeletionTargets(operation)
            deletionRequire(
                targets == expectedTargets,
                "WALLET_DELETION_TARGET_CHANGED",
            )
            val accounts = walletAccounts()
            val identities = walletIdentities()
            val networkAccounts = walletNetworkAccounts()
            deletionRequire(
                accounts.size == operation.expectedWalletCount &&
                    accounts.map { it.substrateAddress }.toSet().containsAll(targets),
                "WALLET_DELETION_BEFORE_COUNT_MISMATCH",
            )
            deletionRequire(
                walletDeletionSnapshotHash(
                    accounts = accounts,
                    identities = identities,
                    networkAccounts = networkAccounts,
                    selectedWalletId = operation.selectedBefore,
                    scope = operation.scope,
                    targetWalletIds = targets,
                ) == operation.beforeSnapshotHash,
                "WALLET_DELETION_BEFORE_SNAPSHOT_MISMATCH",
            )

            requireNoUnresolvedDeletionTransactions(
                walletIds = targets,
                unresolvedCode = "WALLET_DELETION_PENDING_TRANSACTION_RACE",
            )
            val expectedPendingRows =
                db.walletIdentityDao().countPendingTransactionRowsForJournal(
                    targets.toList()
                )
            val expectedSora2PendingRows =
                db.walletIdentityDao().countSora2PendingSubmissionRowsForJournal(
                    targets.toList()
                )
            val expectedNetworks =
                networkAccounts.count { it.walletId in targets }
            val expectedIdentities =
                identities.count { it.walletId in targets }
            deletionRequire(
                db.walletIdentityDao().deletePendingTransactionsForJournal(
                    targets.toList()
                ) == expectedPendingRows,
                "WALLET_DELETION_PENDING_COUNT_MISMATCH",
            )
            deletionRequire(
                db.walletIdentityDao().deleteSora2PendingSubmissionsForJournal(
                    targets.toList()
                ) == expectedSora2PendingRows,
                "WALLET_DELETION_SORA2_PENDING_COUNT_MISMATCH",
            )
            deletionRequire(
                db.walletIdentityDao().deleteNetworkAccountsForJournal(
                    targets.toList()
                ) == expectedNetworks,
                "WALLET_DELETION_NETWORK_COUNT_MISMATCH",
            )
            deletionRequire(
                db.walletIdentityDao().deleteWalletIdentitiesForJournal(
                    targets.toList()
                ) == expectedIdentities &&
                    expectedIdentities == targets.size,
                "WALLET_DELETION_IDENTITY_COUNT_MISMATCH",
            )
            deletionRequire(
                db.accountDao().deleteAccountsForJournal(targets.toList()) ==
                    targets.size,
                "WALLET_DELETION_ACCOUNT_COUNT_MISMATCH",
            )
            db.referralsDao().clearTable()
            db.walletIdentityDao().deleteMigrationJournal()
            if (operation.scope == WalletDeletionContract.SCOPE_ALL) {
                db.nodeDao().clearTable()
                db.globalCardsHubDao().clearTable()
                defaultGlobalCards()
            }

            val remainingAccounts = walletAccounts()
            val remainingIdentities = walletIdentities()
            val remainingNetworks = walletNetworkAccounts()
            deletionRequire(
                remainingAccounts.size ==
                    operation.expectedWalletCount - operation.targetCount &&
                    remainingAccounts.none { it.substrateAddress in targets } &&
                    remainingIdentities.none { it.walletId in targets } &&
                    remainingNetworks.none { it.walletId in targets } &&
                    db.walletIdentityDao().countPendingTransactionRowsForJournal(
                        targets.toList()
                    ) == 0 &&
                    db.walletIdentityDao().countSora2PendingSubmissionRowsForJournal(
                        targets.toList()
                    ) == 0,
                "WALLET_DELETION_DATABASE_REMAINS",
            )
            deletionRequire(
                walletDeletionSnapshotHash(
                    accounts = remainingAccounts,
                    identities = remainingIdentities,
                    networkAccounts = remainingNetworks,
                    selectedWalletId = operation.selectedAfter,
                    scope = operation.scope,
                    targetWalletIds = targets,
                ) == operation.afterSnapshotHash,
                "WALLET_DELETION_AFTER_SNAPSHOT_MISMATCH",
            )
            deletionRequire(
                db.walletIdentityDao().markDeletionDatabaseCommitted(
                    operationId = operation.operationId,
                    requestDigest = operation.requestDigest,
                    updatedAt = System.currentTimeMillis(),
                ) == 1,
                "WALLET_DELETION_DATABASE_PHASE_CAS_MISMATCH",
            )
        }
    }

    private suspend fun commitDeletionPreferencesPhase(
        operation: WalletDeletionOperationLocal,
        targets: Set<String>,
    ) {
        commitDeletionPreferences(
            walletIds = targets,
            selectedAfter = operation.selectedAfter,
            removeLegacyUnsuffixed = operation.removeLegacyUnsuffixed,
            clearAll = operation.scope == WalletDeletionContract.SCOPE_ALL,
            expectedBeforeHash = operation.beforePreferencesHash,
            expectedAfterHash = operation.afterPreferencesHash,
        )
        deletionRequire(
            db.walletIdentityDao().markDeletionPreferencesCommitted(
                operationId = operation.operationId,
                requestDigest = operation.requestDigest,
                updatedAt = System.currentTimeMillis(),
            ) == 1,
            "WALLET_DELETION_PREFERENCES_PHASE_CAS_MISMATCH",
        )
    }

    private suspend fun finishDeletion(
        operation: WalletDeletionOperationLocal,
        targets: Set<String>,
    ) {
        // Reapplying the atomic edit makes a crash after DataStore publication but before the
        // Room phase update or journal deletion deterministic and idempotent.
        commitDeletionPreferences(
            walletIds = targets,
            selectedAfter = operation.selectedAfter,
            removeLegacyUnsuffixed = operation.removeLegacyUnsuffixed,
            clearAll = operation.scope == WalletDeletionContract.SCOPE_ALL,
            expectedBeforeHash = operation.beforePreferencesHash,
            expectedAfterHash = operation.afterPreferencesHash,
        )
        currentSoraAccount.value =
            if (operation.scope == WalletDeletionContract.SCOPE_ALL) {
                null
            } else {
                db.accountDao().getAccount(operation.selectedAfter)
                    ?.let(SoraAccountMapper::map)
                    ?: throw WalletDeletionInvariantException(
                        "WALLET_DELETION_REPLACEMENT_MISSING"
                    )
            }
        deletionRequire(
            db.walletIdentityDao().deleteDeletionOperation(operation.operationId) == 1,
            "WALLET_DELETION_COMPLETION_MISMATCH",
        )
    }

    private suspend fun walletDeletionSnapshotHash(
        selectedWalletId: String,
        scope: String,
        targetWalletIds: Set<String>,
    ): String = walletDeletionSnapshotHash(
        accounts = walletAccounts(),
        identities = walletIdentities(),
        networkAccounts = walletNetworkAccounts(),
        selectedWalletId = selectedWalletId,
        scope = scope,
        targetWalletIds = targetWalletIds,
    )

    private fun walletDeletionSnapshotHash(
        accounts: List<SoraAccountLocal>,
        identities: List<WalletIdentityLocal>,
        networkAccounts: List<NetworkAccountLocal>,
        selectedWalletId: String,
        scope: String,
        targetWalletIds: Set<String>,
    ): String = try {
        WalletDeletionIntegrity.snapshotHash(
            accounts = accounts,
            identities = identities,
            networkAccounts = networkAccounts,
            selectedWalletId = selectedWalletId,
            scope = scope,
            targetWalletIds = targetWalletIds,
        )
    } catch (_: IllegalStateException) {
        throw WalletDeletionInvariantException(
            "WALLET_DELETION_SNAPSHOT_INVALID"
        )
    }

    private fun WalletDeletionScope.toWire(): String = when (this) {
        WalletDeletionScope.SINGLE -> WalletDeletionContract.SCOPE_SINGLE
        WalletDeletionScope.ALL -> WalletDeletionContract.SCOPE_ALL
    }

    private fun deletionRequire(condition: Boolean, code: String) {
        if (!condition) throw WalletDeletionInvariantException(code)
    }

    private suspend fun requireNoUnresolvedDeletionTransactions(
        walletIds: Set<String>,
        unresolvedCode: String,
    ) {
        val unresolved = try {
            db.walletIdentityDao().countUnresolvedTransactionsForJournal(
                walletIds.sorted()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) throw error
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_PENDING_JOURNAL_INVALID"
            )
        }
        deletionRequire(unresolved == 0, unresolvedCode)
    }

    private suspend fun requireDeletionPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
        targetIds: Set<String>,
    ) {
        try {
            val unavailable = walletIdentities()
                .filter { it.migrationState == UNAVAILABLE_STATE }
                .map(WalletIdentityLocal::walletId)
                .toSet()
            // A read-only account may hold no key values at all (for example a row whose keys
            // were cleared by an interrupted removal in an older version); it needs no coverage,
            // whether it is being removed or kept.
            credentialsDatasource.requireWalletPreferenceCoverage(
                walletIds = walletIds,
                selectedAddress = selectedAddress,
                requiredCoverage = walletIds.filter { it !in unavailable }.toSet(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) throw error
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_PREFERENCES_UNAVAILABLE"
            )
        }
    }

    /**
     * Whether removing [targetIds] also removes the release-era (unsuffixed) key set. Only when
     * that set is proven to belong to a verified target: `prefs_address_pure` names the target,
     * its keys decrypt and derive exactly that address (or it holds no secret value at all, just
     * the address). A read-only target, an unreadable set or a set whose keys derive another
     * address is kept; it stays on the device as a retained key set ("Restore wallets found on
     * this phone" adopts it after proving it derives the address).
     */
    private suspend fun releaseEraSetRemovableWith(targetIds: Set<String>): Boolean = try {
        val owner = credentialsDatasource.getAddress()
        when {
            owner.isBlank() || owner !in targetIds -> false
            db.walletIdentityDao().getWallet(owner)?.migrationState != "VERIFIED" -> false
            CredentialField.entries.all { field ->
                credentialsDatasource.readStoredCredential(field, "") == StoredCredential.Absent
            } -> true
            else -> releaseEraOwnerIs(owner)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) throw error
        throw WalletDeletionInvariantException("WALLET_DELETION_LEGACY_OWNER_UNVERIFIED")
    }

    /**
     * True for an account that signs while the phone holds no proven backup of it: its label is
     * `LEGACY_SECRET` (only the key pair is usable), or a stored phrase or seed was judged as
     * another wallet's or cannot be read. Removing such an account deletes the only copy of its
     * key (and, possibly, another wallet's phrase) from the app. A watch-only account stores no
     * secret. A Keystore or storage failure here stops the removal: nothing is removed when it is
     * not known what would be lost.
     */
    private suspend fun removalLosesTheOnlyCopy(identity: WalletIdentityLocal): Boolean {
        if (identity.migrationState != "VERIFIED" || identity.secretSource == "WATCH_ONLY") {
            return false
        }
        if (identity.secretSource == StoredSecretsVerdict.SOURCE_LEGACY_SECRET) return true
        val notProven = setOf(BackupMatch.DOES_NOT_MATCH, BackupMatch.UNREADABLE)
        return try {
            val secrets = readSecrets(identity.walletId)
            try {
                val verdict = judge(identity.walletId, secrets)
                !verdict.hasMatchingBackup || verdict.phrase in notProven || verdict.seed in notProven
            } finally {
                secrets.wipe()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) throw error
            throw WalletDeletionInvariantException("WALLET_DELETION_SECRET_VERIFICATION_FAILED")
        }
    }

    private suspend fun requireDeletionWalletSecrets(
        accounts: List<SoraAccountLocal>,
        targetIds: Set<String>,
    ) {
        try {
            val identities = walletIdentities()
                .associateBy(WalletIdentityLocal::walletId)
            deletionRequire(
                identities.size == accounts.size &&
                    identities.keys == accounts
                    .map(SoraAccountLocal::substrateAddress)
                    .toSet(),
                "WALLET_DELETION_IDENTITY_SET_MISMATCH",
            )
            accounts.sortedBy(SoraAccountLocal::substrateAddress).forEach { account ->
                val identity = requireNotNull(identities[account.substrateAddress])
                if (identity.migrationState == UNAVAILABLE_STATE) {
                    // A read-only account's keys are missing or unreadable, so there is nothing
                    // to prove. Another one is untouched by this deletion; removing this one is
                    // the person's confirmed choice (a copy of the old preference files is kept
                    // before anything is removed).
                    return@forEach
                }
                deletionRequire(
                    identity.migrationState == "VERIFIED",
                    "WALLET_DELETION_WALLET_NOT_VERIFIED",
                )
                val secrets = readSecrets(account.substrateAddress)
                val keyPair = secrets.keyPair
                try {
                    val watchOnly = credentialsDatasource.isExplicitWatchOnly(
                        account.substrateAddress
                    )
                    if (identity.secretSource == "WATCH_ONLY") {
                        deletionRequire(
                            watchOnly &&
                                keyPair == null &&
                                secrets.mnemonic == "" &&
                                secrets.rawSeed == "" &&
                                sora2AddressCodec.soraPublicKeyOrNull(
                                    account.substrateAddress
                                ) != null,
                            "WALLET_DELETION_WATCH_ONLY_MISMATCH",
                        )
                    } else {
                        // A verified account is the one whose stored key signs for its address.
                        // That is what is proven again before anything is removed; the stored
                        // phrase and seed are backup material and do not block the removal the
                        // person chose.
                        deletionRequire(
                            identity.secretSource in DELETABLE_SECRET_SOURCES,
                            "WALLET_DELETION_SECRET_SOURCE_UNSUPPORTED",
                        )
                        deletionRequire(!watchOnly, "WALLET_DELETION_WATCH_ONLY_MISMATCH")
                        deletionRequire(keyPair != null, "WALLET_DELETION_SIGNING_KEY_MISSING")
                        deletionRequire(
                            sora2AddressCodec.toSoraAddressOrNull(keyPair?.publicKey) ==
                                account.substrateAddress,
                            "WALLET_DELETION_SORA_ADDRESS_MISMATCH",
                        )
                        deletionRequire(
                            judge(account.substrateAddress, secrets).canSign,
                            "WALLET_DELETION_SIGNING_KEY_MISMATCH",
                        )
                    }
                } finally {
                    secrets.wipe()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: WalletDeletionInvariantException) {
            throw error
        } catch (error: Throwable) {
            // A busy Keystore, disk-full or preference I/O failure is not a deletion invariant.
            // Preserve the phase and retry it; recording it would permanently latch the journal.
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) {
                throw error
            }
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_SECRET_VERIFICATION_FAILED"
            )
        }
    }

    private suspend fun deletionPreferenceHashes(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
    ): WalletPreferenceIntegrity.Hashes = try {
        credentialsDatasource.previewWalletDeletionPreferences(
            walletIds = walletIds,
            selectedAfter = selectedAfter,
            removeLegacyUnsuffixed = removeLegacyUnsuffixed,
            clearAll = clearAll,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
            // A busy Keystore, disk-full or preference I/O failure is not a deletion invariant.
            // Preserve the phase and retry it; recording it would permanently latch the journal.
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) {
                throw error
            }
        throw WalletDeletionInvariantException(
            "WALLET_DELETION_PREFERENCES_UNAVAILABLE"
        )
    }

    private suspend fun commitDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
        expectedBeforeHash: String,
        expectedAfterHash: String,
    ) {
        try {
            credentialsDatasource.commitWalletDeletionPreferences(
                walletIds = walletIds,
                selectedAfter = selectedAfter,
                removeLegacyUnsuffixed = removeLegacyUnsuffixed,
                clearAll = clearAll,
                expectedBeforeHash = expectedBeforeHash,
                expectedAfterHash = expectedAfterHash,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // A busy Keystore, disk-full or preference I/O failure is not a deletion invariant.
            // Preserve the phase and retry it; recording it would permanently latch the journal.
            if (WalletStorageFailures.isTransient(error) || WalletStorageFailures.isKeyMaterialUnavailable(error)) {
                throw error
            }
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_PREFERENCES_UNAVAILABLE"
            )
        }
    }

    override suspend fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return userDatasource.getParentInviteCode()
    }

    override suspend fun getAvailableLanguages(): Pair<List<Language>, Int> {
        return languagesHolder.getLanguages()
    }

    override suspend fun changeLanguage(language: String): String {
        languagesHolder.setCurrentLanguage(language)
        return language
    }

    override suspend fun setBiometryEnabled(isEnabled: Boolean) {
        userDatasource.setBiometryEnabled(isEnabled)
    }

    override suspend fun isBiometryEnabled(): Boolean {
        return userDatasource.isBiometryEnabled()
    }

    override suspend fun setBiometryAvailable(biometryAvailable: Boolean) {
        userDatasource.setBiometryAvailable(biometryAvailable)
    }

    override suspend fun isBiometryAvailable(): Boolean {
        return userDatasource.isBiometryAvailable()
    }

    override suspend fun getAccountNameForMigration(): String {
        return userDatasource.getAccountName()
    }

    override suspend fun saveNeedsMigration(it: Boolean, soraAccount: SoraAccount) {
        userDatasource.saveNeedsMigration(it, soraAccount.substrateAddress)
    }

    override suspend fun needsMigration(soraAccount: SoraAccount): Boolean {
        return userDatasource.needsMigration(soraAccount.substrateAddress)
    }

    override suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: SoraAccount) {
        userDatasource.saveIsMigrationFetched(it, soraAccount.substrateAddress)
    }

    override suspend fun isMigrationFetched(soraAccount: SoraAccount): Boolean {
        return userDatasource.isMigrationStatusFetched(soraAccount.substrateAddress) &&
            !userDatasource.needsMigration(soraAccount.substrateAddress)
    }

    override suspend fun getSoraAccount(address: String): SoraAccount {
        return mutex.withLock {
            resumePendingWalletDeletionIfAllowed()
            val soraAccountLocal = requireNotNull(db.accountDao().getAccount(address))
            SoraAccountMapper.map(soraAccountLocal)
        }
    }

    override suspend fun savePinTriesUsed(triesUsed: Int) {
        userDatasource.savePinTriesUsed(triesUsed)
    }

    override suspend fun saveTimerStartedTimestamp(timestamp: Long) {
        userDatasource.saveTimerStartedTimestamp(timestamp)
    }

    override suspend fun retrievePinTriesUsed(): Int = userDatasource.retrievePinTriesUsed()

    override suspend fun retrieveTimerStartedTimestamp(): Long =
        userDatasource.retrieveTimerStartedTimestamp()

    override suspend fun resetTimerStartedTimestamp() {
        userDatasource.resetTimerStartedTimestamp()
    }

    override suspend fun accountExists(address: String): Boolean {
        return mutex.withLock {
            resumePendingWalletDeletionIfAllowed()
            db.accountDao().getAccount(address) != null
        }
    }

    override suspend fun resetTriesUsed() {
        userDatasource.resetPinTriesUsed()
    }

    override suspend fun adoptRetainedWallet(
        address: String,
        accountName: String,
        makeCurrent: Boolean,
    ): SoraAccount = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        resumePendingWalletDeletionLocked()
        check(db.accountDao().getAccount(address) == null) { "RETAINED_WALLET_ALREADY_ACCOUNT" }
        // A key set that "Remove all accounts" left behind lost the PIN that protected it. It
        // comes back only with its own recovery phrase, raw seed or backup file (the import
        // adopts the stored keys unchanged), never from here.
        check(!retainedKeySetNeedsPhrase(address)) { RETAINED_WALLET_NEEDS_RECOVERY_PHRASE }
        // The address's own key set, or the release-era unsuffixed set when its public key derives
        // exactly this address (an account removed in an older version kept those keys).
        val secrets = readSecrets(address)
        try {
            val keys = secrets.keyPair ?: error("RETAINED_WALLET_KEYS_MISSING")
            check(sora2AddressCodec.toSoraAddressOrNull(keys.publicKey) == address) {
                "RETAINED_WALLET_ADDRESS_MISMATCH"
            }
        } finally {
            secrets.wipe()
        }
        val account = SoraAccount(address, accountName)
        // Verifies the stored key pair and secret before adding the row; writes no key material.
        insertSoraAccountLocked(account, newAccount = false)
        check(
            db.walletIdentityDao().getWallet(address)?.migrationState == "VERIFIED"
        ) { "RETAINED_WALLET_NOT_VERIFIED" }
        // The current selection is kept when asked and it still names an account; otherwise
        // the restored wallet becomes the selected one (there is nothing else to show).
        val keepSelection = !makeCurrent &&
            userDatasource.getCurAccountAddress().let { current ->
                current.isNotBlank() && current != address && db.accountDao().getAccount(current) != null
            }
        if (!keepSelection) {
            userDatasource.setCurAccountAddress(address)
            currentSoraAccount.value = account
        }
        userDatasource.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        account
    }

    /**
     * Whether the key set [adoptRetainedWallet] would use for [address] is marked as needing its
     * recovery phrase: the set stored under the address when one is stored there (even partly),
     * otherwise the release-era unsuffixed set. When the marks cannot be read, the answer is
     * yes: restoring without the phrase is only offered when it is known to be allowed.
     */
    private suspend fun retainedKeySetNeedsPhrase(address: String): Boolean = try {
        val marked = credentialsDatasource.retainedKeySetsNeedingPhrase()
        when {
            marked.isEmpty() -> false
            listOf(
                CredentialField.PRIVATE_KEY,
                CredentialField.PUBLIC_KEY,
                CredentialField.KEY_NONCE,
            ).any { field ->
                credentialsDatasource.readStoredCredential(field, address) !=
                    StoredCredential.Absent
            } -> address in marked
            else -> WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID in marked
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        true
    }

    override suspend fun keySetProvenByItsBackup(address: String) {
        mutex.withLock {
            // The marker is wallet state: it is written only where wallet changes are allowed
            // (the import that proves a wallet runs only there, too).
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            if (!isWalletId(address)) return@withLock
            val marked = credentialsDatasource.retainedKeySetsNeedingPhrase()
            if (marked.isEmpty()) return@withLock
            val proven = buildSet {
                if (address in marked) add(address)
                // The release-era set is the same wallet when its keys derive this address.
                if (
                    WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID in marked &&
                    releaseEraOwnerIs(address)
                ) {
                    add(WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID)
                }
            }
            if (proven.isNotEmpty()) credentialsDatasource.unmarkKeySetsNeedingPhrase(proven)
        }
    }

    override suspend fun retainedWallets(): List<RetainedWallet> = mutex.withLock {
        if (WalletRecoveryCapabilityGate.isUserMutationBlocked()) return@withLock emptyList()
        WalletUpgradeBackup.quarantine().retainedWalletIds
            .filter { isWalletId(it) && db.accountDao().getAccount(it) == null }
            .sorted()
            .map { address ->
                RetainedWallet(
                    address = address,
                    needsRecoveryPhrase = retainedKeySetNeedsPhrase(address),
                )
            }
    }

    override suspend fun unavailableWalletIds(): Set<String> {
        val identities = db.walletIdentityDao().getWallets()
            .associateBy(WalletIdentityLocal::walletId)
        return walletAccounts()
            .map(SoraAccountLocal::substrateAddress)
            .filter { identities[it]?.migrationState != "VERIFIED" }
            .toSet()
    }

    override suspend fun unavailableWalletOptions(
        address: String,
    ): UnavailableWalletOptions? = mutex.withLock {
        // Repairing and removing are refused while wallet changes are off for the whole app,
        // so no option is offered there (the app-wide banner has the way forward).
        if (WalletRecoveryCapabilityGate.isUserMutationBlocked()) return@withLock null
        if (!isWalletId(address) || db.accountDao().getAccount(address) == null) {
            return@withLock null
        }
        val identityState = db.walletIdentityDao().getWallet(address)?.migrationState
        if (identityState == "VERIFIED") {
            return@withLock null
        }
        val stored = CredentialField.entries.associateWith { field ->
            credentialsDatasource.readStoredCredential(field, address)
        }
        val unreadable = stored.values.any { it == StoredCredential.Unreadable }
        val watchOnly = credentialsDatasource.isExplicitWatchOnly(address)
        val scopedPhraseOrSeed =
            stored[CredentialField.MNEMONIC] is StoredCredential.Present ||
                stored[CredentialField.SEED] is StoredCredential.Present
        // Removing the account deletes its address-scoped values. A readable private key, phrase
        // or seed among them would be lost from the app.
        val removalDeletesReadableSecret = scopedPhraseOrSeed ||
            stored[CredentialField.PRIVATE_KEY] is StoredCredential.Present
        // A release-era (unsuffixed) copy whose keys derive this address. Removing a read-only
        // account never removes it, so it can be adopted again afterwards.
        val releaseEraCopy = releaseEraOwnerIs(address)
        val releaseEraPhraseOrSeed = releaseEraCopy && listOf(
            CredentialField.MNEMONIC,
            CredentialField.SEED,
        ).any { field ->
            credentialsDatasource.readStoredCredential(field, "") is StoredCredential.Present
        }
        val keyValues = listOf(
            stored[CredentialField.PRIVATE_KEY],
            stored[CredentialField.PUBLIC_KEY],
            stored[CredentialField.KEY_NONCE],
        )
        val reason = when {
            watchOnly -> UnavailableWalletReason.WATCH_ONLY
            keyValues.any { it == StoredCredential.Unreadable } ->
                UnavailableWalletReason.KEY_UNREADABLE
            // A release-era copy that derives this address stands in when nothing is stored
            // under the address; it was judged and refused, so it is a key that is not usable.
            keyValues.all { it == StoredCredential.Absent } ->
                if (releaseEraCopy) {
                    UnavailableWalletReason.KEY_NOT_USABLE
                } else {
                    UnavailableWalletReason.KEY_MISSING
                }
            keyValues.any { it == StoredCredential.Absent } ->
                UnavailableWalletReason.KEY_INCOMPLETE
            // A complete, readable key is "not usable" only when a check said so. An identity
            // that was never judged (the row an upgrade creates before its first check) says
            // nothing about the key.
            identityState != UNAVAILABLE_STATE -> UnavailableWalletReason.NOT_CHECKED
            else -> UnavailableWalletReason.KEY_NOT_USABLE
        }
        UnavailableWalletOptions(
            walletId = address,
            reason = reason,
            // Importing the account's phrase or seed adds the missing values. When a stored value
            // differs or cannot be read, the same import offers a confirmed repair that keeps a
            // copy of the old values and writes the proven ones. Never needs removal.
            canRepairWithPhrase = !watchOnly,
            repairReplacesStoredValues = unreadable,
            // Offered only when removal loses nothing readable, or when the readable secret is a
            // phrase or seed the person is shown first.
            canRemoveAndImport = !removalDeletesReadableSecret || scopedPhraseOrSeed,
            canShowPhraseAfterPin = scopedPhraseOrSeed || releaseEraPhraseOrSeed,
            showPhraseBeforeRemoval = scopedPhraseOrSeed,
            hasReleaseEraCopy = releaseEraCopy,
        )
    }

    override suspend fun completeUnavailableWallet(address: String): SoraAccount =
        mutex.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            resumePendingWalletDeletionLocked()
            val row = db.accountDao().getAccount(address) ?: error("UNAVAILABLE_WALLET_MISSING")
            check(db.walletIdentityDao().getWallet(address)?.migrationState != "VERIFIED") {
                "WALLET_ALREADY_AVAILABLE"
            }
            val account = SoraAccountMapper.map(row)
            val model = verifiedWalletModel(account)
            check(model.identity.migrationState == "VERIFIED") { "UNAVAILABLE_WALLET_NOT_VERIFIED" }
            // Only the derived identity and network rows change; the account row and every key
            // value stay exactly as stored.
            db.withTransaction {
                db.walletIdentityDao().upsertWallet(model.identity)
                db.walletIdentityDao().upsertNetworkAccounts(model.networkAccounts)
            }
            if (currentSoraAccount.value?.substrateAddress == address) {
                currentSoraAccount.value = account
            }
            account
        }

    override suspend fun refreshVerifiedWallet(address: String) {
        mutex.withLock {
            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            resumePendingWalletDeletionLocked()
            val row = db.accountDao().getAccount(address) ?: return@withLock
            if (db.walletIdentityDao().getWallet(address)?.migrationState != "VERIFIED") {
                return@withLock
            }
            val model = verifiedWalletModel(SoraAccountMapper.map(row))
            // Only ever records an account that signs: this never makes one read-only.
            if (model.identity.migrationState != "VERIFIED") return@withLock
            db.withTransaction {
                db.walletIdentityDao().upsertWallet(model.identity)
                db.walletIdentityDao().upsertNetworkAccounts(model.networkAccounts)
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

private class WalletDeletionInvariantException(
    val code: String,
) : IllegalStateException(code)
