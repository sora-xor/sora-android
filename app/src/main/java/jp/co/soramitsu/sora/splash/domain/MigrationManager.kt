package jp.co.soramitsu.sora.splash.domain

import androidx.room.withTransaction
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletMigrationIntegrity
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Verifies legacy wallet material before the new multi-network records become active.
 *
 * The legacy `accounts` table and encrypted credential keys remain untouched. A failed run can be
 * retried after process restart and never falls back to creating or reconstructing an account.
 */
@Singleton
class MigrationSr25519Crypto @Inject constructor() {

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

@Singleton
class MigrationManager @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val sora2AddressCodec: Sora2AddressCodec,
    private val database: AppDatabase,
    private val migrationSr25519Crypto: MigrationSr25519Crypto,
) {
    private companion object {
        const val DERIVATION_VERSION = 1
        const val SIGNING_PARITY_CHALLENGE = "sora-wallet-migration-signing-parity-v1"
        const val MAX_WALLET_ID_LENGTH = 256
        val INTEGRITY_HASH = Regex("^[0-9a-f]{64}$")
    }

    private val migrationMutex = Mutex()

    suspend fun start(): Boolean = migrationMutex.withLock {
        userRepository.withWalletMigrationLocked { snapshot ->
            startLocked(
                registrationFinished =
                    snapshot.onboardingState == OnboardingState.REGISTRATION_FINISHED,
                selectedAccount = snapshot.selectedAccount,
            )
        }
    }

    private suspend fun startLocked(
        registrationFinished: Boolean,
        selectedAccount: SoraAccount?,
    ): Boolean {
        val legacyAccounts = database.accountDao().getAccounts()
        if (!registrationFinished && legacyAccounts.isEmpty()) {
            return true
        }

        val startedAt = System.currentTimeMillis()
        val legacyWalletIds = legacyAccounts.map { it.substrateAddress }
        if (legacyAccounts.isEmpty()) {
            recordFailure(
                startedAt = startedAt,
                legacyAccountCount = 0,
                selectedWalletId = "",
                integrityHash = "",
                code = "REGISTERED_WITHOUT_ACCOUNTS",
                walletIds = emptyList(),
            )
            return false
        }

        if (selectedAccount == null) {
            recordFailure(
                startedAt,
                legacyAccounts.size,
                "",
                integrityHash(legacyAccounts.map { it.substrateAddress to it.accountName }, ""),
                "SELECTED_ACCOUNT_MISSING",
                legacyWalletIds,
            )
            return false
        }
        if (legacyAccounts.none { it.substrateAddress == selectedAccount.substrateAddress }) {
            recordFailure(
                startedAt,
                legacyAccounts.size,
                "",
                integrityHash(legacyAccounts.map { it.substrateAddress to it.accountName }, ""),
                "SELECTED_ACCOUNT_MISMATCH",
                legacyWalletIds,
            )
            return false
        }

        val integrityHash = integrityHash(
            legacyAccounts.map { it.substrateAddress to it.accountName },
            selectedAccount.substrateAddress,
        )
        val existingJournal = database.walletIdentityDao()
            .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        val verifiedReceipt = existingJournal?.takeIf(::isStructurallyValidVerifiedReceipt)
        if (existingJournal?.state == "VERIFIED" && verifiedReceipt == null) {
            // A VERIFIED label is not authorization to normalize or replace an inconsistent
            // receipt. Preserve its exact CAS identity, close the capability gate, and require
            // recovery before any secret is inspected or copy-on-write namespace is touched.
            recordFailure(
                startedAt = existingJournal.startedAt,
                legacyAccountCount = existingJournal.legacyAccountCount,
                selectedWalletId = existingJournal.selectedWalletId,
                integrityHash = existingJournal.integrityHash,
                code = "VERIFIED_RECEIPT_INVALID",
                walletIds = legacyWalletIds,
            )
            return false
        }
        var migrationStartedAt = startedAt
        var authorizedRetry = false
        if (existingJournal != null && existingJournal.state != "VERIFIED") {
            // Merely starting the application is never authorization to retry secret inspection
            // or mutate the copy-on-write namespace. The recovery UI must first perform the raw,
            // compare-and-set journal transition.
            if (!WalletRecoveryCapabilityGate.isMigrationRetryAuthorized()) {
                return false
            }
            authorizedRetry =
                existingJournal.state == "VERIFYING" &&
                    existingJournal.legacyAccountCount == legacyAccounts.size &&
                    existingJournal.verifiedAccountCount == 0 &&
                    existingJournal.selectedWalletId == selectedAccount.substrateAddress &&
                    existingJournal.integrityHash == integrityHash &&
                    existingJournal.failureCode == null &&
                    existingJournal.completedAt == null &&
                    existingJournal.startedAt > 0L
            if (!authorizedRetry) {
                recordFailure(
                    startedAt =
                        existingJournal.startedAt.takeIf { it > 0L } ?: startedAt,
                    legacyAccountCount = legacyAccounts.size,
                    selectedWalletId = selectedAccount.substrateAddress,
                    integrityHash = integrityHash,
                    code = "MIGRATION_RETRY_AUTHORIZATION_MISMATCH",
                    walletIds = legacyWalletIds,
                )
                return false
            }
            migrationStartedAt = existingJournal.startedAt
        }
        if (verifiedReceipt != null) {
            val receiptMatchesLiveSnapshot =
                verifiedReceipt.legacyAccountCount == legacyAccounts.size &&
                    verifiedReceipt.verifiedAccountCount == legacyAccounts.size &&
                    verifiedReceipt.selectedWalletId ==
                    selectedAccount.substrateAddress &&
                    verifiedReceipt.integrityHash == integrityHash
            return try {
                val preparedWallets = legacyAccounts.map { prepareWallet(it) }
                if (receiptMatchesLiveSnapshot) {
                    verifyPreparedModel(preparedWallets)
                } else {
                    refreshVerifiedReceipt(
                        receipt = verifiedReceipt,
                        legacyAccounts = legacyAccounts,
                        preparedWallets = preparedWallets,
                        selectedWalletId = selectedAccount.substrateAddress,
                        currentIntegrityHash = integrityHash,
                    )
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (receiptMatchesLiveSnapshot) {
                    recordFailure(
                        verifiedReceipt.startedAt,
                        legacyAccounts.size,
                        selectedAccount.substrateAddress,
                        integrityHash,
                        "ACTIVATED_MODEL_MISMATCH",
                        legacyWalletIds,
                    )
                } else {
                    recordActivatedModelFailureAfterSnapshotChange(
                        receipt = verifiedReceipt,
                        currentAccountCount = legacyAccounts.size,
                        currentSelectedWalletId =
                            selectedAccount.substrateAddress,
                        currentIntegrityHash = integrityHash,
                        walletIds = legacyWalletIds,
                    )
                }
                false
            }
        }
        if (!authorizedRetry) {
            try {
                database.walletIdentityDao().upsertMigrationJournal(
                    WalletMigrationJournalLocal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        state = "VERIFYING",
                        legacyAccountCount = legacyAccounts.size,
                        verifiedAccountCount = 0,
                        selectedWalletId = selectedAccount.substrateAddress,
                        integrityHash = integrityHash,
                        failureCode = null,
                        startedAt = migrationStartedAt,
                        completedAt = null,
                    )
                )
            } catch (error: CancellationException) {
                // A cancelled Room write can have committed before cancellation was observed.
                // Close the live-process capability gate and preserve cancellation unchanged.
                WalletUpgradeBackup.noteMigrationRecoveryRequired()
                throw error
            } catch (error: Throwable) {
                WalletUpgradeBackup.noteMigrationRecoveryRequired()
                throw error
            }
        }

        val preparedWallets = try {
            legacyAccounts.map { prepareWallet(it) }
        } catch (error: CancellationException) {
            // VERIFYING is already durable (or was explicitly authorized). Never leave the
            // current process able to sign or mutate wallets after an interrupted verification.
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
            throw error
        } catch (error: Throwable) {
            recordFailure(
                migrationStartedAt,
                legacyAccounts.size,
                selectedAccount.substrateAddress,
                integrityHash,
                error.safeFailureCode(),
                legacyWalletIds,
            )
            return false
        }

        if (preparedWallets.size != legacyAccounts.size) {
            recordFailure(
                migrationStartedAt,
                legacyAccounts.size,
                selectedAccount.substrateAddress,
                integrityHash,
                "ACCOUNT_COUNT_MISMATCH",
                legacyWalletIds,
            )
            return false
        }

        try {
            database.withTransaction {
                requireActiveSecretSourceContinuity(preparedWallets)
                preparedWallets.forEach { prepared ->
                    database.walletIdentityDao().upsertWallet(prepared.identity)
                    database.walletIdentityDao().upsertNetworkAccounts(prepared.networkAccounts)
                }
                verifyPreparedModel(preparedWallets)
                database.walletIdentityDao().upsertMigrationJournal(
                    WalletMigrationJournalLocal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        state = "VERIFYING",
                        legacyAccountCount = legacyAccounts.size,
                        verifiedAccountCount = preparedWallets.size,
                        selectedWalletId = selectedAccount.substrateAddress,
                        integrityHash = integrityHash,
                        failureCode = null,
                        startedAt = migrationStartedAt,
                        completedAt = null,
                    )
                )
            }

            val finalSelectedWalletId = selectedAccount.substrateAddress
            database.withTransaction {
                verifyPreparedModel(preparedWallets)
                val finalAccounts = database.accountDao().getAccounts()
                val finalHash = integrityHash(
                    finalAccounts.map { it.substrateAddress to it.accountName },
                    finalSelectedWalletId,
                )
                if (
                    finalAccounts.size != legacyAccounts.size ||
                    finalHash != integrityHash
                ) {
                    throw MigrationVerificationException("POST_MIGRATION_MISMATCH")
                }
                check(
                    database.walletIdentityDao().activateMigrationJournal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        legacyAccountCount = legacyAccounts.size,
                        verifiedAccountCount = preparedWallets.size,
                        selectedWalletId = finalSelectedWalletId,
                        integrityHash = integrityHash,
                        startedAt = migrationStartedAt,
                        completedAt = System.currentTimeMillis(),
                    ) == 1
                ) { "MIGRATION_JOURNAL_ACTIVATION_MISMATCH" }
            }
        } catch (error: CancellationException) {
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
            throw error
        } catch (error: Throwable) {
            recordFailure(
                migrationStartedAt,
                legacyAccounts.size,
                selectedAccount.substrateAddress,
                integrityHash,
                error.safeFailureCode(),
                legacyWalletIds,
            )
            return false
        }

        WalletUpgradeBackup.noteMigrationVerified()
        return true
    }

    private suspend fun requireActiveSecretSourceContinuity(
        preparedWallets: List<PreparedWallet>,
    ) {
        val preparedByWalletId = preparedWallets.associateBy {
            it.identity.walletId
        }
        database.walletIdentityDao().getWallets()
            .filter { it.migrationState == "VERIFIED" }
            .forEach { active ->
                if (
                    preparedByWalletId[active.walletId]
                        ?.identity
                        ?.secretSource != active.secretSource
                ) {
                    throw MigrationVerificationException(
                        "ACTIVE_SECRET_SOURCE_MISMATCH"
                    )
                }
            }
    }

    private fun isStructurallyValidVerifiedReceipt(
        receipt: WalletMigrationJournalLocal,
    ): Boolean =
        receipt.migrationId == WalletMigrationIds.NETWORK_ACCOUNTS_V1 &&
            receipt.state == "VERIFIED" &&
            receipt.legacyAccountCount > 0 &&
            receipt.verifiedAccountCount == receipt.legacyAccountCount &&
            receipt.selectedWalletId.isNotBlank() &&
            receipt.selectedWalletId.length <= MAX_WALLET_ID_LENGTH &&
            INTEGRITY_HASH.matches(receipt.integrityHash) &&
            receipt.failureCode == null &&
            receipt.startedAt > 0L &&
            receipt.completedAt?.let { it >= receipt.startedAt } == true

    /**
     * A VERIFIED receipt is the last fully verified wallet checkpoint, not a requirement that the
     * live inventory never change. Re-verify every current secret/address/signing vector before
     * advancing it after a legitimate lifecycle change. The exact old receipt and current model
     * are checked in one transaction, so corruption or a concurrent change remains
     * recovery-required.
     */
    private suspend fun refreshVerifiedReceipt(
        receipt: WalletMigrationJournalLocal,
        legacyAccounts: List<jp.co.soramitsu.core_db.model.SoraAccountLocal>,
        preparedWallets: List<PreparedWallet>,
        selectedWalletId: String,
        currentIntegrityHash: String,
    ) {
        val receiptCompletedAt = checkNotNull(receipt.completedAt) {
            "VERIFIED_RECEIPT_COMPLETION_MISSING"
        }
        database.withTransaction {
            val finalAccounts = database.accountDao().getAccounts()
            check(
                finalAccounts.size == legacyAccounts.size &&
                    integrityHash(
                        finalAccounts.map { it.substrateAddress to it.accountName },
                        selectedWalletId,
                    ) == currentIntegrityHash
            ) { "POST_ACTIVATION_WALLET_SNAPSHOT_CHANGED" }
            verifyPreparedModel(preparedWallets)
            check(
                database.walletIdentityDao().refreshVerifiedMigrationJournal(
                    migrationId = receipt.migrationId,
                    receiptAccountCount = receipt.legacyAccountCount,
                    receiptVerifiedAccountCount = receipt.verifiedAccountCount,
                    receiptSelectedWalletId = receipt.selectedWalletId,
                    receiptIntegrityHash = receipt.integrityHash,
                    receiptStartedAt = receipt.startedAt,
                    receiptCompletedAt = receiptCompletedAt,
                    currentAccountCount = finalAccounts.size,
                    currentSelectedWalletId = selectedWalletId,
                    currentIntegrityHash = currentIntegrityHash,
                    refreshedAt = maxOf(
                        System.currentTimeMillis(),
                        receipt.startedAt,
                        receiptCompletedAt,
                    ),
                ) == 1
            ) { "VERIFIED_RECEIPT_REFRESH_CAS_MISMATCH" }
        }
    }

    private suspend fun recordActivatedModelFailureAfterSnapshotChange(
        receipt: WalletMigrationJournalLocal,
        currentAccountCount: Int,
        currentSelectedWalletId: String,
        currentIntegrityHash: String,
        walletIds: List<String>,
    ) {
        try {
            val receiptCompletedAt = checkNotNull(receipt.completedAt) {
                "VERIFIED_RECEIPT_COMPLETION_MISSING"
            }
            val completedAt = maxOf(
                System.currentTimeMillis(),
                receipt.startedAt,
                receiptCompletedAt,
            )
            check(
                database.walletIdentityDao().markActivatedMigrationRecoveryRequired(
                    migrationId = receipt.migrationId,
                    legacyAccountCount = receipt.legacyAccountCount,
                    receiptVerifiedAccountCount = receipt.verifiedAccountCount,
                    receiptSelectedWalletId = receipt.selectedWalletId,
                    receiptIntegrityHash = receipt.integrityHash,
                    startedAt = receipt.startedAt,
                    receiptCompletedAt = receiptCompletedAt,
                    currentAccountCount = currentAccountCount,
                    currentSelectedWalletId = currentSelectedWalletId,
                    currentIntegrityHash = currentIntegrityHash,
                    failureCode = "ACTIVATED_MODEL_MISMATCH",
                    completedAt = completedAt,
                ) == 1
            ) { "MIGRATION_FAILURE_CAS_MISMATCH" }
            if (walletIds.isNotEmpty()) {
                try {
                    database.walletIdentityDao()
                        .markWalletsRecoveryRequired(walletIds)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // The durable journal above is the activation gate.
                }
            }
        } finally {
            // A failed compare-and-set is itself an integrity failure. Keep the process gated even
            // when low storage or cancellation prevents the durable recovery write from finishing.
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
        }
    }

    suspend fun failureCode(): String? =
        userRepository.walletDeletionFailureCode()
            ?: database.walletIdentityDao()
                .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
                ?.failureCode

    private suspend fun recordFailure(
        startedAt: Long,
        legacyAccountCount: Int,
        selectedWalletId: String,
        integrityHash: String,
        code: String,
        walletIds: List<String>,
    ) {
        try {
            // Commit the durable global recovery gate first. Even if a later per-wallet marker
            // cannot be written, the legacy namespace remains authoritative.
            check(
                database.walletIdentityDao().recordMigrationFailure(
                    WalletMigrationJournalLocal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        state = "RECOVERY_REQUIRED",
                        legacyAccountCount = legacyAccountCount,
                        verifiedAccountCount = 0,
                        selectedWalletId = selectedWalletId,
                        integrityHash = integrityHash,
                        failureCode = code,
                        startedAt = startedAt,
                        completedAt = maxOf(System.currentTimeMillis(), startedAt),
                    )
                )
            ) { "MIGRATION_FAILURE_CAS_MISMATCH" }
            if (walletIds.isNotEmpty()) {
                try {
                    database.walletIdentityDao().markWalletsRecoveryRequired(walletIds)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // The durable journal above is the activation gate. Preserve
                    // it and continue to the recovery-safe route.
                }
            }
        } finally {
            // The in-memory gate must close even when the journal write itself fails or a
            // committed suspend call reports cancellation. A restart will re-inspect durability.
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
        }
    }

    private fun integrityHash(accounts: List<Pair<String, String>>, selected: String): String =
        WalletMigrationIntegrity.snapshotHashPairs(accounts, selected)

    private suspend fun verifyPreparedModel(preparedWallets: List<PreparedWallet>) {
        val identities = database.walletIdentityDao().getWallets()
        val expectedIdentities = preparedWallets.map(PreparedWallet::identity)
        if (
            identities.size != expectedIdentities.size ||
            identities.associateBy(WalletIdentityLocal::walletId) !=
            expectedIdentities.associateBy(WalletIdentityLocal::walletId)
        ) {
            throw MigrationVerificationException("STAGED_IDENTITY_MISMATCH")
        }
        preparedWallets.forEach { prepared ->
            val actual = database.walletIdentityDao()
                .getNetworkAccounts(prepared.identity.walletId)
                .sortedBy(NetworkAccountLocal::networkId)
            val expected = prepared.networkAccounts.sortedBy(NetworkAccountLocal::networkId)
            if (actual != expected) {
                throw MigrationVerificationException("STAGED_NETWORK_ACCOUNT_MISMATCH")
            }
        }
    }

    private suspend fun prepareWallet(
        legacy: jp.co.soramitsu.core_db.model.SoraAccountLocal,
    ): PreparedWallet {
        val account = SoraAccount(
            substrateAddress = legacy.substrateAddress,
            accountName = legacy.accountName,
        )
        if (credentialsRepository.isExplicitWatchOnly(account)) {
            return prepareExplicitWatchOnlyWallet(legacy, account)
        }
        val keyPair = credentialsRepository.retrieveKeyPair(account)
        try {
            val rederivedAddress = sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey)
                ?: throw MigrationVerificationException("SORA_ADDRESS_DERIVATION")
            if (rederivedAddress != legacy.substrateAddress) {
                throw MigrationVerificationException("SORA_ADDRESS_MISMATCH")
            }

            val mnemonic = credentialsRepository.retrieveMnemonic(account)
            val storedSeed = credentialsRepository.retrieveStoredSeed(account)
            val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val secret = when {
                mnemonic.isBlank() && storedSeed.isBlank() ->
                    VerifiedSecret("LEGACY_SECRET", "")
                mnemonic.isBlank() -> {
                    if (!credentialsRepository.isRawSeedValid(storedSeed)) {
                        throw MigrationVerificationException("SECRET_SOURCE_MISSING")
                    }
                    VerifiedSecret("RAW_SEED", storedSeed)
                }
                else -> {
                    val mnemonicSeed = credentialsRepository
                        .convertRetainedSoraPassphraseToSeed(mnemonic)
                    if (
                        storedSeed.isNotBlank() &&
                        !storedSeed.removePrefix("0x").removePrefix("0X")
                            .equals(
                                mnemonicSeed.removePrefix("0x").removePrefix("0X"),
                                ignoreCase = true,
                            )
                    ) {
                        throw MigrationVerificationException("STORED_SEED_MISMATCH")
                    }
                    VerifiedSecret(
                        if (words.size == 12 || words.size == 24) {
                            "MNEMONIC"
                        } else {
                            "MNEMONIC_UNSUPPORTED"
                        },
                        mnemonicSeed,
                    )
                }
            }
            if (secret.source == "LEGACY_SECRET") {
                verifyLegacySecretKeyMaterial(keyPair)
            } else {
                verifySoraKeyMaterial(keyPair, secret.substrateSeed)
            }

            val networkAccounts = mutableListOf(
                NetworkAccountLocal(
                    walletId = legacy.substrateAddress,
                    networkId = "sora2",
                    publicKey = keyPair.publicKey.toHex(),
                    address = legacy.substrateAddress,
                    derivationPath = "",
                    derivationVersion = DERIVATION_VERSION,
                    enabled = true,
                )
            )
            if (secret.source == "MNEMONIC") {
                listOf(NexusNetworks.minamoto, NexusNetworks.taira).forEach { network ->
                    val derived = IrohaKeyDerivation.derive(mnemonic, network)
                    try {
                        networkAccounts += NetworkAccountLocal(
                            walletId = legacy.substrateAddress,
                            networkId = network.id.wireId,
                            publicKey = derived.publicKey.toHex(),
                            address = derived.address,
                            derivationPath = network.derivationPath,
                            derivationVersion = DERIVATION_VERSION,
                            enabled = network.enabledByDefault,
                        )
                    } finally {
                        derived.destroy()
                    }
                }
            }

            return PreparedWallet(
                identity = WalletIdentityLocal(
                    walletId = legacy.substrateAddress,
                    displayName = legacy.accountName,
                    secretSource = secret.source,
                    migrationState = "VERIFIED",
                    derivationVersion = DERIVATION_VERSION,
                ),
                networkAccounts = networkAccounts,
            )
        } finally {
            keyPair.privateKey.fill(0)
            keyPair.nonce.fill(0)
        }
    }

    private suspend fun prepareExplicitWatchOnlyWallet(
        legacy: jp.co.soramitsu.core_db.model.SoraAccountLocal,
        account: jp.co.soramitsu.common.account.SoraAccount,
    ): PreparedWallet {
        val signingKey = credentialsRepository.retrieveKeyPairOrNull(account)
        try {
            if (signingKey != null) {
                throw MigrationVerificationException("WATCH_ONLY_SIGNING_KEY_CONFLICT")
            }
        } finally {
            signingKey?.privateKey?.fill(0)
            signingKey?.nonce?.fill(0)
        }
        if (
            credentialsRepository.retrieveMnemonic(account).isNotBlank() ||
            credentialsRepository.retrieveStoredSeed(account).isNotBlank()
        ) {
            throw MigrationVerificationException("WATCH_ONLY_SECRET_CONFLICT")
        }

        val publicKey = sora2AddressCodec.soraPublicKeyOrNull(legacy.substrateAddress)
            ?: throw MigrationVerificationException("WATCH_ONLY_ADDRESS_INVALID")
        if (sora2AddressCodec.toSoraAddressOrNull(publicKey) != legacy.substrateAddress) {
            throw MigrationVerificationException("WATCH_ONLY_ADDRESS_MISMATCH")
        }

        return PreparedWallet(
            identity = WalletIdentityLocal(
                walletId = legacy.substrateAddress,
                displayName = legacy.accountName,
                secretSource = "WATCH_ONLY",
                migrationState = "VERIFIED",
                derivationVersion = DERIVATION_VERSION,
            ),
            networkAccounts = listOf(
                NetworkAccountLocal(
                    walletId = legacy.substrateAddress,
                    networkId = "sora2",
                    publicKey = publicKey.toHex(),
                    address = legacy.substrateAddress,
                    derivationPath = "",
                    derivationVersion = DERIVATION_VERSION,
                    enabled = true,
                )
            ),
        )
    }

    private fun verifySoraKeyMaterial(
        stored: Sr25519Keypair,
        substrateSeed: String,
    ) {
        val normalizedSeed = substrateSeed.removePrefix("0x").removePrefix("0X")
        val seed = normalizedSeed.fromHex()
        val regenerated = try {
            migrationSr25519Crypto.generateKeypair(seed)
                ?: throw MigrationVerificationException("SORA_KEY_TYPE_MISMATCH")
        } finally {
            seed.fill(0)
        }
        try {
            if (
                !MessageDigest.isEqual(stored.publicKey, regenerated.publicKey) ||
                !MessageDigest.isEqual(stored.privateKey, regenerated.privateKey) ||
                !MessageDigest.isEqual(stored.nonce, regenerated.nonce)
            ) {
                throw MigrationVerificationException("SORA_SIGNING_KEY_MISMATCH")
            }
            val challenge = SIGNING_PARITY_CHALLENGE.encodeToByteArray()
            try {
                val signatureMatches = try {
                    migrationSr25519Crypto.signAndVerify(
                        keypair = stored,
                        challenge = challenge,
                        expectedPublicKey = regenerated.publicKey,
                    )
                } catch (_: Throwable) {
                    throw MigrationVerificationException("SORA_SIGNING_VECTOR_FAILED")
                }
                if (!signatureMatches) {
                    throw MigrationVerificationException(
                        "SORA_SIGNING_VECTOR_MISMATCH"
                    )
                }
            } finally {
                challenge.fill(0)
            }
        } finally {
            regenerated.privateKey.fill(0)
            regenerated.nonce.fill(0)
        }
    }

    /**
     * Proves that a retained keypair-only wallet can still sign for its stored public key.
     *
     * There is deliberately no seed reconstruction here: release-era wallets may have retained
     * only the independently encrypted Sr25519 key fields. The caller has already bound the
     * public key to the unchanged SORA2 address; this fixed challenge binds the private half to
     * that public key without persisting or logging either the challenge response or key bytes.
     */
    private fun verifyLegacySecretKeyMaterial(stored: Sr25519Keypair) {
        val challenge = SIGNING_PARITY_CHALLENGE.encodeToByteArray()
        try {
            val signatureMatches = try {
                migrationSr25519Crypto.signAndVerify(
                    keypair = stored,
                    challenge = challenge,
                    expectedPublicKey = stored.publicKey,
                )
            } catch (_: Throwable) {
                throw MigrationVerificationException("SORA_SIGNING_VECTOR_FAILED")
            }
            if (!signatureMatches) {
                throw MigrationVerificationException("SORA_SIGNING_VECTOR_MISMATCH")
            }
        } finally {
            challenge.fill(0)
        }
    }

    private fun Throwable.safeFailureCode(): String = when (this) {
        is MigrationVerificationException -> code
        is KeyMaterialUnavailableException -> "KEYSTORE_UNAVAILABLE"
        is WalletDecryptionException -> "SECRET_DECRYPTION_FAILED"
        is IllegalStateException -> "SECRET_MISSING"
        else -> "VERIFICATION_FAILED"
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private data class PreparedWallet(
        val identity: WalletIdentityLocal,
        val networkAccounts: List<NetworkAccountLocal>,
    )

    private data class VerifiedSecret(
        val source: String,
        val substrateSeed: String,
    )

    private class MigrationVerificationException(val code: String) : IllegalStateException(code)
}
