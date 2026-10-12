package jp.co.soramitsu.sora.substrate.runtime

import java.io.IOException
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
 *
 * The finalized height itself controls neither. The two reviewed endpoints finalize the same
 * chain a moment apart, and this check is answered by the second one exactly when the first has
 * just dropped. A head that is a few blocks lower than the one signed against
 * ([Sora2BoundedRuntimeRpcClient.FINALITY_LAG_TOLERANCE_BLOCKS]) is therefore accepted: genesis,
 * runtime versions, metadata and types are still compared exactly, the signing block is by
 * definition inside its validity window, and the node that receives the transaction checks the
 * signed block hash itself. Refusing here failed a Send in the one moment the second endpoint
 * exists for.
 *
 * A head that is further behind comes from an endpoint that no longer follows the chain. Nothing
 * is handed to it; the failure carries an [IOException] so that screens name the network and ask
 * to try again.
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
    if (
        signing.finalizedBlockNumber - current.finalizedBlockNumber >
        Sora2BoundedRuntimeRpcClient.FINALITY_LAG_TOLERANCE_BLOCKS
    ) {
        throw IllegalStateException(
            "SORA2_MUTATION_FINALIZED_HEIGHT_REGRESSION",
            IOException("SORA2_RUNTIME_RPC_ENDPOINT_BEHIND"),
        )
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
 * Release contract generated from the reviewed sora2-network source revision.
 *
 * [SPEC_VERSION], [TRANSACTION_VERSION], [METADATA_FILE_SHA256] and [TYPES_FILE_SHA256] describe
 * the runtime fixture packaged with the app (`sora2_metadata`, runtime 130). They identify that
 * fixture and pin the Polkamarkt mutation path, which stays off in this release. Ordinary
 * transfers, swaps, liquidity, referral and claim transactions are not pinned to them: they are
 * signed with the live mainnet runtime that [RuntimeManager.getMutationRuntimeContext] validates
 * (canonical genesis, SS58 prefix, and spec version and metadata read at one finalized block).
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
        /**
         * First reviewed HTTPS JSON-RPC endpoint (the one named in sora2_metadata.manifest.json).
         * It keeps the state of about the last 256 blocks.
         */
        const val RUNTIME_RPC_ENDPOINT = "https://ws.mof.sora.org"
        /** Exact selected WebSocket counterpart permitted to carry reviewed SORA2 mutations. */
        const val RUNTIME_WS_ENDPOINT = "wss://ws.mof.sora.org"
        /**
         * Second reviewed HTTPS JSON-RPC endpoint: another SORA mainnet node on another host and
         * address (an archive node, one of the default nodes of the app). It serves the same
         * requests as [RUNTIME_RPC_ENDPOINT] under the same checks whenever that endpoint gives
         * no acceptable answer: chain identity, metadata, nonce, fee and status reads, and the
         * state of blocks the first endpoint has already discarded.
         *
         * A signed transaction is offered to it only when the attempt on the other endpoint
         * provably never reached that node ([Sora2BoundedRuntimeRpcClient.submitExtrinsicOnce]).
         * Each endpoint must itself report [SORA_MAINNET_GENESIS_HASH] before it serves anything
         * else in a production build.
         */
        const val SECOND_RUNTIME_RPC_ENDPOINT = "https://mof2.sora.org"
        /** Runtime of the packaged fixture; not a signing requirement (see the class KDoc). */
        const val SPEC_VERSION = 130
        /** Transaction version of the packaged fixture; not a signing requirement. */
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
