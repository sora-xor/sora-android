package jp.co.soramitsu.sora.substrate.runtime

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.xsubstrate.runtime.metadata.module

data class VerifiedSora2Runtime(
    val sourceRevision: String,
    val specVersion: Int,
    val transactionVersion: Int,
)

/**
 * Opaque proof that [context] passed every reviewed SORA2 Polkamarkt mutation gate. The raw
 * context remains internal to the substrate module so feature code can pass this proof to the
 * signing coordinator but cannot substitute a different snapshot after qualification.
 */
class QualifiedSora2MutationRuntime internal constructor(
    internal val context: Sora2MutationRuntimeContext,
    val verified: VerifiedSora2Runtime,
)

internal fun requireReviewedSora2SnapshotIdentity(
    specVersion: Int,
    metadataSha256: String,
    typesSha256: String,
) {
    check(specVersion == Sora2RuntimeContract.SPEC_VERSION) {
        "SORA2_RUNTIME_SNAPSHOT_VERSION_MISMATCH"
    }
    check(metadataSha256 == Sora2RuntimeContract.METADATA_FILE_SHA256) {
        "SORA2_RUNTIME_SNAPSHOT_IDENTITY_MISMATCH"
    }
    check(typesSha256 == Sora2RuntimeContract.TYPES_FILE_SHA256) {
        "SORA2_RUNTIME_TYPES_IDENTITY_MISMATCH"
    }
}

/**
 * A newer finalized head is expected and is not identity drift. Each head is independently
 * admitted by [RuntimeManager]; everything that controls call encoding or signing must remain
 * identical between the qualified signing context and the last pre-transport check.
 */
internal fun requireSameSora2MutationRuntimeIdentity(
    signing: Sora2MutationRuntimeContext,
    current: Sora2MutationRuntimeContext,
) {
    signing.finalizedHash.requireCanonicalBlockHash("SORA2_SIGNING_FINALIZED_HASH_INVALID")
    current.finalizedHash.requireCanonicalBlockHash("SORA2_CURRENT_FINALIZED_HASH_INVALID")
    val signingGenesis = signing.genesisHash.requireCanonicalBlockHash(
        "SORA2_SIGNING_GENESIS_HASH_INVALID"
    )
    val currentGenesis = current.genesisHash.requireCanonicalBlockHash(
        "SORA2_CURRENT_GENESIS_HASH_INVALID"
    )
    check(signingGenesis == currentGenesis) {
        "SORA2_MUTATION_GENESIS_DRIFT"
    }
    check(signing.runtimeVersion.specVersion == current.runtimeVersion.specVersion) {
        "SORA2_MUTATION_SPEC_VERSION_DRIFT"
    }
    check(
        signing.runtimeVersion.transactionVersion == current.runtimeVersion.transactionVersion
    ) { "SORA2_MUTATION_TRANSACTION_VERSION_DRIFT" }
    check(signing.metadataSha256 == current.metadataSha256) {
        "SORA2_MUTATION_METADATA_DRIFT"
    }
    check(signing.typesSha256 == current.typesSha256) {
        "SORA2_MUTATION_TYPES_DRIFT"
    }
    check(current.finalizedBlockNumber >= signing.finalizedBlockNumber) {
        "SORA2_MUTATION_FINALIZED_HEIGHT_REGRESSION"
    }
    val eraDeath = try {
        Math.addExact(
            signing.finalizedBlockNumber,
            SubstrateOptionsProvider.mortalEraLength.toLong(),
        )
    } catch (error: ArithmeticException) {
        throw IllegalStateException("SORA2_MUTATION_MORTAL_ERA_INVALID", error)
    }
    check(current.finalizedBlockNumber < eraDeath) {
        "SORA2_MUTATION_MORTAL_ERA_EXPIRED"
    }
}

/**
 * Mutations whose runtime authority came from the reviewed HTTPS endpoint may only be handed to
 * its exact reviewed WebSocket counterpart. Deliberately avoid URL normalization: aliases,
 * alternate ports, query strings, user info, fragments, and arbitrary selected nodes are not the
 * transport covered by the checked-in runtime contract.
 */
