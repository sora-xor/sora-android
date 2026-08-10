package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusToriiClient
import jp.co.soramitsu.common.nexus.NexusToriiException
import jp.co.soramitsu.common.nexus.NexusTransactionHash
import jp.co.soramitsu.common.nexus.TairaDeployment
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager

data class NexusSendRequest(
    val walletId: String,
    val networkId: WalletNetworkId,
    val recipient: String,
    val amount: String,
)

data class NexusSendResult(
    val localId: String,
    val transactionHash: String,
    val state: String,
)

data class NexusRecoveryPass(
    val attempted: Int,
    val terminal: Int,
    val unresolved: Int,
    val failures: Int,
    val remainingUnattempted: Int,
) {
    init {
        require(attempted >= 0)
        require(terminal >= 0)
        require(unresolved >= 0)
        require(failures >= 0)
        require(remainingUnattempted >= 0)
        require(terminal + unresolved + failures == attempted)
    }

    val requiresRetry: Boolean
        get() = unresolved > 0 || failures > 0 || remainingUnattempted > 0
}

interface NexusPendingRecovery {
    suspend fun recoverAfterRestart(
        maxTransactions: Int,
        startOffset: Long,
    ): NexusRecoveryPass
}

class NexusPreparedSend internal constructor(
    val request: NexusSendRequest,
    val canonicalRecipient: String,
    val canonicalAmount: String,
    val assetDefinitionId: String,
    val fee: String,
    val availableBalance: String,
    val quoteIdentity: String,
    internal val signingRequest: NexusTransferSigningRequest,
    internal val quote: NexusTransferFeeQuote,
) {
    private val submissionStarted = AtomicBoolean(false)

    internal fun reserveSubmission(): Boolean =
        submissionStarted.compareAndSet(false, true)
}

/**
 * Coordinates Nexus sends without ever retrying a submission. Pending records are written before
 * transport, so process death and ambiguous responses can be reconciled by transaction hash.
 */
