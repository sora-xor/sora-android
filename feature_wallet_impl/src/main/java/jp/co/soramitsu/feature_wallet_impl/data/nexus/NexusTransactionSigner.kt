package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.NexusNetwork
import jp.co.soramitsu.common.nexus.NexusTransactionHash
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.model.NetworkAccountLocal

data class NexusTransferSigningRequest(
    val network: NexusNetwork,
    val account: NetworkAccountLocal,
    val recipient: String,
    val assetDefinitionId: String,
    val amount: String,
)

data class NexusTransferFeeQuote(
    val networkId: String,
    val authority: String,
    val recipient: String,
    val assetDefinitionId: String,
    val amount: String,
    val fee: String,
    val quoteIdentity: String,
    val validUntilBlock: Long?,
)

data class NexusSignedTransaction(
    val noritoBytes: ByteArray,
    val transactionHash: String,
)

/**
 * Read-only finalized-chain identity returned by the reviewed network reader.
 *
 * Keeping both wallet network and chain UUID in the proof prevents a height obtained from Taira
 * from satisfying a Minamoto transaction (or vice versa). The canonical nonzero finalized block
 * hash binds the checkpoint to the attested tip artifact; a height alone is not a finality proof.
 * The reviewed native verifier must decode Torii's checksummed Norito hash literal and project the
 * verified 32 bytes as the shared Android/iOS lowercase, prefix-free 64-hex representation.
 */
data class NexusFinalityCheckpoint(
    val networkId: WalletNetworkId,
    val chainId: String,
    val finalizedBlockHeight: Long,
    val finalizedBlockHash: String,
) {
    fun requireFor(network: NexusNetwork): Long {
        check(
            networkId == network.id &&
                chainId == network.chainId
        ) { "NEXUS_FINALITY_NETWORK_MISMATCH" }
        check(
            finalizedBlockHeight > 0L &&
                NexusTransactionHash.normalized(finalizedBlockHash) == finalizedBlockHash
        ) { "NEXUS_FINALITY_CHECKPOINT_INVALID" }
        return finalizedBlockHeight
    }
}

/**
 * Capability-limited finality source. Restart recovery receives this interface instead of a
 * transaction signer, so disabling mutations cannot grant recovery signing or submission access.
 */
interface NexusFinalityReader {
    fun isQualifiedFor(network: NexusNetwork): Boolean

    suspend fun finalizedCheckpoint(network: NexusNetwork): NexusFinalityCheckpoint
}

/**
 * Production implementations must keep mnemonic/key material inside the OS-secured credential
 * boundary, wipe derived seeds, and use a checksum-pinned Iroha/Norito codec.
 */
interface NexusTransactionSigner {
    fun isQualifiedFor(network: NexusNetwork): Boolean

    suspend fun quote(request: NexusTransferSigningRequest): NexusTransferFeeQuote

    suspend fun sign(
        request: NexusTransferSigningRequest,
        quote: NexusTransferFeeQuote,
    ): NexusSignedTransaction
}

/**
 * Fail-closed provider used until a reviewed SDK artifact and funded live receipt vectors are
 * installed. It prevents a read-capable build from accidentally advertising transaction support.
 */
class UnavailableNexusTransactionSigner : NexusTransactionSigner {
    override fun isQualifiedFor(network: NexusNetwork): Boolean = false

    override suspend fun quote(request: NexusTransferSigningRequest): NexusTransferFeeQuote =
        throw IllegalStateException("NEXUS_SIGNER_NOT_QUALIFIED")

    override suspend fun sign(
        request: NexusTransferSigningRequest,
        quote: NexusTransferFeeQuote,
    ): NexusSignedTransaction = throw IllegalStateException("NEXUS_SIGNER_NOT_QUALIFIED")
}

/** Fail-closed finality binding retained until the reviewed read-only native adapter is installed. */
class UnavailableNexusFinalityReader : NexusFinalityReader {
    override fun isQualifiedFor(network: NexusNetwork): Boolean = false

    override suspend fun finalizedCheckpoint(network: NexusNetwork): NexusFinalityCheckpoint =
        throw IllegalStateException("NEXUS_FINALITY_READER_NOT_QUALIFIED")
}
