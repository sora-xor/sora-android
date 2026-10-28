package jp.co.soramitsu.sora.substrate.runtime

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Sora2RuntimeContractTest {

    @Test
    fun `runtime metadata cannot replace an installed Sora2 wallet prefix`() {
        assertEquals(
            69.toShort(),
            requireSora2Ss58Prefix(BigInteger.valueOf(69L)),
        )
        assertEquals(
            "SORA2_SS58_PREFIX_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                requireSora2Ss58Prefix(BigInteger.valueOf(42L))
            }.message,
        )
        assertThrows(Sora2RuntimeIdentityException::class.java) {
            requireSora2Ss58Prefix(null)
        }
        assertThrows(Sora2RuntimeIdentityException::class.java) {
            // `BigInteger.toShort()` would truncate this to 69.
            requireSora2Ss58Prefix(BigInteger.valueOf(65_605L))
        }
    }

    @Test
    fun `generation manifest rejects missing or mixed runtime cache files`() {
        val oldMetadata = "0x0102\n"
        val oldTypes = "{\"runtime_id\":42}\n"
        val oldManifest = createRuntimeCacheManifest(
            oldMetadata,
            oldTypes,
            130,
            Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        )
        val published = requirePublishedRuntimeCache(oldManifest, oldMetadata, oldTypes)

        assertEquals(130, published.runtimeVersion)
        assertEquals(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, published.genesisHash)
        assertEquals(oldMetadata.runtimeMetadataSha256(), published.metadataSha256)
        assertEquals(oldTypes.rawSha256(), published.typesSha256)
        assertEquals(
            "SORA2_RUNTIME_CACHE_METADATA_HASH_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(oldManifest, "0x0304", oldTypes)
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_TYPES_HASH_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(oldManifest, oldMetadata, "{}")
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_TYPES_MISSING",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(oldManifest, oldMetadata, null)
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_GENERATION_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(
                    oldManifest.copy(generation = "unbound-generation"),
                    oldMetadata,
                    oldTypes,
                )
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_GENERATION_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(
                    oldManifest.copy(genesisHash = "0x${"ab".repeat(32)}"),
                    oldMetadata,
                    oldTypes,
                )
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_MANIFEST_INVALID",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(
                    oldManifest.copy(genesisHash = null),
                    oldMetadata,
                    oldTypes,
                )
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_GENERATION_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(
                    oldManifest.copy(runtimeVersion = 131),
                    oldMetadata,
                    oldTypes,
                )
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_FILE_IDENTITY_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requirePublishedRuntimeCache(
                    oldManifest.copy(typesFile = "../types.json"),
                    oldMetadata,
                    oldTypes,
                )
            }.message,
        )
    }

    @Test
    fun `runtime metadata identity canonicalizes asset newline without weakening type bytes`() {
        assertEquals("0xABCD\n".runtimeMetadataSha256(), "0xabcd".runtimeMetadataSha256())
        assertEquals(
            "SORA2_RUNTIME_TYPES_IDENTITY_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                requireReviewedSora2SnapshotIdentity(
                    specVersion = Sora2RuntimeContract.SPEC_VERSION,
                    metadataSha256 = Sora2RuntimeContract.METADATA_FILE_SHA256,
                    typesSha256 = "0".repeat(64),
                )
            }.message,
        )
        assertEquals(
            "SORA2_RUNTIME_SNAPSHOT_VERSION_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                requireReviewedSora2SnapshotIdentity(
                    specVersion = 131,
                    metadataSha256 = Sora2RuntimeContract.METADATA_FILE_SHA256,
                    typesSha256 = Sora2RuntimeContract.TYPES_FILE_SHA256,
                )
            }.message,
        )
    }

    @Test
    fun `malformed or partial legacy runtime cache falls back only to bundled pair`() {
        val bundledMetadata = "0x0102\n"
        val bundledTypes = "{\"runtime_id\":130}\n"

        assertTrue(
            legacyRuntimeCacheMatchesBundled(
                bundledMetadata,
                bundledTypes,
                bundledMetadata,
                bundledTypes,
            )
        )
        assertFalse(
            legacyRuntimeCacheMatchesBundled(
                "not-runtime-metadata",
                bundledTypes,
                bundledMetadata,
                bundledTypes,
            )
        )
        assertFalse(
            legacyRuntimeCacheMatchesBundled(
                bundledMetadata,
                null,
                bundledMetadata,
                bundledTypes,
            )
        )
        assertFalse(
            legacyRuntimeCacheMatchesBundled(
                bundledMetadata,
                "{}",
                bundledMetadata,
                bundledTypes,
            )
        )
    }

    @Test
    fun `runtime payload admission rejects oversize and malformed UTF8 before caching`() {
        assertEquals(
            "a🙂",
            requireRuntimePayloadWithinLimit(
                content = "a🙂",
                maxBytes = 5,
                errorCode = "TEST_RUNTIME_PAYLOAD_TOO_LARGE",
            ),
        )
        assertEquals(
            "TEST_RUNTIME_PAYLOAD_TOO_LARGE",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireRuntimePayloadWithinLimit(
                    content = "a🙂",
                    maxBytes = 4,
                    errorCode = "TEST_RUNTIME_PAYLOAD_TOO_LARGE",
                )
            }.message,
        )
        assertEquals(
            "TEST_RUNTIME_PAYLOAD_TOO_LARGE_UTF8_INVALID",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireRuntimePayloadWithinLimit(
                    content = 0xD800.toChar().toString(),
                    maxBytes = 16,
                    errorCode = "TEST_RUNTIME_PAYLOAD_TOO_LARGE",
                )
            }.message,
        )
    }

    @Test
    fun `unreadable legacy runtime cache permits only bundled fallback`() {
        assertTrue(
            mayAdmitLegacyRuntimeCache(
                RuntimeCacheReadState.PRESENT,
                RuntimeCacheReadState.PRESENT,
            )
        )
        assertFalse(
            mayAdmitLegacyRuntimeCache(
                RuntimeCacheReadState.UNREADABLE,
                RuntimeCacheReadState.PRESENT,
            )
        )
        assertFalse(
            mayAdmitLegacyRuntimeCache(
                RuntimeCacheReadState.PRESENT,
                RuntimeCacheReadState.UNREADABLE,
            )
        )
        assertFalse(
            mayAdmitLegacyRuntimeCache(
                RuntimeCacheReadState.ABSENT,
                RuntimeCacheReadState.PRESENT,
            )
        )
    }

    @Test
    fun `runtime generation pruning is bounded and protects active or unrelated files`() {
        val genesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH.drop(2)
        fun generation(version: Int, suffix: String): String =
            "sora2_runtime_cache_v2-$version-$genesis-${suffix.repeat(64)}-${suffix.repeat(64)}"

        val active = generation(130, "a")
        val old = generation(129, "b")
        val older = generation(128, "c")
        val candidates = listOf(
            "$active.metadata",
            "$active.types.json",
            "$old.metadata",
            "$old.types.json",
            "$older.metadata",
            "$older.types.json",
            "sora2_runtime_cache_manifest_v2.json",
            "$old.metadata.bak",
            "$old.metadata.new",
            "unrelated-wallet-cache",
            "sora2_runtime_cache_v2-malformed.metadata",
            "$old.metadata",
        )

        assertEquals(
            listOf("$older.metadata", "$older.types.json"),
            selectUnreachableRuntimeGenerationFiles(
                fileNames = candidates,
                activeMetadataFile = "$active.metadata",
                activeTypesFile = "$active.types.json",
                maxFiles = 2,
            ),
        )
        assertTrue(
            selectUnreachableRuntimeGenerationFiles(
                fileNames = candidates,
                activeMetadataFile = "$active.metadata",
                activeTypesFile = "$active.types.json",
                maxFiles = 4,
            ).none {
                it.startsWith(active) ||
                    it.endsWith(".bak") ||
                    it.endsWith(".new") ||
                    it == "unrelated-wallet-cache" ||
                    it == "sora2_runtime_cache_v2-malformed.metadata"
            }
        )
        assertEquals(
            "SORA2_RUNTIME_CACHE_PRUNE_LIMIT_INVALID",
            assertThrows(IllegalArgumentException::class.java) {
                selectUnreachableRuntimeGenerationFiles(
                    fileNames = candidates,
                    activeMetadataFile = "$active.metadata",
                    activeTypesFile = "$active.types.json",
                    maxFiles = 5,
                )
            }.message,
        )
    }

    @Test
    fun `mutation runtime identity rejects upgrades and downgrades symmetrically`() {
        val genesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
        val metadata = "1".repeat(64)

        assertEquals(
            "SORA2_MUTATION_RUNTIME_VERSION_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireMutationRuntimeIdentity(
                    130,
                    genesis,
                    metadata,
                    131,
                    genesis,
                    metadata,
                )
            }.message,
        )
        assertEquals(
            "SORA2_MUTATION_RUNTIME_VERSION_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireMutationRuntimeIdentity(
                    130,
                    genesis,
                    metadata,
                    129,
                    genesis,
                    metadata,
                )
            }.message,
        )
        assertEquals(
            "SORA2_MUTATION_GENESIS_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireMutationRuntimeIdentity(
                    130,
                    genesis,
                    metadata,
                    130,
                    "0x${"ab".repeat(32)}",
                    metadata,
                )
            }.message,
        )
        assertEquals(
            "SORA2_MUTATION_METADATA_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireMutationRuntimeIdentity(
                    130,
                    genesis,
                    metadata,
                    130,
                    genesis,
                    "2".repeat(64),
                )
            }.message,
        )
    }

    @Test
    fun `qualified signing context accepts finalized advance but rejects every identity drift`() {
        val signing = mutationContext(finalizedHash = "0x${"11".repeat(32)}")
        val current = mutationContext(finalizedHash = "0x${"22".repeat(32)}")

        requireSameSora2MutationRuntimeIdentity(signing, current)

        assertIdentityDrift(
            expectedMessage = "SORA2_MUTATION_GENESIS_DRIFT",
            signing = signing,
            current = current.copy(genesisHash = "0x${"ab".repeat(32)}"),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_MUTATION_SPEC_VERSION_DRIFT",
            signing = signing,
            current = current.copy(runtimeVersion = runtimeVersion(131, 130)),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_MUTATION_TRANSACTION_VERSION_DRIFT",
            signing = signing,
            current = current.copy(runtimeVersion = runtimeVersion(130, 131)),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_MUTATION_METADATA_DRIFT",
            signing = signing,
            current = current.copy(metadataSha256 = "1".repeat(64)),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_MUTATION_TYPES_DRIFT",
            signing = signing,
            current = current.copy(typesSha256 = "2".repeat(64)),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_CURRENT_FINALIZED_HASH_INVALID",
            signing = signing,
            current = current.copy(finalizedHash = "not-a-finalized-hash"),
        )
        assertIdentityDrift(
            expectedMessage = "SORA2_SIGNING_FINALIZED_HASH_INVALID",
            signing = signing.copy(finalizedHash = "not-a-finalized-hash"),
            current = current,
        )
    }

    @Test
    fun `production runtime cache and mutations are bound to canonical genesis`() {
        assertEquals(
            Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            requireCanonicalProductionGenesis(
                "0x${Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH.drop(2).uppercase()}",
                enforceCanonicalMainnet = true,
            ),
        )
        assertEquals(
            "SORA2_GENESIS_HASH_MISMATCH",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                requireCanonicalProductionGenesis(
                    "0x${"ab".repeat(32)}",
                    enforceCanonicalMainnet = true,
                )
            }.message,
        )
    }

    @Test
    fun `wrong live genesis fails before runtime metadata can authorize a mutation`() = runTest {
        val runtimeManager = mockk<RuntimeManager>(relaxed = true)
        val runtimeContext = mockk<Sora2MutationRuntimeContext>()
        coEvery { runtimeManager.getMutationRuntimeContext() } returns runtimeContext
        every { runtimeContext.genesisHash } returns "0x${"ab".repeat(32)}"
        val contract = Sora2RuntimeContract(runtimeManager)

        val error = captureFailure {
            contract.requirePolkamarktMutationRuntime()
        }

        assertEquals("SORA2_GENESIS_HASH_MISMATCH", error.message)
        coVerify(exactly = 1) { runtimeManager.getMutationRuntimeContext() }
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Exception) {
            return error
        }
        throw AssertionError("Expected the live network identity gate to fail")
    }

    private fun mutationContext(
        finalizedHash: String,
    ) = Sora2MutationRuntimeContext(
        snapshot = mockk<RuntimeSnapshot>(),
        runtimeVersion = runtimeVersion(130, 130),
        genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        finalizedHash = finalizedHash,
        metadataSha256 = Sora2RuntimeContract.METADATA_FILE_SHA256,
        typesSha256 = Sora2RuntimeContract.TYPES_FILE_SHA256,
    )

    private fun runtimeVersion(specVersion: Int, transactionVersion: Int): RuntimeVersion {
        val version = mockk<RuntimeVersion>()
        every { version.specVersion } returns specVersion
        every { version.transactionVersion } returns transactionVersion
        return version
    }

    private fun assertIdentityDrift(
        expectedMessage: String,
        signing: Sora2MutationRuntimeContext,
        current: Sora2MutationRuntimeContext,
    ) {
        assertEquals(
            expectedMessage,
            assertThrows(IllegalStateException::class.java) {
                requireSameSora2MutationRuntimeIdentity(signing, current)
            }.message,
        )
    }
}
