package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusAssetDefinitionIdentity
import jp.co.soramitsu.common.nexus.NexusNetwork
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.common.nexus.NexusTransactionHash
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Shared in-process handoff lease. Recovery can observe whether the exact durable hash is still in
 * the mutation coordinator's one live Torii handoff without receiving a signer or submit client.
 */
@Singleton
class NexusSubmissionActivityRegistry @Inject constructor() {
    private val activeLocalIds = ConcurrentHashMap.newKeySet<String>()

    fun reserve(localId: String): Boolean = activeLocalIds.add(localId)

    fun release(localId: String) {
        activeLocalIds.remove(localId)
    }

    fun isActive(localId: String): Boolean = localId in activeLocalIds
}

/**
 * Status-only restart reconciliation.
 *
 * Its constructor exposes no quote, signing, signed-byte, or submission capability. The only
 * network dependency is [NexusToriiReadClient], and finality comes from an independently qualified
 * read-only reader bound to both wallet network ID and chain UUID.
 */
@Singleton
class NexusPendingReconciler @Inject constructor(
    private val database: AppDatabase,
    private val userRepository: UserRepository,
    private val torii: NexusToriiReadClient,
    private val finalityReader: NexusFinalityReader,
    private val submissionActivity: NexusSubmissionActivityRegistry,
) : NexusPendingRecovery {

    suspend fun reconcile(localId: String): NexusSendResult {
        var pending = checkNotNull(database.walletIdentityDao().getPendingTransaction(localId)) {
            "NEXUS_PENDING_TRANSACTION_MISSING"
        }
        check(!submissionActivity.isActive(localId)) { "NEXUS_SUBMISSION_HANDOFF_ACTIVE" }
        val networkId = checkNotNull(WalletNetworkId.fromWireId(pending.networkId))
        val network = requireNetwork(networkId)
        // A network label can survive a chain reset. Historical/unbound rows are immutable
        // recovery evidence and must never be interpreted against the currently configured Torii.
        check(WalletIdentityDao.hasCurrentChainIdentity(pending)) {
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
        }
        val hash = canonicalHash(checkNotNull(pending.transactionHash) {
            "NEXUS_AMBIGUOUS_WITHOUT_HASH"
        })
        check(
            IrohaAddressCodec.parse(
                pending.recipient,
                network.chainDiscriminant,
            ).address == pending.recipient
        ) { "NEXUS_PENDING_RECIPIENT_INVALID" }
        check(canonicalPositiveQuantity(pending.amount) == pending.amount) {
            "NEXUS_PENDING_AMOUNT_INVALID"
        }
        if (pending.state == "SIGNED") {
            // A durable signed record can outlive the process immediately before or during the
            // transport call. It is therefore status-only and ambiguous after restart.
            pending = update(localId, hash, "UNKNOWN", ambiguous = true)
        }
        check(NexusAssetDefinitionIdentity.hasCanonicalWireShape(pending.assetId)) {
            "NEXUS_PENDING_ASSET_INVALID"
        }
        val status = torii.transactionStatus(network, hash)
        check(canonicalHash(status.hash) == hash) { "NEXUS_STATUS_HASH_MISMATCH" }
        check(status.hasAuthoritativeGlobalResolution) {
            "NEXUS_STATUS_RESOLUTION_INVALID"
        }

        return when (status.status.kind.lowercase()) {
            "committed", "applied" -> reconcileCommitted(
                pending = pending,
                networkId = networkId,
                network = network,
                hash = hash,
                committedAt = checkNotNull(status.status.blockHeight) {
                    "NEXUS_COMMITTED_HEIGHT_MISSING"
                },
            )
            "rejected", "expired" -> {
                val rejected = update(localId, hash, "REJECTED", ambiguous = false)
                NexusSendResult(localId, hash, rejected.state)
            }
            "approved", "queued", "submitted", "pending", "validating" -> {
                val submitted = update(localId, hash, "SUBMITTED", ambiguous = false)
                NexusSendResult(localId, hash, submitted.state)
            }
            else -> {
                val unknown = update(localId, hash, "UNKNOWN", ambiguous = true)
                NexusSendResult(localId, hash, unknown.state)
            }
        }
    }

    private suspend fun reconcileCommitted(
        pending: PendingNetworkTransactionLocal,
        networkId: WalletNetworkId,
        network: NexusNetwork,
        hash: String,
        committedAt: Long,
    ): NexusSendResult {
        check(committedAt > 0L) { "NEXUS_COMMITTED_HEIGHT_INVALID" }
        check(finalityReader.isQualifiedFor(network)) {
            "NEXUS_FINALITY_READER_NOT_QUALIFIED"
        }
        val finalizedAt = finalityReader.finalizedCheckpoint(network).requireFor(network)
        check(finalizedAt >= committedAt) { "NEXUS_TRANSACTION_NOT_FINAL" }

        val committed = update(
            pending.localId,
            hash,
            "COMMITTED_PENDING_RECONCILIATION",
            ambiguous = false,
        )
        if (committed.state in TERMINAL_STATES) {
            return NexusSendResult(pending.localId, hash, committed.state)
        }
        val account = requireActiveAccount(
            walletId = pending.walletId,
            networkId = networkId,
            requireSelected = false,
        )
        check(
            torii.hasAuthoritativeCommittedTransaction(
                network = network,
                accountId = account.address,
                assetDefinitionId = pending.assetId,
                transactionHash = hash,
            )
        ) { "NEXUS_TRANSACTION_HISTORY_PROOF_MISSING" }
        val readBack = torii.getAssetBalanceByDefinition(
            network = network,
            accountId = account.address,
            assetDefinitionId = pending.assetId,
        )
        check(readBack.asset == pending.assetId) { "NEXUS_PENDING_ASSET_MISMATCH" }

        val exactHistoryMatches = torii.committedXorTransfers(
            network = network,
            accountId = account.address,
            assetDefinitionId = pending.assetId,
        ).count { transfer ->
            canonicalHash(transfer.transactionHash) == hash &&
                transfer.sender == account.address &&
                transfer.receiver == pending.recipient &&
                transfer.amount.toBigDecimalExact().compareTo(
                    pending.amount.toBigDecimalExact()
                ) == 0
        }
        if (exactHistoryMatches != 1) {
            val stillPending = update(
                pending.localId,
                hash,
                "COMMITTED_PENDING_RECONCILIATION",
                ambiguous = false,
            )
            return NexusSendResult(pending.localId, hash, stillPending.state)
        }
        val finalized = update(pending.localId, hash, "FINALIZED", ambiguous = false)
        return NexusSendResult(pending.localId, hash, finalized.state)
    }

    /** Polls the exact hash only; this component has no path that can sign or resubmit. */
    suspend fun reconcileUntilTerminal(
        localId: String,
        maxAttempts: Int = MAX_STATUS_ATTEMPTS,
        pollDelayMillis: Long = STATUS_POLL_DELAY_MILLIS,
    ): NexusSendResult {
        require(maxAttempts in 1..MAX_STATUS_ATTEMPTS)
        require(pollDelayMillis in 0..MAX_STATUS_POLL_DELAY_MILLIS)
        var result: NexusSendResult? = null
        repeat(maxAttempts) { attempt ->
            result = reconcile(localId)
            if (result?.state in TERMINAL_STATES || attempt == maxAttempts - 1) {
                return checkNotNull(result)
            }
            if (pollDelayMillis > 0) delay(pollDelayMillis)
        }
        return checkNotNull(result)
    }

    suspend fun recoverAfterRestart(): NexusRecoveryPass =
        recoverAfterRestart(MAX_RECOVERY_TRANSACTIONS, startOffset = 0)

    suspend fun recoverAfterRestart(maxTransactions: Int): NexusRecoveryPass =
        recoverAfterRestart(maxTransactions, startOffset = 0)

    override suspend fun recoverAfterRestart(
        maxTransactions: Int,
        startOffset: Long,
    ): NexusRecoveryPass {
        require(maxTransactions in 1..MAX_RECOVERY_TRANSACTIONS)
        require(startOffset >= 0)
        val candidates = database.walletIdentityDao().getUnresolvedTransactions()
            .filter {
                WalletNetworkId.fromWireId(it.networkId) in
                    setOf(WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA)
            }
        val normalizedOffset = if (candidates.isEmpty()) {
            0
        } else {
            (startOffset % candidates.size).toInt()
        }
        val orderedCandidates = if (normalizedOffset == 0) {
            candidates
        } else {
            candidates.drop(normalizedOffset) + candidates.take(normalizedOffset)
        }
        var terminal = 0
        var unresolved = 0
        var failures = 0
        val attempted = orderedCandidates.take(maxTransactions)
        attempted.forEach { transaction ->
            try {
                val result = reconcile(transaction.localId)
                if (result.state in TERMINAL_STATES) terminal += 1 else unresolved += 1
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failures += 1
            }
        }
        return NexusRecoveryPass(
            attempted = attempted.size,
            terminal = terminal,
            unresolved = unresolved,
            failures = failures,
            remainingUnattempted = candidates.size - attempted.size,
        )
    }

    private suspend fun requireActiveAccount(
        walletId: String,
        networkId: WalletNetworkId,
        requireSelected: Boolean,
    ): NetworkAccountLocal {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
            "WALLET_DELETION_ACTIVE"
        }
        val migration = database.walletIdentityDao()
            .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        val identity = checkNotNull(database.walletIdentityDao().getWallet(walletId)) {
            "NEXUS_WALLET_IDENTITY_MISSING"
        }
        check(
            if (migration == null) identity.migrationState == "VERIFIED"
            else migration.state == "VERIFIED"
        ) { "WALLET_MIGRATION_NOT_VERIFIED" }
        if (requireSelected) {
            val selected = userRepository.getCurSoraAccount()
            check(selected.substrateAddress == walletId) { "NEXUS_SELECTED_WALLET_CHANGED" }
        }
        check(identity.migrationState == "VERIFIED") { "NEXUS_WALLET_NOT_VERIFIED" }
        check(identity.secretSource == "MNEMONIC") { "NEXUS_MASTER_PHRASE_REQUIRED" }
        val account = checkNotNull(
            database.walletIdentityDao().getNetworkAccount(walletId, networkId.wireId)
        ) { "NEXUS_NETWORK_ACCOUNT_MISSING" }
        check(account.enabled) { "NEXUS_NETWORK_ACCOUNT_DISABLED" }
        check(account.address.isNotBlank() && account.publicKey.isNotBlank()) {
            "NEXUS_NETWORK_ACCOUNT_INCOMPLETE"
        }
        check(account.walletId == walletId && account.networkId == networkId.wireId) {
            "NEXUS_NETWORK_ACCOUNT_IDENTITY_MISMATCH"
        }
        val network = requireNetwork(networkId)
        check(
            account.derivationPath == network.derivationPath &&
                account.derivationVersion == identity.derivationVersion &&
                account.derivationVersion == SUPPORTED_DERIVATION_VERSION
        ) { "NEXUS_NETWORK_ACCOUNT_DERIVATION_MISMATCH" }
        val parsedAddress = IrohaAddressCodec.parse(account.address, network.chainDiscriminant)
        check(
            account.publicKey == account.publicKey.lowercase() &&
                ACCOUNT_PUBLIC_KEY.matches(account.publicKey) &&
                parsedAddress.publicKeyHex == account.publicKey
        ) { "NEXUS_NETWORK_ACCOUNT_KEY_MISMATCH" }
        return account
    }

    private suspend fun update(
        localId: String,
        transactionHash: String,
        state: String,
        ambiguous: Boolean,
    ): PendingNetworkTransactionLocal = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val canonicalTransactionHash = canonicalHash(transactionHash)
        val current = checkNotNull(
            database.walletIdentityDao().getPendingTransaction(localId)
        ) { "NEXUS_PENDING_TRANSACTION_MISSING" }
        val currentNetworkId = checkNotNull(WalletNetworkId.fromWireId(current.networkId))
        requireNetwork(currentNetworkId)
        check(WalletIdentityDao.hasCurrentChainIdentity(current)) {
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
        }
        check(
            current.transactionHash?.let(::canonicalHash) == canonicalTransactionHash
        ) { "NEXUS_PENDING_HASH_CHANGED" }
        if (current.state in TERMINAL_STATES) return@withLock current
        check(state in NEXUS_PENDING_STATES) { "NEXUS_PENDING_STATE_INVALID" }
        check(ambiguous == (state == "UNKNOWN")) { "NEXUS_PENDING_AMBIGUITY_INVALID" }
        if (state !in checkNotNull(ALLOWED_TRANSITIONS[current.state]) {
                "NEXUS_PENDING_STATE_INVALID"
            }
        ) {
            return@withLock current
        }
        val updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt)
        database.walletIdentityDao().updatePendingTransaction(
            localId = localId,
            transactionHash = canonicalTransactionHash,
            state = state,
            ambiguous = ambiguous,
            updatedAt = updatedAt,
        )
        current.copy(
            transactionHash = canonicalTransactionHash,
            state = state,
            submissionIsAmbiguous = ambiguous,
            updatedAt = updatedAt,
        )
    }

    private fun requireNetwork(id: WalletNetworkId): NexusNetwork = when (id) {
        WalletNetworkId.MINAMOTO -> NexusNetworks.minamoto
        WalletNetworkId.TAIRA -> NexusNetworks.taira
        WalletNetworkId.SORA2 -> throw IllegalArgumentException("NEXUS_WRONG_NETWORK")
    }

    private fun canonicalPositiveQuantity(value: String): String {
        check(NexusQuantityContract.isWireQuantity(value)) { "NEXUS_INVALID_QUANTITY" }
        val decimal = value.toBigDecimalExact()
        check(decimal.signum() > 0) { "NEXUS_AMOUNT_NOT_POSITIVE" }
        return decimal.stripTrailingZeros().toPlainString()
    }

    private fun String.toBigDecimalExact(): BigDecimal =
        runCatching { BigDecimal(this) }.getOrElse {
            throw IllegalArgumentException("NEXUS_INVALID_QUANTITY")
        }

    private fun canonicalHash(value: String): String =
        checkNotNull(NexusTransactionHash.normalized(value)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }

    private companion object {
        val ACCOUNT_PUBLIC_KEY = Regex("^[0-9a-f]{64}$")
        val TERMINAL_STATES = setOf("FINALIZED", "REJECTED")
        val NEXUS_PENDING_STATES = setOf(
            "SUBMITTED",
            "UNKNOWN",
            "COMMITTED_PENDING_RECONCILIATION",
            "FINALIZED",
            "REJECTED",
        )
        val ALLOWED_TRANSITIONS = mapOf(
            "SIGNED" to NEXUS_PENDING_STATES,
            "SUBMITTED" to NEXUS_PENDING_STATES,
            "UNKNOWN" to NEXUS_PENDING_STATES,
            "COMMITTED_PENDING_RECONCILIATION" to setOf(
                "COMMITTED_PENDING_RECONCILIATION",
                "FINALIZED",
            ),
        )
        const val MAX_STATUS_ATTEMPTS = 20
        const val STATUS_POLL_DELAY_MILLIS = 3_000L
        const val MAX_STATUS_POLL_DELAY_MILLIS = 10_000L
        const val MAX_RECOVERY_TRANSACTIONS = 50
        const val SUPPORTED_DERIVATION_VERSION = 1
    }
}
