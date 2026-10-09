package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Signing must follow the live mainnet runtime. These tests drive the real RuntimeManager with
 * the packaged runtime bytes standing in for "whatever the node serves now" and a live runtime
 * version that is not the packaged one (spec 133, transaction 131, as on mainnet in Oct 2026).
 */
class RuntimeManagerLiveRuntimeTest {

    private val bundledMetadata = repoFile("common/src/production/assets/sora2_metadata")
    private val bundledTypes = repoFile("common/src/production/assets/types_scalecodec_mobile.json")
    private val cache = mutableMapOf<String, String>()

    // The constructor starts a display prefetch on the application scope. Give it a scheduler
    // that is never advanced, so each test controls every runtime load itself.
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(TestCoroutineScheduler()))

    private val fileManager = mockk<FileManager>().also { files ->
        every { files.readAssetFile("sora2_metadata", any()) } returns bundledMetadata
        every { files.readAssetFile("types_scalecodec_mobile.json", any()) } returns bundledTypes
        every { files.readInternalCacheFile(any(), any()) } answers { cache[firstArg()] }
        every { files.readInternalCacheFileAtomically(any(), any()) } answers { cache[firstArg()] }
        every { files.writeInternalCacheFileAtomically(any(), any(), any()) } answers {
            cache[firstArg()] = secondArg()
        }
        every { files.listInternalCacheFileNames(any(), any()) } answers {
            cache.keys.filter { it.startsWith(firstArg<String>()) }
        }
        every { files.deleteInternalCacheFile(any()) } answers { cache.remove(firstArg()) != null }
    }
    private val preferences = mockk<SoraPreferences>(relaxed = true).also {
        coEvery { it.getInt(any(), any()) } returns 1
    }
    private val rpc = mockk<Sora2BoundedRuntimeRpcClient>()
    private val connection = mockk<ConnectionManager>(relaxed = true)
    private val http = mockk<BoundedHttpTextClient>()
    private val config = mockk<SoraConfigManager>().also {
        coEvery { it.getSubstrateTypesUrl() } returns CONFIG_TYPES_URL
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun TestScope.runtimeManager(): RuntimeManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coroutineManager = mockk<CoroutineManager>().also {
            every { it.applicationScope } returns scope
            every { it.io } returns dispatcher
        }
        return RuntimeManager(
            fileManager = fileManager,
            gson = Gson(),
            soraPreferences = preferences,
            sora2RuntimeRpcClient = rpc,
            connectionManager = connection,
            boundedHttpTextClient = http,
            coroutineManager = coroutineManager,
            soraConfigManager = config,
        )
    }

    private fun liveChain(specVersion: Int = 133, transactionVersion: Int = 131) {
        coEvery { rpc.getFinalizedIdentity() } returns Sora2FinalizedRuntimeIdentity(
            genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            finalizedHash = FINALIZED_HASH,
            runtimeVersion = RuntimeVersion(specVersion, transactionVersion),
            finalizedBlockNumber = 27_973_000L,
        )
        coEvery { rpc.getMetadataAtFinalized(FINALIZED_HASH) } returns bundledMetadata
    }

    @Test
    fun `signing context follows a newer live runtime than the packaged one`() = runTest {
        liveChain(specVersion = 133, transactionVersion = 131)
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val manager = runtimeManager()

        val context = manager.getMutationRuntimeContext()

        assertEquals(133, context.runtimeVersion.specVersion)
        assertEquals(131, context.runtimeVersion.transactionVersion)
        assertEquals(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, context.genesisHash)
        assertEquals(FINALIZED_HASH, context.finalizedHash)
        assertEquals(bundledMetadata.runtimeMetadataSha256(), context.metadataSha256)
        assertEquals(133, manager.getRuntimeSpecVersion())
        coVerify(exactly = 1) { connection.requireReviewedSora2MutationTransport() }
        // Types come from the remote config URL that 3.8.6.3 used, never from the PI indexer.
        coVerify(exactly = 1) { http.getUtf8(CONFIG_TYPES_URL, any()) }

        // The second mutation in the same runtime reuses the published snapshot.
        val again = manager.getMutationRuntimeContext()
        assertSame(context.snapshot, again.snapshot)
        coVerify(exactly = 1) { http.getUtf8(CONFIG_TYPES_URL, any()) }
    }

    @Test
    fun `read runtime refreshes to the live runtime when the remote types are unreachable`() =
        runTest {
            liveChain(specVersion = 140, transactionVersion = 135)
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } throws IOException("offline")
            val manager = runtimeManager()

            manager.getRuntimeSnapshot()

            assertEquals(140, manager.getRuntimeSpecVersion())
            assertEquals(bundledTypes.rawSha256(), manager.getRuntimeTypesSha256())
            // The refreshed generation is published, so the next process starts from it.
            assertNotNull(cache["sora2_runtime_cache_manifest_v2.json"])
        }

    @Test
    fun `unusable remote types fall back to the packaged aliases`() = runTest {
        liveChain()
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns "{\"runtime_id\": 1, \"types\": {}}"
        coEvery { config.getSubstrateTypesUrl() } returns CONFIG_TYPES_URL

        val context = runtimeManager().getMutationRuntimeContext()

        assertEquals(bundledTypes.rawSha256(), context.typesSha256)
        assertEquals(133, context.runtimeVersion.specVersion)
    }

    @Test
    fun `pending submissions signed with the live runtime can be recovered`() = runTest {
        liveChain()
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val manager = runtimeManager()
        val context = manager.getMutationRuntimeContext()

        val recovered = manager.getReviewedRecoveryRuntimeSnapshotOrNull(
            genesisHash = context.genesisHash,
            specVersion = context.runtimeVersion.specVersion,
            transactionVersion = context.runtimeVersion.transactionVersion,
            metadataSha256 = context.metadataSha256,
            typesSha256 = context.typesSha256,
        )
        assertSame(context.snapshot, recovered)

        // A fresh process finds the same runtime in the published cache generation.
        val restarted = runtimeManager().getReviewedRecoveryRuntimeSnapshotOrNull(
            genesisHash = context.genesisHash,
            specVersion = context.runtimeVersion.specVersion,
            transactionVersion = context.runtimeVersion.transactionVersion,
            metadataSha256 = context.metadataSha256,
            typesSha256 = context.typesSha256,
        )
        assertNotNull(restarted)

        // An unknown runtime stays unresolved instead of being decoded with the wrong metadata.
        assertNull(
            manager.getReviewedRecoveryRuntimeSnapshotOrNull(
                genesisHash = context.genesisHash,
                specVersion = 134,
                transactionVersion = 131,
                metadataSha256 = context.metadataSha256,
                typesSha256 = context.typesSha256,
            )
        )
    }

    private companion object {
        const val CONFIG_TYPES_URL =
            "https://config.polkaswap2.io/prod/substrate_types_mobile_android.json"
        val FINALIZED_HASH = "0x${"5a".repeat(32)}"

        fun repoFile(path: String): String {
            val candidates = listOf(File("../$path"), File(path))
            return candidates.first(File::isFile).readText()
        }
    }
}