@Singleton
class NexusTransactionCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val userRepository: UserRepository,
    private val torii: NexusToriiClient,
    private val signer: NexusTransactionSigner,
    private val finalityReader: NexusFinalityReader,
    private val featureManager: ProductionFeatureManager,
    private val pendingRecoveryScheduler: NexusPendingRecoveryScheduler,
    private val pendingReconciler: NexusPendingReconciler,
    private val submissionActivity: NexusSubmissionActivityRegistry,
) {

    /**
     * Performs address, amount, fee, balance, selected-wallet, and signer qualification checks
     * without signing. The returned values are the confirmation contract shown to the user.
     */
    suspend fun prepare(request: NexusSendRequest): NexusPreparedSend {
        val network = requireNetwork(request.networkId)
        val state = featureManager.getState()
        requireSendEnabled(state, request.networkId)
        check(signer.isQualifiedFor(network)) { "NEXUS_SIGNER_NOT_QUALIFIED" }
        check(finalityReader.isQualifiedFor(network)) {
            "NEXUS_FINALITY_READER_NOT_QUALIFIED"
        }
        requirePendingChainRecoveryClear()

        val account = requireActiveAccount(request.walletId, request.networkId)
        val canonicalRecipient =
            IrohaAddressCodec.parse(request.recipient, network.chainDiscriminant).address
        val canonicalAmount = canonicalPositiveQuantity(request.amount)
        val xorDefinition = torii.resolveXorDefinition(network)
        val balance = torii.getXorBalance(network, account.address)
        check(balance.asset == xorDefinition.id) { "NEXUS_ASSET_DEFINITION_CHANGED" }

        val signingRequest = NexusTransferSigningRequest(
            network = network,
            account = account,
            recipient = canonicalRecipient,
            assetDefinitionId = xorDefinition.id,
            amount = canonicalAmount,
        )
        val quote = signer.quote(signingRequest)
        validateQuote(signingRequest, quote)
        validateQuoteExpiry(network, quote)
        val fee = canonicalPositiveFee(quote.fee)
        check(canonicalNonNegativeQuantity(balance.quantity).toBigDecimalExact() >=
            canonicalAmount.toBigDecimalExact() + fee.toBigDecimalExact()) {
            "NEXUS_INSUFFICIENT_XOR"
        }
        return NexusPreparedSend(
            request = request,
            canonicalRecipient = canonicalRecipient,
            canonicalAmount = canonicalAmount,
            assetDefinitionId = xorDefinition.id,
            fee = fee,
            availableBalance = canonicalNonNegativeQuantity(balance.quantity),
            quoteIdentity = quote.quoteIdentity,
            signingRequest = signingRequest,
            quote = quote,
        )
    }

    /**
     * Revalidates a displayed confirmation and submits exactly once. Submission errors that may
     * have reached Torii are recorded as ambiguous and are only resolved by hash lookup.
     */
    suspend fun submit(prepared: NexusPreparedSend): NexusSendResult {
        check(prepared.reserveSubmission()) {
            "NEXUS_CONFIRMATION_ALREADY_SUBMITTED"
        }
        val request = prepared.request
        val network = requireNetwork(request.networkId)
        val state = featureManager.getState()
        requireSendEnabled(state, request.networkId)
        check(signer.isQualifiedFor(network)) { "NEXUS_SIGNER_NOT_QUALIFIED" }
        check(finalityReader.isQualifiedFor(network)) {
            "NEXUS_FINALITY_READER_NOT_QUALIFIED"
        }
        validatePreparedContract(prepared, network)
        requirePendingChainRecoveryClear()
        val account = requireActiveAccount(request.walletId, request.networkId)
        check(account == prepared.signingRequest.account) {
            "NEXUS_ACCOUNT_CHANGED"
        }
        check(
            IrohaAddressCodec.parse(
                prepared.canonicalRecipient,
                network.chainDiscriminant,
            ).address == prepared.signingRequest.recipient
        ) { "NEXUS_RECIPIENT_CHANGED" }
        check(prepared.canonicalAmount == prepared.signingRequest.amount) {
            "NEXUS_AMOUNT_CHANGED"
        }
        val xorDefinition = torii.resolveXorDefinition(network)
        check(xorDefinition.id == prepared.assetDefinitionId) {
            "NEXUS_ASSET_DEFINITION_CHANGED"
        }
        val freshQuote = signer.quote(prepared.signingRequest)
        validateQuote(prepared.signingRequest, freshQuote)
        validateQuoteExpiry(network, freshQuote)
        val freshFee = canonicalPositiveFee(freshQuote.fee)
        check(
            freshQuote.quoteIdentity == prepared.quoteIdentity &&
                freshFee == prepared.fee &&
                freshQuote.validUntilBlock == prepared.quote.validUntilBlock
        ) { "NEXUS_QUOTE_CHANGED" }
        val latestBalanceResponse = torii.getXorBalance(network, account.address)
        check(latestBalanceResponse.asset == xorDefinition.id) {
            "NEXUS_ASSET_DEFINITION_CHANGED"
        }
        val latestBalance = canonicalNonNegativeQuantity(latestBalanceResponse.quantity)
        check(
            latestBalance.toBigDecimalExact() >=
                prepared.canonicalAmount.toBigDecimalExact() + freshFee.toBigDecimalExact()
        ) { "NEXUS_BALANCE_CHANGED" }
        val staged = WalletMutationCoordinator.withLock {
            val finalState = featureManager.getState()
            requireSendEnabled(finalState, request.networkId)
            check(signer.isQualifiedFor(network)) { "NEXUS_SIGNER_NOT_QUALIFIED" }
            check(finalityReader.isQualifiedFor(network)) {
                "NEXUS_FINALITY_READER_NOT_QUALIFIED"
            }
            requirePendingChainRecoveryClear()
            validatePreparedContract(prepared, network)
            val finalAccount = requireActiveAccount(request.walletId, request.networkId)
            check(finalAccount == prepared.signingRequest.account) {
                "NEXUS_ACCOUNT_CHANGED"
            }
            check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
                "WALLET_DELETION_ACTIVE"
            }
            val finalXorDefinition = torii.resolveXorDefinition(network)
            check(
                finalXorDefinition.id == xorDefinition.id &&
                    finalXorDefinition.id == prepared.assetDefinitionId
            ) { "NEXUS_ASSET_DEFINITION_CHANGED" }
            val finalQuote = signer.quote(prepared.signingRequest)
            validateQuote(prepared.signingRequest, finalQuote)
            validateQuoteExpiry(network, finalQuote)
            val finalFee = canonicalPositiveFee(finalQuote.fee)
            check(
                finalQuote.quoteIdentity == freshQuote.quoteIdentity &&
                    finalFee == freshFee &&
                    finalQuote.validUntilBlock == freshQuote.validUntilBlock
            ) { "NEXUS_QUOTE_CHANGED" }
            val finalBalanceResponse = torii.getXorBalance(network, finalAccount.address)
            check(finalBalanceResponse.asset == finalXorDefinition.id) {
                "NEXUS_ASSET_DEFINITION_CHANGED"
            }
            val finalBalance = canonicalNonNegativeQuantity(finalBalanceResponse.quantity)
            check(
                finalBalance.toBigDecimalExact() >=
                    prepared.canonicalAmount.toBigDecimalExact() +
                    finalFee.toBigDecimalExact()
            ) { "NEXUS_BALANCE_CHANGED" }

            WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            requirePendingChainRecoveryClear()
            val localId = durablePendingLocalId(network)
            val signed = signer.sign(prepared.signingRequest, finalQuote)
            try {
                check(signed.noritoBytes.isNotEmpty()) {
                    "NEXUS_EMPTY_SIGNED_TRANSACTION"
                }
                val canonicalHash = canonicalHash(signed.transactionHash)
                val now = System.currentTimeMillis()
                WalletRecoveryCapabilityGate.requireUserMutationAllowed()
                check(submissionActivity.reserve(localId)) {
                    "NEXUS_ACTIVE_SUBMISSION_ID_COLLISION"
                }
                try {
                    database.walletIdentityDao().insertPendingTransaction(
                        PendingNetworkTransactionLocal(
                            localId = localId,
                            walletId = request.walletId,
                            networkId = request.networkId.wireId,
                            chainId = network.chainId,
                            transactionHash = canonicalHash,
                            assetId = finalXorDefinition.id,
                            amount = prepared.canonicalAmount,
                            recipient = prepared.canonicalRecipient,
                            state = "SIGNED",
                            submissionIsAmbiguous = false,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                    StagedSubmission(
                        signed = signed,
                        localId = localId,
                        transactionHash = canonicalHash,
                    )
                } catch (error: Throwable) {
                    submissionActivity.release(localId)
                    // Room cancellation can race a committed insert. A status-only kick is harmless
                    // when no row exists and guarantees a committed exact hash is not stranded.
                    runCatching { pendingRecoveryScheduler.kickAfterJournal() }
                    throw error
                }
            } catch (error: Throwable) {
                signed.noritoBytes.fill(0)
                throw error
            }
        }
        var transportStarted = false
        try {
            try {
                val receipt = WalletMutationCoordinator.withLock {
                    val transportState = featureManager.getState()
                    requireSendEnabled(transportState, request.networkId)
                    check(signer.isQualifiedFor(network)) {
                        "NEXUS_SIGNER_NOT_QUALIFIED"
                    }
                    check(finalityReader.isQualifiedFor(network)) {
                        "NEXUS_FINALITY_READER_NOT_QUALIFIED"
                    }
                    requirePendingChainRecoveryClear()
                    validatePreparedContract(prepared, network)
                    val transportAccount = requireActiveAccount(
                        request.walletId,
                        request.networkId,
                    )
                    check(transportAccount == prepared.signingRequest.account) {
                        "NEXUS_ACCOUNT_CHANGED"
                    }
                    check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
                        "WALLET_DELETION_ACTIVE"
                    }
                    val transportXorDefinition = torii.resolveXorDefinition(
                        network
                    )
                    check(
                        transportXorDefinition.id == prepared.assetDefinitionId
                    ) { "NEXUS_ASSET_DEFINITION_CHANGED" }
                    WalletRecoveryCapabilityGate.requireUserMutationAllowed()
                    requirePendingChainRecoveryClear()
                    transportStarted = true
                    torii.submit(network, staged.signed.noritoBytes)
                }
                val receiptHash = canonicalHash(receipt.payload.transactionHash)
                val entrypointHash = canonicalHash(receipt.payload.entrypointHash)
                val signedReceiptHash = receipt.payload.signedTransactionHash
                    ?.let(::canonicalHash)
                if (
                    receiptHash != staged.transactionHash ||
                    entrypointHash != staged.transactionHash ||
                    (signedReceiptHash != null &&
                        signedReceiptHash != staged.transactionHash) ||
                    receipt.payload.submittedAtMillis <= 0 ||
                    receipt.payload.submittedAtHeight < 0
                ) {
                    update(
                        staged.localId,
                        staged.transactionHash,
                        "UNKNOWN",
                        ambiguous = true,
                    )
                    throw IllegalStateException("NEXUS_RECEIPT_INVALID")
                }
                val persisted = update(
                    staged.localId,
                    staged.transactionHash,
                    "SUBMITTED",
                    ambiguous = false,
                )
                return NexusSendResult(
                    staged.localId,
                    staged.transactionHash,
                    persisted.state,
                )
            } catch (error: NexusToriiException) {
                val ambiguous = error.submissionMayHaveReachedTorii
                update(
                    localId = staged.localId,
                    transactionHash = staged.transactionHash,
                    state = if (ambiguous) "UNKNOWN" else "REJECTED",
                    ambiguous = ambiguous,
                )
                throw error
            } catch (error: Throwable) {
                // A failure while waiting for the final wallet/feature validation lock provably
                // precedes submission. Once Torii handoff begins, any unclassified failure is
                // ambiguous. Persist the exact hash in either case and never sign or submit again.
                update(
                    staged.localId,
                    staged.transactionHash,
                    state = if (transportStarted) "UNKNOWN" else "REJECTED",
                    ambiguous = transportStarted,
                )
                throw error
            }
        } finally {
            staged.signed.noritoBytes.fill(0)
            submissionActivity.release(staged.localId)
            // Scheduling must never rewrite an already observed submission outcome. If the local
            // scheduler is temporarily unavailable, app startup will enqueue the same unique work.
            runCatching { pendingRecoveryScheduler.kickAfterJournal() }
        }
    }

    /** Advances the exact hash through the capability-limited status-only reconciler. */
    suspend fun reconcile(localId: String): NexusSendResult =
        pendingReconciler.reconcile(localId)

    suspend fun reconcileUntilTerminal(
        localId: String,
        maxAttempts: Int = MAX_STATUS_ATTEMPTS,
        pollDelayMillis: Long = STATUS_POLL_DELAY_MILLIS,
    ): NexusSendResult = pendingReconciler.reconcileUntilTerminal(
        localId = localId,
        maxAttempts = maxAttempts,
        pollDelayMillis = pollDelayMillis,
    )

    suspend fun submitAndTrack(prepared: NexusPreparedSend): NexusSendResult {
        val submitted = submit(prepared)
        return reconcileUntilTerminal(submitted.localId)
    }

    suspend fun recoverAfterRestart(): NexusRecoveryPass =
        pendingReconciler.recoverAfterRestart()

    suspend fun recoverAfterRestart(
        maxTransactions: Int,
    ): NexusRecoveryPass = pendingReconciler.recoverAfterRestart(maxTransactions)

    suspend fun recoverAfterRestart(
        maxTransactions: Int,
        startOffset: Long,
    ): NexusRecoveryPass = pendingReconciler.recoverAfterRestart(
        maxTransactions = maxTransactions,
        startOffset = startOffset,
    )

    private suspend fun requireActiveAccount(
        walletId: String,
        networkId: WalletNetworkId,
        requireSelected: Boolean = true,
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
        val parsedAddress = IrohaAddressCodec.parse(
            account.address,
            network.chainDiscriminant,
        )
        check(
            account.publicKey == account.publicKey.lowercase() &&
                ACCOUNT_PUBLIC_KEY.matches(account.publicKey) &&
                parsedAddress.publicKeyHex == account.publicKey
        ) { "NEXUS_NETWORK_ACCOUNT_KEY_MISMATCH" }
        return account
    }

    private fun validateQuote(
        request: NexusTransferSigningRequest,
        quote: NexusTransferFeeQuote,
    ) {
        check(quote.networkId == request.network.id.wireId) { "NEXUS_QUOTE_NETWORK_MISMATCH" }
        check(quote.authority == request.account.address) { "NEXUS_QUOTE_AUTHORITY_MISMATCH" }
        check(quote.recipient == request.recipient) { "NEXUS_QUOTE_RECIPIENT_MISMATCH" }
        check(quote.assetDefinitionId == request.assetDefinitionId) {
            "NEXUS_QUOTE_ASSET_MISMATCH"
        }
        check(quote.amount == request.amount) { "NEXUS_QUOTE_AMOUNT_MISMATCH" }
        canonicalPositiveFee(quote.fee)
        check(quote.quoteIdentity.isNotBlank()) { "NEXUS_QUOTE_IDENTITY_MISSING" }
        check(quote.validUntilBlock == null || quote.validUntilBlock >= 0L) {
            "NEXUS_QUOTE_EXPIRY_INVALID"
        }
    }

    private fun validatePreparedContract(
        prepared: NexusPreparedSend,
        network: jp.co.soramitsu.common.nexus.NexusNetwork,
    ) {
        check(prepared.signingRequest.network == network) {
            "NEXUS_SIGNING_NETWORK_MISMATCH"
        }
        check(
            prepared.signingRequest.account.walletId == prepared.request.walletId &&
                prepared.signingRequest.account.networkId == prepared.request.networkId.wireId
        ) { "NEXUS_SIGNING_ACCOUNT_MISMATCH" }
        check(prepared.signingRequest.recipient == prepared.canonicalRecipient) {
            "NEXUS_RECIPIENT_CHANGED"
        }
        val canonicalAmount = canonicalPositiveQuantity(prepared.canonicalAmount)
        check(
            canonicalAmount == prepared.canonicalAmount &&
                canonicalAmount == prepared.signingRequest.amount
        ) { "NEXUS_AMOUNT_CHANGED" }
        check(prepared.signingRequest.assetDefinitionId == prepared.assetDefinitionId) {
            "NEXUS_SIGNING_ASSET_MISMATCH"
        }
        validateQuote(prepared.signingRequest, prepared.quote)
        check(
            canonicalPositiveFee(prepared.quote.fee) == prepared.fee &&
                prepared.quote.quoteIdentity == prepared.quoteIdentity
        ) { "NEXUS_PREPARED_QUOTE_MISMATCH" }
    }

    private suspend fun validateQuoteExpiry(
        network: jp.co.soramitsu.common.nexus.NexusNetwork,
        quote: NexusTransferFeeQuote,
    ) {
        check(finalityReader.isQualifiedFor(network)) {
            "NEXUS_FINALITY_READER_NOT_QUALIFIED"
        }
        val finalized = finalityReader.finalizedCheckpoint(network).requireFor(network)
        quote.validUntilBlock?.let { validUntil ->
            check(finalized <= validUntil) { "NEXUS_QUOTE_EXPIRED" }
        }
    }

    private suspend fun requirePendingChainRecoveryClear() {
        check(
            database.walletIdentityDao()
                .countPendingTransactionsRequiringChainRecovery() == 0
        ) { "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED" }
    }

    private suspend fun update(
        localId: String,
        transactionHash: String?,
        state: String,
        ambiguous: Boolean,
    ): PendingNetworkTransactionLocal = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val canonicalTransactionHash = canonicalHash(
            checkNotNull(transactionHash) { "NEXUS_PENDING_HASH_MISSING" }
        )
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
        if (current.state in TERMINAL_STATES) {
            return@withLock current
        }
        check(state in NEXUS_PENDING_STATES) { "NEXUS_PENDING_STATE_INVALID" }
        check(ambiguous == (state == "UNKNOWN")) {
            "NEXUS_PENDING_AMBIGUITY_INVALID"
        }
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

    private fun requireNetwork(id: WalletNetworkId) = when (id) {
        WalletNetworkId.MINAMOTO -> NexusNetworks.minamoto
        WalletNetworkId.TAIRA -> NexusNetworks.taira
        WalletNetworkId.SORA2 -> throw IllegalArgumentException("NEXUS_WRONG_NETWORK")
    }

    private fun durablePendingLocalId(network: jp.co.soramitsu.common.nexus.NexusNetwork): String {
        val nonce = UUID.randomUUID().toString()
        if (network.id != WalletNetworkId.TAIRA) return nonce

        val binding = checkNotNull(TairaDeployment.binding) {
            "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED"
        }
        check(
            network.chainId == binding.currentChainId &&
                network.deploymentManifestSha256 == binding.manifestSha256
        ) { "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED" }
        return "${binding.pendingJournalPrefix}$nonce".also {
            check(it.length <= 128) { "PENDING_TRANSACTION_ID_INVALID" }
        }
    }

    private fun requireSendEnabled(
        state: jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState,
        networkId: WalletNetworkId,
    ) {
        check(
            state.nexusAvailable &&
                state.nexusSendsAvailable &&
                (networkId != WalletNetworkId.TAIRA || state.tairaVisible)
        ) { "NEXUS_SEND_DISABLED" }
    }

    private fun canonicalPositiveQuantity(value: String): String {
        val canonical = canonicalNonNegativeQuantity(value)
        check(canonical.toBigDecimalExact().signum() > 0) { "NEXUS_AMOUNT_NOT_POSITIVE" }
        return canonical
    }

    private fun canonicalPositiveFee(value: String): String {
        val canonical = canonicalNonNegativeQuantity(value)
        check(canonical.toBigDecimalExact().signum() > 0) { "NEXUS_FEE_NOT_POSITIVE" }
        return canonical
    }

    private fun canonicalNonNegativeQuantity(value: String): String {
        check(NexusQuantityContract.isWireQuantity(value)) {
            "NEXUS_INVALID_QUANTITY"
        }
        val decimal = value.toBigDecimalExact()
        check(decimal.signum() >= 0) { "NEXUS_INVALID_QUANTITY" }
        return decimal.stripTrailingZeros().toPlainString()
    }

    private fun String.toBigDecimalExact(): BigDecimal =
        runCatching { BigDecimal(this) }.getOrElse {
            throw IllegalArgumentException("NEXUS_INVALID_QUANTITY")
        }

    private fun canonicalHash(value: String): String {
        return checkNotNull(NexusTransactionHash.normalized(value)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }
    }

    private data class StagedSubmission(
        val signed: NexusSignedTransaction,
        val localId: String,
        val transactionHash: String,
    )

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
        const val SUPPORTED_DERIVATION_VERSION = 1
    }
}
