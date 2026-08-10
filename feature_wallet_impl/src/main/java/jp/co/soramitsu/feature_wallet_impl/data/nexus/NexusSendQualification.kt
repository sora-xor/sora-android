package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.NexusNetwork

/**
 * Read-only projection of the capabilities required to admit a Nexus send.
 *
 * Portfolio and UI code receive this contract instead of retaining quote, signing, or finalized
 * checkpoint capabilities. The mutation coordinator remains the only UI-reachable owner of the
 * complete signer and finality-reader pair.
 */
interface NexusSendQualification {
    fun isQualifiedFor(network: NexusNetwork): Boolean
}

/**
 * Narrows the production signer and finality reader to a boolean status projection.
 *
 * This class deliberately implements neither full capability interface. Qualification adapters
 * are expected to be pure, but an adapter failure still disables sends without breaking qualified
 * portfolio reads.
 */
class DefaultNexusSendQualification(
    private val signer: NexusTransactionSigner,
    private val finalityReader: NexusFinalityReader,
) : NexusSendQualification {
    override fun isQualifiedFor(network: NexusNetwork): Boolean = try {
        signer.isQualifiedFor(network) && finalityReader.isQualifiedFor(network)
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false
    }
}
