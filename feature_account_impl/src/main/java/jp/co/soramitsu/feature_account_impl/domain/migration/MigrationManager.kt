package jp.co.soramitsu.feature_account_impl.domain.migration

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.WalletStorageFailures
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletMigrationIntegrity
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.core_db.WalletSelectionPolicy
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
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

/** Result of one wallet verification run. Only [Recovery] was written to the journal. */
sealed interface MigrationOutcome {
    val code: String?

    /** Every account was verified or classified; accounts whose keys are unavailable are read-only. */
    data object Verified : MigrationOutcome {
        override val code: String? = null
    }

    /** A transient failure (busy Keystore, full disk, I/O). Nothing was written; retry later. */
    data class Transient(override val code: String) : MigrationOutcome

    /** The Keystore cannot unlock any stored key on this device. Nothing was written. */
    data class KeysUnavailable(override val code: String) : MigrationOutcome

    /** An interrupted or failed verification is waiting for an authorized retry. */
    data class AwaitingRetry(override val code: String) : MigrationOutcome

    /** A deterministic integrity failure that was recorded in the migration journal. */
    data class Recovery(override val code: String) : MigrationOutcome
}

/**
 * Verifies every wallet's stored keys before the multi-network records become active.
 *
 * Account rows and encrypted credentials are never changed here: only the derived
 * `walletIdentities`, `networkAccounts` and the journal are written (copy-on-write).
 *
 * - All keys are read and checked before anything is written, so a transient failure (busy
 *   Keystore, full disk) leaves no trace and the next start simply runs again.
 * - Problems that belong to one account (missing, incomplete or unreadable keys, an address that
 *   does not match) make only that account unavailable (read-only); every other account verifies.
 *   A later run that finds valid keys makes the account available again.
 * - Only genuine integrity failures of the verification itself are journaled.
 */
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
        const val UNAVAILABLE_STATE = "UNAVAILABLE"
        const val UNAVAILABLE_SOURCE = "SECRET_UNAVAILABLE"
        val INTEGRITY_HASH = Regex("^[0-9a-f]{64}$")
    }

    private val migrationMutex = Mutex()

    @Volatile
    var lastOutcome: MigrationOutcome? = null
        private set

    /** Returns true when the wallet is usable (possibly with some accounts read-only). */
    suspend fun start(): Boolean = startWithOutcome() == MigrationOutcome.Verified

    suspend fun startWithOutcome(): MigrationOutcome = migrationMutex.withLock {
        val outcome = userRepository.withWalletMigrationLocked { snapshot ->
            startLocked(
                registrationFinished =
                    snapshot.onboardingState == OnboardingState.REGISTRATION_FINISHED,
                selectedAccount = snapshot.selectedAccount,
            )
        }
        lastOutcome = outcome
        outcome
    }

    /** The code of the last unsuccessful run in this process, if any. */
    fun lastFailureCode(): String? = lastOutcome?.code

    @Suppress("UNUSED_PARAMETER")
    private suspend fun startLocked(
        registrationFinished: Boolean,
        selectedAccount: SoraAccount?,
    ): MigrationOutcome {
        val legacyAccounts = database.accountDao().getAccounts()
            .filter { it.substrateAddress.isNotBlank() && it.substrateAddress.length <= MAX_WALLET_ID_LENGTH }
        val existingJournal = database.walletIdentityDao()
            .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        if (legacyAccounts.isEmpty()) {
            // Nothing to verify: a new install, every account removed, or the database was lost
            // while keys were kept (those keys are set aside and offered for restore). A journal
            // left by an earlier build protects no account and would only block later accounts.
            if (existingJournal != null) {
                database.walletIdentityDao().deleteMigrationJournal()
            }
            WalletUpgradeBackup.noteMigrationVerified()
            return MigrationOutcome.Verified
        }

        val startedAt = System.currentTimeMillis()
        val legacyWalletIds = legacyAccounts.map { it.substrateAddress }
        val selectedWalletId = selectedAccount?.substrateAddress
            ?.takeIf { it in legacyWalletIds }
            ?: requireNotNull(WalletSelectionPolicy.fallback(legacyWalletIds, legacyOwner = null))
        val integrityHash = integrityHash(
            legacyAccounts.map { it.substrateAddress to it.accountName },
            selectedWalletId,
        )
        val verifiedReceipt = existingJournal?.takeIf(::isStructurallyValidVerifiedReceipt)
        if (existingJournal?.state == "VERIFIED" && verifiedReceipt == null) {
            // A VERIFIED label is not authorization to normalize an inconsistent receipt. Record
            // it; the next start retries the verification automatically from the account rows.
            return recordFailure(
                startedAt = existingJournal.startedAt,
                legacyAccountCount = existingJournal.legacyAccountCount,
                selectedWalletId = existingJournal.selectedWalletId,
                integrityHash = existingJournal.integrityHash,
                code = "VERIFIED_RECEIPT_INVALID",
                walletIds = legacyWalletIds,
            )
        }
        var migrationStartedAt = startedAt
        var authorizedRetry = false
        if (existingJournal != null && existingJournal.state != "VERIFIED") {
            // Starting the app alone never retries an interrupted run: the upgrade gate first
            // performs the compare-and-set journal transition (automatically, at most three times
            // in a row, or when the person taps Try again).
            if (!WalletRecoveryCapabilityGate.isMigrationRetryAuthorized()) {
                return MigrationOutcome.AwaitingRetry("WALLET_MIGRATION_RECOVERY_REQUIRED")
            }
            authorizedRetry =
                existingJournal.state == "VERIFYING" &&
                    existingJournal.verifiedAccountCount == 0 &&
                    existingJournal.failureCode == null &&
                    existingJournal.completedAt == null &&
                    existingJournal.startedAt > 0L
            if (!authorizedRetry) {
                return recordFailure(
                    startedAt = existingJournal.startedAt.takeIf { it > 0L } ?: startedAt,
                    legacyAccountCount = legacyAccounts.size,
                    selectedWalletId = selectedWalletId,
                    integrityHash = integrityHash,
                    code = "MIGRATION_RETRY_AUTHORIZATION_MISMATCH",
                    walletIds = legacyWalletIds,
                )
            }
            migrationStartedAt = existingJournal.startedAt
        }

        // 1. Read and verify every wallet. Pure reads and crypto: nothing is written yet.
        val preparedWallets = mutableListOf<PreparedWallet>()
        for (legacy in legacyAccounts) {
            when (val result = prepareWalletClassified(legacy)) {
                is Preparation.Abort -> return abortWithoutWrites(result)
                is Preparation.Ready -> preparedWallets += result.wallet
            }
        }
        val classifiedWallets = applySecretSourceContinuity(preparedWallets)

        // 2. Already verified: re-stage only what changed and advance the checkpoint.
        if (verifiedReceipt != null) {
            val receiptMatchesLiveSnapshot =
                verifiedReceipt.legacyAccountCount == legacyAccounts.size &&
                    verifiedReceipt.verifiedAccountCount == legacyAccounts.size &&
                    verifiedReceipt.selectedWalletId == selectedWalletId &&
                    verifiedReceipt.integrityHash == integrityHash
            return try {
                if (!receiptMatchesLiveSnapshot || !storedModelMatches(classifiedWallets)) {
                    database.withTransaction {
                        stagePreparedModel(classifiedWallets)
                        verifyPreparedModel(classifiedWallets)
                        if (!receiptMatchesLiveSnapshot) {
                            refreshVerifiedReceiptLocked(
                                receipt = verifiedReceipt,
                                legacyAccounts = legacyAccounts,
                                selectedWalletId = selectedWalletId,
                                currentIntegrityHash = integrityHash,
                            )
                        }
                    }
                }
                WalletUpgradeBackup.noteMigrationVerified()
                MigrationOutcome.Verified
            } catch (error: CancellationException) {
                WalletUpgradeBackup.noteMigrationRecoveryRequired()
                throw error
            } catch (error: Throwable) {
                if (WalletStorageFailures.isTransient(error)) {
                    abortWithoutWrites(Preparation.Abort.from(error))
                } else {
                    recordActivatedModelFailureAfterSnapshotChange(
                        receipt = verifiedReceipt,
                        currentAccountCount = legacyAccounts.size,
                        currentSelectedWalletId = selectedWalletId,
                        currentIntegrityHash = integrityHash,
                        walletIds = legacyWalletIds,
                        code = error.safeFailureCode(),
                    )
                }
            }
        }

        // 3. First verification (or an authorized retry): journal, stage, then activate.
        try {
            if (!authorizedRetry) {
                database.walletIdentityDao().upsertMigrationJournal(
                    WalletMigrationJournalLocal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        state = "VERIFYING",
                        legacyAccountCount = legacyAccounts.size,
                        verifiedAccountCount = 0,
                        selectedWalletId = selectedWalletId,
                        integrityHash = integrityHash,
                        failureCode = null,
                        startedAt = migrationStartedAt,
                        completedAt = null,
                    )
                )
            }
            database.withTransaction {
                stagePreparedModel(classifiedWallets)
                verifyPreparedModel(classifiedWallets)
                database.walletIdentityDao().upsertMigrationJournal(
                    WalletMigrationJournalLocal(
                        migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
                        state = "VERIFYING",
                        legacyAccountCount = legacyAccounts.size,
                        verifiedAccountCount = classifiedWallets.size,
                        selectedWalletId = selectedWalletId,
                        integrityHash = integrityHash,
                        failureCode = null,
                        startedAt = migrationStartedAt,
                        completedAt = null,
                    )
                )
            }
            database.withTransaction {
                verifyPreparedModel(classifiedWallets)
                val finalAccounts = database.accountDao().getAccounts()
                    .filter { it.substrateAddress.isNotBlank() && it.substrateAddress.length <= MAX_WALLET_ID_LENGTH }
                val finalHash = integrityHash(
                    finalAccounts.map { it.substrateAddress to it.accountName },
                    selectedWalletId,
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
                        verifiedAccountCount = classifiedWallets.size,
                        selectedWalletId = selectedWalletId,
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
            if (WalletStorageFailures.isTransient(error)) {
                // A VERIFYING journal may remain; it is retried automatically on the next start.
                return abortWithoutWrites(Preparation.Abort.from(error))
            }
            return recordFailure(
                startedAt = migrationStartedAt,
                legacyAccountCount = legacyAccounts.size,
                selectedWalletId = selectedWalletId,
                integrityHash = integrityHash,
                code = error.safeFailureCode(),
                walletIds = legacyWalletIds,
            )
        }

        WalletUpgradeBackup.noteMigrationVerified()
        return MigrationOutcome.Verified
    }

    private fun abortWithoutWrites(abort: Preparation.Abort): MigrationOutcome {
        WalletUpgradeBackup.noteTransientVerificationFailure(abort.code)
        return if (abort.keysUnavailable) {
            MigrationOutcome.KeysUnavailable(abort.code)
        } else {
            MigrationOutcome.Transient(abort.code)
        }
    }

    /**
     * An account whose stored keys were verified earlier under one secret source and now present
     * another is not silently re-labelled: it becomes read-only until the keys verify under the
     * same source again (or the person restores it).
     */
    private suspend fun applySecretSourceContinuity(
        prepared: List<PreparedWallet>,
    ): List<PreparedWallet> {
        val active = database.walletIdentityDao().getWallets()
            .associateBy(WalletIdentityLocal::walletId)
        return prepared.map { wallet ->
            val stored = active[wallet.identity.walletId]
            if (
                stored?.migrationState == "VERIFIED" &&
                wallet.identity.migrationState == "VERIFIED" &&
                stored.secretSource != wallet.identity.secretSource
            ) {
                unavailableWallet(
                    walletId = wallet.identity.walletId,
                    displayName = wallet.identity.displayName,
                )
            } else {
                wallet
            }
        }
    }

    private suspend fun prepareWalletClassified(legacy: SoraAccountLocal): Preparation =
        try {
            Preparation.Ready(prepareWallet(legacy))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (
                WalletStorageFailures.isKeyMaterialUnavailable(error) ||
                WalletStorageFailures.isTransient(error)
            ) {
                Preparation.Abort.from(error)
            } else {
                // This account's keys are missing, incomplete, unreadable or do not match its
                // address. Only this account becomes read-only.
                Preparation.Ready(
                    unavailableWallet(
                        walletId = legacy.substrateAddress,
                        displayName = legacy.accountName,
                    )
                )
            }
        }

    private fun unavailableWallet(walletId: String, displayName: String): PreparedWallet =
        PreparedWallet(
            identity = WalletIdentityLocal(
                walletId = walletId,
                displayName = displayName,
                secretSource = UNAVAILABLE_SOURCE,
                migrationState = UNAVAILABLE_STATE,
                derivationVersion = DERIVATION_VERSION,
            ),
            networkAccounts = listOf(
                NetworkAccountLocal(
                    walletId = walletId,
                    networkId = "sora2",
                    publicKey = sora2AddressCodec.soraPublicKeyOrNull(walletId)?.toHex().orEmpty(),
                    address = walletId,
                    derivationPath = "",
                    derivationVersion = DERIVATION_VERSION,
                    enabled = true,
                )
            ),
        )

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
     * Advances a VERIFIED checkpoint after a legitimate lifecycle change (account creation,
     * rename, selection or removal). Runs inside the caller's transaction after the current
     * model has been staged and verified.
     */
    private suspend fun refreshVerifiedReceiptLocked(
        receipt: WalletMigrationJournalLocal,
        legacyAccounts: List<SoraAccountLocal>,
        selectedWalletId: String,
        currentIntegrityHash: String,
    ) {
        val receiptCompletedAt = checkNotNull(receipt.completedAt) {
            "VERIFIED_RECEIPT_COMPLETION_MISSING"
        }
        val finalAccounts = database.accountDao().getAccounts()
            .filter { it.substrateAddress.isNotBlank() && it.substrateAddress.length <= MAX_WALLET_ID_LENGTH }
        check(
            finalAccounts.size == legacyAccounts.size &&
                integrityHash(
                    finalAccounts.map { it.substrateAddress to it.accountName },
                    selectedWalletId,
                ) == currentIntegrityHash
        ) { "POST_ACTIVATION_WALLET_SNAPSHOT_CHANGED" }
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

    private suspend fun recordActivatedModelFailureAfterSnapshotChange(
        receipt: WalletMigrationJournalLocal,
        currentAccountCount: Int,
        currentSelectedWalletId: String,
        currentIntegrityHash: String,
        walletIds: List<String>,
        code: String,
    ): MigrationOutcome {
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The process gate below still closes; the next start inspects the journal again.
        } finally {
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
        }
        return MigrationOutcome.Recovery(code)
    }

    suspend fun failureCode(): String? =
        userRepository.walletDeletionFailureCode()
            ?: lastOutcome?.code
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
    ): MigrationOutcome {
        try {
            // Commit the durable recovery gate first. Even if a later per-wallet marker cannot be
            // written, the account rows and encrypted keys remain authoritative.
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
                    // The durable journal above is the activation gate.
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A failed journal write (for example a full disk) is not remembered either; the
            // process stays read-only and the next start verifies again.
        } finally {
            // The in-memory gate must close even when the journal write itself fails or a
            // committed suspend call reports cancellation. A restart will re-inspect durability.
            WalletUpgradeBackup.noteMigrationRecoveryRequired()
        }
        return MigrationOutcome.Recovery(code)
    }

    private fun integrityHash(accounts: List<Pair<String, String>>, selected: String): String =
        WalletMigrationIntegrity.snapshotHashPairs(accounts, selected)

    /** Writes the prepared identity and network accounts of every wallet whose stored copy differs. */
    private suspend fun stagePreparedModel(preparedWallets: List<PreparedWallet>) {
        val identities = database.walletIdentityDao().getWallets()
            .associateBy(WalletIdentityLocal::walletId)
        preparedWallets.forEach { prepared ->
            if (identities[prepared.identity.walletId] != prepared.identity) {
                database.walletIdentityDao().upsertWallet(prepared.identity)
            }
            val actual = database.walletIdentityDao()
                .getNetworkAccounts(prepared.identity.walletId)
                .associateBy(NetworkAccountLocal::networkId)
            val changed = prepared.networkAccounts.filter { actual[it.networkId] != it }
            if (changed.isNotEmpty()) {
                database.walletIdentityDao().upsertNetworkAccounts(changed)
            }
        }
    }

    private suspend fun storedModelMatches(preparedWallets: List<PreparedWallet>): Boolean =
        try {
            verifyPreparedModel(preparedWallets)
            true
        } catch (_: MigrationVerificationException) {
            false
        }

    /**
     * Every account row must carry exactly its prepared identity and prepared network accounts.
     * Rows for networks that are no longer derived, and identities without an account row, are
     * left in place and ignored: they cannot sign without a verified identity and account row.
     */
    private suspend fun verifyPreparedModel(preparedWallets: List<PreparedWallet>) {
        val identities = database.walletIdentityDao().getWallets()
            .associateBy(WalletIdentityLocal::walletId)
        preparedWallets.forEach { prepared ->
            if (identities[prepared.identity.walletId] != prepared.identity) {
                throw MigrationVerificationException("STAGED_IDENTITY_MISMATCH")
            }
            val actual = database.walletIdentityDao()
                .getNetworkAccounts(prepared.identity.walletId)
                .associateBy(NetworkAccountLocal::networkId)
            if (prepared.networkAccounts.any { actual[it.networkId] != it }) {
                throw MigrationVerificationException("STAGED_NETWORK_ACCOUNT_MISMATCH")
            }
        }
    }

    private suspend fun prepareWallet(
        legacy: SoraAccountLocal,
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
                NexusNetworks.admitted.forEach { network ->
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
        legacy: SoraAccountLocal,
        account: SoraAccount,
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
        is KeyMaterialUnavailableException -> WalletRecoveryOptions.KEYSTORE_KEY_UNAVAILABLE
        is WalletDecryptionException -> "SECRET_DECRYPTION_FAILED"
        is IllegalStateException -> message?.takeIf { it.matches(Regex("^[A-Z0-9_]{1,64}$")) }
            ?: "SECRET_MISSING"
        else -> "VERIFICATION_FAILED"
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private sealed interface Preparation {
        class Ready(val wallet: PreparedWallet) : Preparation

        class Abort(val code: String, val keysUnavailable: Boolean) : Preparation {
            companion object {
                fun from(error: Throwable): Abort {
                    val keystore = generateSequence(error) { it.cause }
                        .take(8)
                        .filterIsInstance<KeyMaterialUnavailableException>()
                        .firstOrNull()
                    return when {
                        keystore != null && !keystore.transient -> Abort(
                            code = WalletRecoveryOptions.KEYSTORE_KEY_UNAVAILABLE,
                            keysUnavailable = true,
                        )
                        keystore != null -> Abort(
                            code = WalletRecoveryOptions.KEYSTORE_TRANSIENT,
                            keysUnavailable = false,
                        )
                        generateSequence(error) { it.cause }.take(8)
                            .any { it is SQLiteFullException } -> Abort(
                            code = WalletRecoveryOptions.STORAGE_FULL,
                            keysUnavailable = false,
                        )
                        generateSequence(error) { it.cause }.take(8)
                            .any { it is SQLiteDatabaseLockedException } -> Abort(
                            code = WalletRecoveryOptions.DATABASE_LOCKED,
                            keysUnavailable = false,
                        )
                        else -> Abort(
                            code = WalletRecoveryOptions.STORAGE_IO,
                            keysUnavailable = false,
                        )
                    }
                }
            }
        }
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