internal fun requireReviewedSora2WebSocketEndpoint(observed: String?): String {
    return reviewedSora2WebSocketEndpointOrNull(observed)
        ?: throw Sora2RuntimeIdentityException("SORA2_MUTATION_TRANSPORT_UNREVIEWED")
}

internal fun reviewedSora2WebSocketEndpointOrNull(observed: String?): String? =
    observed?.takeIf {
        it == Sora2RuntimeContract.RUNTIME_WS_ENDPOINT ||
            it == "${Sora2RuntimeContract.RUNTIME_WS_ENDPOINT}/"
    }?.removeSuffix("/")

/**
 * Release contract generated from the reviewed sora2-network source revision. Mutations fail
 * closed on runtime drift while read-only discovery remains available.
 */
@Singleton
class Sora2RuntimeContract @Inject constructor(
    private val runtimeManager: RuntimeManager,
) {
    suspend fun requirePolkamarktMutationRuntime(): VerifiedSora2Runtime {
        return requirePolkamarktMutationRuntimeContext().verified
    }

    /**
     * Returns the exact qualified context that Polkamarkt must pass into its signing builder.
     * Callers must not qualify one context and then let the builder fetch another one.
     */
    suspend fun requirePolkamarktMutationRuntimeContext(): QualifiedSora2MutationRuntime {
        // Resolve one finalized runtime checkpoint through the exact manifest-bound bounded HTTPS
        // client. Every mutation consumer must validate the same context that
        // ExtrinsicBuilderFactory uses for metadata and signed extensions.
        val context = runtimeManager.getMutationRuntimeContext()
        check(context.genesisHash.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true)) {
            "SORA2_GENESIS_HASH_MISMATCH"
        }
        val version = context.runtimeVersion
        check(version.specVersion == SPEC_VERSION) { "SORA2_SPEC_VERSION_MISMATCH" }
        check(version.transactionVersion == TRANSACTION_VERSION) {
            "SORA2_TRANSACTION_VERSION_MISMATCH"
        }
        check(context.metadataSha256 == METADATA_FILE_SHA256) {
            "SORA2_RUNTIME_SNAPSHOT_IDENTITY_MISMATCH"
        }
        requireReviewedSora2SnapshotIdentity(
            specVersion = version.specVersion,
            metadataSha256 = context.metadataSha256,
            typesSha256 = context.typesSha256,
        )

        // ExtrinsicBuilder resolves calls from this exact hash-verified snapshot, so neither pallet
        // nor call indices are embedded in the application.
        context.snapshot.metadata.module(POLKAMARKT_PALLET)
        return QualifiedSora2MutationRuntime(
            context = context,
            verified = VerifiedSora2Runtime(
                sourceRevision = SOURCE_REVISION,
                specVersion = version.specVersion,
                transactionVersion = version.transactionVersion,
            ),
        )
    }

    /**
     * Requalifies the live runtime immediately before transport and proves that the already-signed
     * bytes used the same chain/runtime identity. Finalized head advancement alone is accepted.
     */
    suspend fun requirePolkamarktMutationRuntimeUnchanged(
        signingRuntime: QualifiedSora2MutationRuntime,
    ): VerifiedSora2Runtime {
        val current = requirePolkamarktMutationRuntimeContext()
        requireSameSora2MutationRuntimeIdentity(
            signing = signingRuntime.context,
            current = current.context,
        )
        return current.verified
    }

    companion object {
        const val SOURCE_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6"
        /** Exact HTTPS JSON-RPC endpoint reviewed in sora2_metadata.manifest.json. */
        const val RUNTIME_RPC_ENDPOINT = "https://ws.mof.sora.org"
        /** Exact selected WebSocket counterpart permitted to carry reviewed SORA2 mutations. */
        const val RUNTIME_WS_ENDPOINT = "wss://ws.mof.sora.org"
        const val SPEC_VERSION = 130
        const val TRANSACTION_VERSION = 130
        const val POLKAMARKT_PALLET = "Polkamarkt"
        const val SORA_MAINNET_GENESIS_HASH =
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val METADATA_FILE_SHA256 =
            "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf"
        const val TYPES_FILE_SHA256 =
            "e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601"
    }
}
