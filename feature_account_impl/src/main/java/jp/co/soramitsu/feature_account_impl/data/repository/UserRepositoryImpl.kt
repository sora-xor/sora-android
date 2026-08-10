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

import android.os.SystemClock
import androidx.room.withTransaction
import java.security.MessageDigest
import java.util.UUID
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
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
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionResult
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionTarget
import jp.co.soramitsu.feature_account_api.domain.model.WalletMutationSnapshot
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Singleton
class UserRepositorySr25519Crypto @Inject constructor() {

    fun generateKeypair(seed: ByteArray): Sr25519Keypair? =
        SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            seed,
        ) as? Sr25519Keypair

    fun signAndVerify(
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
    private val runtimeManager: RuntimeManager,
    private val userRepositorySr25519Crypto: UserRepositorySr25519Crypto,
) : UserRepository {

    private companion object {
        const val DELETION_PREVIEW_TTL_MILLIS = 2 * 60 * 1_000L
        const val LEGACY_SORA_MNEMONIC_WORD_COUNT = 15
        const val LEGACY_SECRET_SIGNING_CHALLENGE =
            "sora-wallet-legacy-secret-self-consistency-v1"
    }

    private val currentSoraAccount = MutableStateFlow<SoraAccount?>(null)

    private val mutex = WalletMutationCoordinator
    private val deletionPreviews = mutableMapOf<String, WalletDeletionPreview>()

    init {
        if (
            WalletUpgradeBackup.blockingFailure() == null &&
            WalletRecoveryCapabilityGate.mayResumeWalletDeletion()
        ) {
            coroutineManager.applicationScope.launch {
                mutex.withLock {
                    resumePendingWalletDeletionLocked()
                    initCurSoraAccount()
                }
            }
        }
    }

    private suspend fun initCurSoraAccount() {
        val curAddress = userDatasource.getCurAccountAddress()
        val accounts = db.accountDao().getAccounts()
        if (accounts.isEmpty()) {
            check(curAddress.isEmpty()) { "SELECTED_WALLET_WITHOUT_ACCOUNT" }
            currentSoraAccount.value = null
            return
        }
        check(curAddress.isNotEmpty()) { "SELECTED_WALLET_MISSING" }
        val selected = accounts.singleOrNull {
            it.substrateAddress == curAddress
        } ?: error("SELECTED_WALLET_MISMATCH")
        currentSoraAccount.value = SoraAccountMapper.map(selected)
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
            check(
                db.accountDao().getAccount(soraAccount.substrateAddress) != null &&
                    db.walletIdentityDao().getWallet(soraAccount.substrateAddress)
                        ?.migrationState == "VERIFIED"
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
            list.map {
                SoraAccountMapper.map(it)
            }
        }

    override suspend fun soraAccountsList(): List<SoraAccount> =
        mutex.withLock {
            resumePendingWalletDeletionIfAllowed()
            db.accountDao().getAccounts().map {
                SoraAccountMapper.map(it)
            }
        }

    override suspend fun getSoraAccountsCount(): Int = mutex.withLock {
        resumePendingWalletDeletionIfAllowed()
        db.accountDao().getAccountsCount()
    }

    override suspend fun insertSoraAccount(
        soraAccount: SoraAccount,
        newAccount: Boolean,
    ) = mutex.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        resumePendingWalletDeletionLocked()
        insertSoraAccountLocked(soraAccount, newAccount)
    }

    private suspend fun insertSoraAccountLocked(
        soraAccount: SoraAccount,
        newAccount: Boolean,
    ) {
        val mnemonic = credentialsDatasource.retrieveMnemonic(soraAccount.substrateAddress)
        val rawSeed = credentialsDatasource.retrieveSeed(soraAccount.substrateAddress)
        val keyPair = credentialsDatasource.retrieveKeys(soraAccount.substrateAddress)
        try {
            val explicitWatchOnly =
                credentialsDatasource.isExplicitWatchOnly(soraAccount.substrateAddress)
            val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val watchOnlyPublicKey = if (explicitWatchOnly) {
                check(keyPair == null && mnemonic.isBlank() && rawSeed.isBlank()) {
                    "WATCH_ONLY_SECRET_CONFLICT"
                }
                runtimeManager.soraPublicKeyOrNull(soraAccount.substrateAddress)
                    ?: error("WATCH_ONLY_ADDRESS_INVALID")
            } else {
                null
            }
            val verifiedPublicKey = keyPair?.publicKey ?: watchOnlyPublicKey
            if (verifiedPublicKey != null) {
                check(
                    runtimeManager.toSoraAddressOrNull(verifiedPublicKey) ==
                        soraAccount.substrateAddress
                ) { "SORA_ADDRESS_MISMATCH" }
            }
            if (keyPair != null) {
                if (mnemonic.isNotBlank() || rawSeed.isNotBlank()) {
                    verifyStoredSigningKey(keyPair, mnemonic, rawSeed)
                } else if (!explicitWatchOnly) {
                    verifyLegacySecretSigningKey(keyPair)
                }
            }
            val secretSource = when {
                explicitWatchOnly -> "WATCH_ONLY"
                words.size == 12 || words.size == 24 -> "MNEMONIC"
                mnemonic.isNotBlank() -> {
                    check(words.size == LEGACY_SORA_MNEMONIC_WORD_COUNT) {
                        "SORA_MNEMONIC_WORD_COUNT_UNSUPPORTED"
                    }
                    "MNEMONIC_UNSUPPORTED"
                }
                rawSeed.isNotBlank() -> "RAW_SEED"
                keyPair != null -> "LEGACY_SECRET"
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
            if (migrationState == "VERIFIED" && secretSource == "MNEMONIC") {
                NexusNetworks.all.forEach { network ->
                    val derived = IrohaKeyDerivation.derive(mnemonic, network)
                    try {
                        networkAccounts += NetworkAccountLocal(
                            walletId = soraAccount.substrateAddress,
                            networkId = network.id.wireId,
                            publicKey = derived.publicKey.toHex(),
                            address = derived.address,
                            derivationPath = network.derivationPath,
                            derivationVersion = 1,
                            enabled = network.enabledByDefault,
                        )
                    } finally {
                        derived.destroy()
                    }
                }
            }

            val activeIdentity = db.walletIdentityDao()
                .getWallet(soraAccount.substrateAddress)
            check(
                activeIdentity == null ||
                    activeIdentity.migrationState != "VERIFIED" ||
                    activeIdentity.secretSource == secretSource
            ) { "WALLET_SECRET_SOURCE_CONTINUITY_MISMATCH" }

            db.withTransaction {
                db.accountDao().insertSoraAccount(
                    SoraAccountMapper.map(soraAccount)
                )
                db.walletIdentityDao().upsertWallet(
                    WalletIdentityLocal(
                        walletId = soraAccount.substrateAddress,
                        displayName = soraAccount.accountName,
                        secretSource = secretSource,
                        migrationState = migrationState,
                        derivationVersion = 1,
                    )
                )
                db.walletIdentityDao().upsertNetworkAccounts(networkAccounts)
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
        } finally {
            keyPair?.privateKey?.fill(0)
            keyPair?.nonce?.fill(0)
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
                    ?.migrationState == "VERIFIED"
            ) { "SELECTED_WALLET_NOT_VERIFIED" }
            val updated = soraAccount.copy(accountName = newName)
            userDatasource.setCurAccountAddress(updated.substrateAddress)
            currentSoraAccount.value = updated
        }
    }

    override suspend fun savePin(pin: String) {
        userDatasource.savePin(pin)
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
        val accounts = db.accountDao().getAccounts()
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
        requireDeletionWalletSecrets(accounts)
        requireDeletionPreferenceCoverage(
            walletIds = installedIds,
            selectedAddress = selectedBefore,
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
        val legacyAddress = credentialsDatasource.getAddress()
        check(legacyAddress.isBlank() || legacyAddress in installedIds) {
            "WALLET_DELETION_LEGACY_OWNER_UNKNOWN"
        }
        val scopeWire = scope.toWire()
        val snapshotHash = walletDeletionSnapshotHash(
            selectedWalletId = selectedBefore,
            scope = scopeWire,
            targetWalletIds = targetIds,
        )
        val removeLegacyUnsuffixed = legacyAddress.isNotBlank() &&
            legacyAddress in targetIds
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
        val legacyAddress = credentialsDatasource.getAddress()
        deletionRequire(
            (legacyAddress.isNotBlank() && legacyAddress in targetIds) ==
                preview.removeLegacyUnsuffixed,
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

        db.withTransaction {
            val accounts = db.accountDao().getAccounts()
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
            val remainingIdentities = db.walletIdentityDao().getWallets().filter {
                it.walletId !in targetIds
            }
            val remainingNetworkAccounts =
                db.walletIdentityDao().getAllNetworkAccounts().filter {
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
        val currentAccounts = db.accountDao().getAccounts()
        requireDeletionWalletSecrets(currentAccounts)
        requireDeletionPreferenceCoverage(
            walletIds = currentAccounts.map(SoraAccountLocal::substrateAddress).toSet(),
            selectedAddress = expectedOperation.selectedBefore,
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
            val accounts = db.accountDao().getAccounts()
            val identities = db.walletIdentityDao().getWallets()
            val networkAccounts = db.walletIdentityDao().getAllNetworkAccounts()
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

            val remainingAccounts = db.accountDao().getAccounts()
            val remainingIdentities = db.walletIdentityDao().getWallets()
            val remainingNetworks = db.walletIdentityDao().getAllNetworkAccounts()
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
        accounts = db.accountDao().getAccounts(),
        identities = db.walletIdentityDao().getWallets(),
        networkAccounts = db.walletIdentityDao().getAllNetworkAccounts(),
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
        } catch (_: Throwable) {
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_PENDING_JOURNAL_INVALID"
            )
        }
        deletionRequire(unresolved == 0, unresolvedCode)
    }

    private suspend fun requireDeletionPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
    ) {
        try {
            credentialsDatasource.requireWalletPreferenceCoverage(
                walletIds = walletIds,
                selectedAddress = selectedAddress,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_PREFERENCES_UNAVAILABLE"
            )
        }
    }

    private suspend fun requireDeletionWalletSecrets(
        accounts: List<SoraAccountLocal>,
    ) {
        try {
            val identities = db.walletIdentityDao().getWallets()
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
                deletionRequire(
                    identity.migrationState == "VERIFIED",
                    "WALLET_DELETION_WALLET_NOT_VERIFIED",
                )
                val mnemonic =
                    credentialsDatasource.retrieveMnemonic(account.substrateAddress)
                val rawSeed =
                    credentialsDatasource.retrieveSeed(account.substrateAddress)
                val keyPair =
                    credentialsDatasource.retrieveKeys(account.substrateAddress)
                try {
                    val watchOnly = credentialsDatasource.isExplicitWatchOnly(
                        account.substrateAddress
                    )
                    val words = mnemonic.trim()
                        .split(Regex("\\s+"))
                        .filter(String::isNotBlank)
                    when (identity.secretSource) {
                        "WATCH_ONLY" -> {
                            deletionRequire(
                                watchOnly &&
                                    keyPair == null &&
                                    mnemonic.isBlank() &&
                                    rawSeed.isBlank() &&
                                    runtimeManager.soraPublicKeyOrNull(
                                        account.substrateAddress
                                    ) != null,
                                "WALLET_DELETION_WATCH_ONLY_MISMATCH",
                            )
                        }
                        "MNEMONIC" -> {
                            val signingKey = keyPair
                                ?: throw WalletDeletionInvariantException(
                                    "WALLET_DELETION_MNEMONIC_MISSING"
                                )
                            deletionRequire(
                                !watchOnly &&
                                    words.size in setOf(12, 24),
                                "WALLET_DELETION_MNEMONIC_MISSING",
                            )
                            verifyDeletionSigningKey(
                                account,
                                signingKey,
                                mnemonic,
                                rawSeed,
                            )
                        }
                        "MNEMONIC_UNSUPPORTED" -> {
                            val signingKey = keyPair
                                ?: throw WalletDeletionInvariantException(
                                    "WALLET_DELETION_MNEMONIC_MISSING"
                                )
                            deletionRequire(
                                !watchOnly &&
                                    words.size == LEGACY_SORA_MNEMONIC_WORD_COUNT,
                                "WALLET_DELETION_MNEMONIC_MISSING",
                            )
                            verifyDeletionSigningKey(
                                account,
                                signingKey,
                                mnemonic,
                                rawSeed,
                            )
                        }
                        "RAW_SEED" -> {
                            val signingKey = keyPair
                                ?: throw WalletDeletionInvariantException(
                                    "WALLET_DELETION_RAW_SEED_MISSING"
                                )
                            deletionRequire(
                                !watchOnly &&
                                    mnemonic.isBlank() &&
                                    rawSeed.isNotBlank(),
                                "WALLET_DELETION_RAW_SEED_MISSING",
                            )
                            verifyDeletionSigningKey(
                                account,
                                signingKey,
                                mnemonic,
                                rawSeed,
                            )
                        }
                        "LEGACY_SECRET" -> {
                            val signingKey = keyPair
                                ?: throw WalletDeletionInvariantException(
                                    "WALLET_DELETION_LEGACY_SECRET_MISSING"
                                )
                            deletionRequire(
                                !watchOnly &&
                                    mnemonic.isBlank() &&
                                    rawSeed.isBlank(),
                                "WALLET_DELETION_LEGACY_SECRET_MISMATCH",
                            )
                            deletionRequire(
                                runtimeManager.toSoraAddressOrNull(
                                    signingKey.publicKey
                                ) == account.substrateAddress,
                                "WALLET_DELETION_SORA_ADDRESS_MISMATCH",
                            )
                            verifyLegacySecretSigningKey(signingKey)
                        }
                        else -> throw WalletDeletionInvariantException(
                            "WALLET_DELETION_SECRET_SOURCE_UNSUPPORTED"
                        )
                    }
                } finally {
                    keyPair?.privateKey?.fill(0)
                    keyPair?.nonce?.fill(0)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: WalletDeletionInvariantException) {
            throw error
        } catch (_: Throwable) {
            throw WalletDeletionInvariantException(
                "WALLET_DELETION_SECRET_VERIFICATION_FAILED"
            )
        }
    }

    private fun verifyDeletionSigningKey(
        account: SoraAccountLocal,
        keyPair: Sr25519Keypair,
        mnemonic: String,
        rawSeed: String,
    ) {
        deletionRequire(
            runtimeManager.toSoraAddressOrNull(keyPair.publicKey) ==
                account.substrateAddress,
            "WALLET_DELETION_SORA_ADDRESS_MISMATCH",
        )
        verifyStoredSigningKey(keyPair, mnemonic, rawSeed)
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
    } catch (_: Throwable) {
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
        } catch (_: Throwable) {
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

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun verifyStoredSigningKey(
        stored: Sr25519Keypair,
        mnemonic: String,
        rawSeed: String,
    ) {
        var mnemonicSeed: ByteArray? = null
        var storedSeed: ByteArray? = null
        try {
            mnemonicSeed = if (mnemonic.isNotBlank()) {
                SubstrateSeedFactory.deriveSeed32(
                    MnemonicCreator.fromWords(mnemonic).words,
                    null,
                ).seed
            } else {
                null
            }
            storedSeed = if (rawSeed.isNotBlank()) {
                rawSeed.removePrefix("0x").removePrefix("0X").fromHex()
            } else {
                null
            }
            val seed = mnemonicSeed ?: storedSeed
                ?: throw IllegalStateException("SORA_SECRET_SOURCE_MISSING")
            if (
                mnemonicSeed != null &&
                storedSeed != null &&
                !MessageDigest.isEqual(mnemonicSeed, storedSeed)
            ) {
                throw IllegalStateException("SORA_STORED_SEED_MISMATCH")
            }
            val regenerated = userRepositorySr25519Crypto.generateKeypair(seed)
                ?: throw IllegalStateException("SORA_KEY_TYPE_MISMATCH")
            try {
                check(
                    MessageDigest.isEqual(stored.publicKey, regenerated.publicKey) &&
                        MessageDigest.isEqual(stored.privateKey, regenerated.privateKey) &&
                        MessageDigest.isEqual(stored.nonce, regenerated.nonce)
                ) { "SORA_SIGNING_KEY_MISMATCH" }
            } finally {
                regenerated.privateKey.fill(0)
                regenerated.nonce.fill(0)
            }
        } finally {
            mnemonicSeed?.fill(0)
            storedSeed?.fill(0)
        }
    }

    /**
     * Verifies a retained keypair-only wallet without inventing a mnemonic or seed. The caller
     * separately binds the public key to the wallet's unchanged SORA2 address.
     */
    private fun verifyLegacySecretSigningKey(stored: Sr25519Keypair) {
        val challenge = LEGACY_SECRET_SIGNING_CHALLENGE.encodeToByteArray()
        try {
            check(
                userRepositorySr25519Crypto.signAndVerify(
                    keypair = stored,
                    challenge = challenge,
                    expectedPublicKey = stored.publicKey,
                )
            ) { "SORA_SIGNING_KEY_MISMATCH" }
        } finally {
            challenge.fill(0)
        }
    }

}

private class WalletDeletionInvariantException(
    val code: String,
) : IllegalStateException(code)
