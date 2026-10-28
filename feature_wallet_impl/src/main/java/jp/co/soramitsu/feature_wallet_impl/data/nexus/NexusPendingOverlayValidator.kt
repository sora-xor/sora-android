package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusAssetDefinitionIdentity
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusTransactionHash
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal

/**
 * Fails closed before a durable pending row is projected into a wallet/network portfolio.
 *
 * The DAO query is scoped too, but recovery/UI code treats persisted rows as untrusted input so a
 * corrupt or interrupted row cannot appear beneath another wallet or network badge.
 */
internal object NexusPendingOverlayValidator {
    sealed interface Projection {
        data class Valid(
            val transactions: List<NexusPendingTransaction>,
        ) : Projection

        data object RecoveryRequired : Projection
    }

    private const val MAX_PENDING_ID_LENGTH = 128
    private val pendingStates = setOf(
        "SIGNED",
        "SUBMITTED",
        "UNKNOWN",
        "COMMITTED_PENDING_RECONCILIATION",
    )

    fun requireValid(
        transaction: PendingNetworkTransactionLocal,
        expectedWalletId: String,
        expectedNetworkId: String,
        expectedChainId: String,
        discriminant: Int,
    ) {
        check(
            transaction.walletId == expectedWalletId &&
                transaction.networkId == expectedNetworkId
        ) { "NEXUS_PENDING_SCOPE_INVALID" }
        check(transaction.chainId == expectedChainId) {
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
        }
        check(WalletIdentityDao.hasCurrentChainIdentity(transaction)) {
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
        }
        check(
            transaction.localId.isNotBlank() &&
                transaction.localId.length <= MAX_PENDING_ID_LENGTH
        ) { "NEXUS_PENDING_ID_INVALID" }
        val hash = transaction.transactionHash
        check(hash != null && NexusTransactionHash.normalized(hash) == hash) {
            "NEXUS_PENDING_HASH_INVALID"
        }
        check(
            IrohaAddressCodec.parse(transaction.recipient, discriminant).address ==
                transaction.recipient
        ) { "NEXUS_PENDING_RECIPIENT_INVALID" }
        check(canonicalPositiveQuantity(transaction.amount) == transaction.amount) {
            "NEXUS_PENDING_AMOUNT_INVALID"
        }
        check(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(transaction.assetId)
        ) { "NEXUS_PENDING_ASSET_INVALID" }
        check(transaction.state in pendingStates) { "NEXUS_PENDING_STATE_INVALID" }
        check(transaction.submissionIsAmbiguous == (transaction.state == "UNKNOWN")) {
            "NEXUS_PENDING_AMBIGUITY_INVALID"
        }
        check(
            transaction.createdAt > 0 &&
                transaction.updatedAt >= transaction.createdAt
        ) { "NEXUS_PENDING_TIMESTAMP_INVALID" }
    }

    /**
     * A malformed durable row is recovery evidence, not an ordinary overlay and not permission to
     * hide the network. Project the whole network batch atomically or expose no unsafe row fields.
     */
    fun projectForPortfolio(
        transactions: List<PendingNetworkTransactionLocal>,
        expectedWalletId: String,
        expectedNetworkId: String,
        expectedChainId: String,
        discriminant: Int,
    ): Projection = try {
        Projection.Valid(
            transactions.map { transaction ->
                requireValid(
                    transaction = transaction,
                    expectedWalletId = expectedWalletId,
                    expectedNetworkId = expectedNetworkId,
                    expectedChainId = expectedChainId,
                    discriminant = discriminant,
                )
                NexusPendingTransaction(
                    localId = transaction.localId,
                    transactionHash = transaction.transactionHash,
                    assetDefinitionId = transaction.assetId,
                    amount = transaction.amount,
                    recipient = transaction.recipient,
                    state = transaction.state,
                    submissionIsAmbiguous = transaction.submissionIsAmbiguous,
                )
            }
        )
    } catch (_: IllegalArgumentException) {
        Projection.RecoveryRequired
    } catch (_: IllegalStateException) {
        Projection.RecoveryRequired
    }

    private fun canonicalPositiveQuantity(value: String): String {
        check(NexusQuantityContract.isWireQuantity(value)) {
            "NEXUS_PENDING_AMOUNT_INVALID"
        }
        val decimal = runCatching { BigDecimal(value) }
            .getOrElse { throw IllegalArgumentException("NEXUS_PENDING_AMOUNT_INVALID") }
        check(decimal.signum() > 0) { "NEXUS_PENDING_AMOUNT_INVALID" }
        return decimal.stripTrailingZeros().toPlainString()
    }
}

/** Routes any malformed row in the shared durable journal to an explicit recovery surface. */
internal object NexusPendingJournalPresentationContract {
    data class PortfolioAccess(
        val allowsQualifiedReads: Boolean,
        val projectsPending: Boolean,
        val allowsSends: Boolean,
    )

    /**
     * Recovery evidence is a mutation boundary, not a read boundary. Qualified
     * current-chain balance and finalized-history reads deliberately remain
     * allowed while untrusted pending projection and sends are blocked.
     */
    fun portfolioAccess(recoveryRequired: Boolean) = PortfolioAccess(
        allowsQualifiedReads = true,
        projectsPending = !recoveryRequired,
        allowsSends = !recoveryRequired,
    )

    fun requiresRecovery(
        transactions: List<PendingNetworkTransactionLocal>,
        networkAccounts: List<NetworkAccountLocal>,
        maximumTransactions: Int,
    ): Boolean {
        require(maximumTransactions > 0)
        if (transactions.size > maximumTransactions) return true
        val accountRoutes = networkAccounts.mapTo(mutableSetOf()) {
            it.walletId to it.networkId
        }
        return transactions.any { transaction ->
            try {
                WalletIdentityDao.validatePendingTransaction(transaction)
                !WalletIdentityDao.hasCurrentChainIdentity(transaction) ||
                    (
                        transaction.networkId in setOf("minamoto", "taira") &&
                            (transaction.walletId to transaction.networkId) !in accountRoutes
                    )
            } catch (_: IllegalArgumentException) {
                true
            } catch (_: IllegalStateException) {
                true
            }
        }
    }
}
