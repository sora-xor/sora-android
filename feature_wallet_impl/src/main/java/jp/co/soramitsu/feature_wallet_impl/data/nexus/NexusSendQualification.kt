package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.NexusNetwork

/**
 * Read-only projection of the capabilities required to admit a Nexus send.
 *
 * Portfolio and UI code receive this contract instead of retaining quote, signing, or finalized
 * checkpoint capabilities. The mutation coordinator remains the only UI-reachable owner of the
 * complete signer and finality-reader pair.
 */
enum class NexusCapabilityStatus {
    QUALIFIED,
    UNAVAILABLE,
    ERROR,
}

data class NexusSendCapabilities(
    val signing: NexusCapabilityStatus,
    val finality: NexusCapabilityStatus,
) {
    val supportsSend: Boolean
        get() = signing == NexusCapabilityStatus.QUALIFIED &&
            finality == NexusCapabilityStatus.QUALIFIED

    companion object {
        fun unavailable(): NexusSendCapabilities = NexusSendCapabilities(
            signing = NexusCapabilityStatus.UNAVAILABLE,
            finality = NexusCapabilityStatus.UNAVAILABLE,
        )
    }
}

interface NexusSendQualification {
    fun capabilitiesFor(network: NexusNetwork): NexusSendCapabilities
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
    override fun capabilitiesFor(network: NexusNetwork): NexusSendCapabilities =
        NexusSendCapabilities(
            signing = qualification { signer.isQualifiedFor(network) },
            finality = qualification { finalityReader.isQualifiedFor(network) },
        )

    private inline fun qualification(block: () -> Boolean): NexusCapabilityStatus = try {
        if (block()) NexusCapabilityStatus.QUALIFIED else NexusCapabilityStatus.UNAVAILABLE
    } catch (_: Exception) {
        NexusCapabilityStatus.ERROR
    } catch (_: LinkageError) {
        NexusCapabilityStatus.ERROR
    }
}
