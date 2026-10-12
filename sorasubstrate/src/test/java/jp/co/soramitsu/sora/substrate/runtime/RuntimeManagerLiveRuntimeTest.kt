package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.io.IOException
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.ExtrinsicMetadataV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.PortableType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RegistryType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.SignedExtensionMetadataV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefEnum
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
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
import org.junit.Assert.assertTrue
import org.junit.Before
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
    private val http = mockk<BoundedHttpTextClient>()

    /** Runtimes the pending-submission journal still needs; null = journal unreadable. */
    private var retainedRuntimes: Set<Sora2RecoveryRuntimeIdentity>? = emptySet()
    private val config = mockk<SoraConfigManager>().also {
        coEvery { it.getSubstrateTypesUrl() } returns CONFIG_TYPES_URL
    }

    @Before
    fun setUp() {
        // Spy only: calls still pass through, so tests can check what was recorded.
        mockkObject(FirebaseWrapper)
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(FirebaseWrapper)
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
            boundedHttpTextClient = http,
            coroutineManager = coroutineManager,
            soraConfigManager = config,
            recoveryRuntimeRetention = { retainedRuntimes },
        )
    }

    private fun liveChain(
        specVersion: Int = 133,
        transactionVersion: Int = 131,
        finalizedHash: String = FINALIZED_HASH,
    ) {
        coEvery { rpc.getFinalizedIdentity() } returns Sora2FinalizedRuntimeIdentity(
            genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            finalizedHash = finalizedHash,
            runtimeVersion = RuntimeVersion(specVersion, transactionVersion),
            finalizedBlockNumber = 27_973_000L,
        )
        coEvery { rpc.getMetadataAtFinalized(finalizedHash) } returns bundledMetadata
    }

    private fun Sora2MutationRuntimeContext.recoveryIdentity() = Sora2RecoveryRuntimeIdentity(
        genesisHash = genesisHash,
        specVersion = runtimeVersion.specVersion,
        metadataSha256 = metadataSha256,
        typesSha256 = typesSha256,
    )

    private fun generationFiles(context: Sora2MutationRuntimeContext): List<String> =
        checkNotNull(runtimeCacheGenerationFilesOrNull(context.recoveryIdentity()))
            .toList()

    private suspend fun RuntimeManager.recover(
        context: Sora2MutationRuntimeContext,
        eraBirthBlockHash: String? = null,
    ) = getReviewedRecoveryRuntimeSnapshotOrNull(
        genesisHash = context.genesisHash,
        specVersion = context.runtimeVersion.specVersion,
        transactionVersion = context.runtimeVersion.transactionVersion,
        metadataSha256 = context.metadataSha256,
        typesSha256 = context.typesSha256,
        eraBirthBlockHash = eraBirthBlockHash,
    )

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
        // RuntimeManager has no WebSocket dependency: the node the app's socket is connected to
        // (for example mof2 after an automatic switch) cannot block or change a mutation.
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

    @Test
    fun `a submission signed before a runtime upgrade is still decoded after the upgrade`() =
        runTest {
            liveChain(specVersion = 133, transactionVersion = 131)
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val signing = runtimeManager().getMutationRuntimeContext()
            // The journal row for the submission is still unresolved.
            retainedRuntimes = setOf(signing.recoveryIdentity())

            // Mainnet upgrades; the next start refreshes, publishes 134 and prunes the cache.
            liveChain(specVersion = 134, transactionVersion = 131, finalizedHash = UPGRADED_HASH)
            val upgraded = runtimeManager()
            assertEquals(134, upgraded.getMutationRuntimeContext().runtimeVersion.specVersion)
            generationFiles(signing).forEach { assertNotNull("$it was pruned", cache[it]) }

            // A new process (the recovery worker) still finds the runtime 133 snapshot locally.
            val recovered = runtimeManager().recover(signing, eraBirthBlockHash = FINALIZED_HASH)
            assertNotNull(recovered)
            coVerify(exactly = 0) { rpc.getHistoricMetadataForStatus(any()) }
        }

    @Test
    fun `an evicted signing runtime is rebuilt from the chain and checked against the journal`() =
        runTest {
            liveChain(specVersion = 133, transactionVersion = 131)
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val signing = runtimeManager().getMutationRuntimeContext()
            liveChain(specVersion = 134, transactionVersion = 131, finalizedHash = UPGRADED_HASH)
            runtimeManager().getMutationRuntimeContext()
            // The OS cleared the cache directory (or the generation was pruned earlier).
            generationFiles(signing).forEach { cache.remove(it) }
            val restarted = runtimeManager()
            assertNull(restarted.recover(signing))

            coEvery { rpc.getHistoricRuntimeVersionForStatus(FINALIZED_HASH) } returns
                RuntimeVersion(133, 131)
            coEvery { rpc.getHistoricMetadataForStatus(FINALIZED_HASH) } returns bundledMetadata

            assertNotNull(restarted.recover(signing, eraBirthBlockHash = FINALIZED_HASH))
            // Saved again for later passes, outside the manifest.
            generationFiles(signing).forEach { assertNotNull(cache[it]) }
            assertTrue(
                cache.getValue("sora2_runtime_cache_manifest_v2.json").contains("-134-")
            )
        }

    @Test
    fun `a rebuilt runtime is rejected unless it matches the journaled identity`() = runTest {
        liveChain(specVersion = 133, transactionVersion = 131)
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val signing = runtimeManager().getMutationRuntimeContext()
        generationFiles(signing).forEach { cache.remove(it) }
        liveChain(specVersion = 134, transactionVersion = 131, finalizedHash = UPGRADED_HASH)
        val restarted = runtimeManager()
        restarted.getRuntimeSnapshot()

        // Wrong runtime version at the era-birth block.
        coEvery { rpc.getHistoricRuntimeVersionForStatus(FINALIZED_HASH) } returns
            RuntimeVersion(134, 131)
        coEvery { rpc.getHistoricMetadataForStatus(FINALIZED_HASH) } returns bundledMetadata
        assertNull(restarted.recover(signing, eraBirthBlockHash = FINALIZED_HASH))

        // Right version, but the metadata bytes differ from the journaled hash.
        coEvery { rpc.getHistoricRuntimeVersionForStatus(FINALIZED_HASH) } returns
            RuntimeVersion(133, 131)
        coEvery { rpc.getHistoricMetadataForStatus(FINALIZED_HASH) } returns
            bundledMetadata.trimEnd() + "00"
        assertNull(restarted.recover(signing, eraBirthBlockHash = FINALIZED_HASH))

        // The node is unreachable: no snapshot, no crash; the row simply stays unresolved.
        coEvery { rpc.getHistoricRuntimeVersionForStatus(FINALIZED_HASH) } throws
            IOException("offline")
        assertNull(restarted.recover(signing, eraBirthBlockHash = FINALIZED_HASH))
    }

    @Test
    fun `cache is not pruned when the journal cannot be read`() = runTest {
        liveChain(specVersion = 133, transactionVersion = 131)
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val signing = runtimeManager().getMutationRuntimeContext()
        retainedRuntimes = null

        liveChain(specVersion = 134, transactionVersion = 131, finalizedHash = UPGRADED_HASH)
        runtimeManager().getMutationRuntimeContext()

        generationFiles(signing).forEach { assertNotNull(cache[it]) }
    }

    @Test
    fun `a full disk does not stop the live runtime from loading or signing`() = runTest {
        liveChain(specVersion = 133, transactionVersion = 131)
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        every { fileManager.writeInternalCacheFileAtomically(any(), any(), any()) } throws
            IOException("ENOSPC")
        val manager = runtimeManager()

        // A required refresh (live 133 vs packaged 130) whose save fails still serves 133.
        manager.getRuntimeSnapshot()
        assertEquals(133, manager.getRuntimeSpecVersion())
        assertNull(cache["sora2_runtime_cache_manifest_v2.json"])
        val context = manager.getMutationRuntimeContext()
        assertEquals(133, context.runtimeVersion.specVersion)
        // The in-memory runtime is reused; the metadata is not downloaded again for each read.
        manager.getRuntimeSnapshot()
        coVerify(exactly = 2) { rpc.getMetadataAtFinalized(FINALIZED_HASH) }
    }

    // ---- Upgrading from 3.8.6.3 ----
    // 3.8.6.3 kept the runtime it last used in two unversioned cache files plus an int preference,
    // and saved whatever the node served. A phone that was online in October 2026 therefore holds
    // spec-133 metadata and types that 3.9.0 has no reviewed hash for, and no v2 manifest.

    /** Stand-ins for what 3.8.6.3 saved: valid hex / JSON, but not the packaged pair. */
    private val legacyMetadata = bundledMetadata.trimEnd() + "0000\n"
    private val legacyTypes = bundledTypes.trimEnd() + "\n\n"

    private fun seed3863Cache(
        metadata: String? = legacyMetadata,
        types: String? = legacyTypes,
        storedRuntimeVersion: Int = 133,
    ) {
        metadata?.let { cache[LEGACY_METADATA_FILE] = it }
        types?.let { cache[LEGACY_TYPES_FILE] = it }
        coEvery { preferences.getInt(RUNTIME_VERSION_PREF, any()) } returns storedRuntimeVersion
    }

    private fun offlineChain() {
        coEvery { rpc.getFinalizedIdentity() } throws IOException("offline")
    }

    private fun verifyLegacyMismatchRecorded(times: Int) {
        verify(exactly = times) {
            FirebaseWrapper.log("SORA2_RUNTIME_LEGACY_CACHE_IDENTITY_MISMATCH")
        }
    }

    private suspend fun RuntimeManager.assertPackagedRuntime(case: String = "") {
        getRuntimeSnapshot()
        assertEquals(case, Sora2RuntimeContract.SPEC_VERSION, getRuntimeSpecVersion())
        assertEquals(case, bundledMetadata.runtimeMetadataSha256(), getRuntimeMetadataSha256())
        assertEquals(case, bundledTypes.rawSha256(), getRuntimeTypesSha256())
    }

    @Test
    fun `offline after upgrading from 3_8_6_3 the packaged runtime is used, not the old cache`() =
        runTest {
            seed3863Cache()
            offlineChain()
            val manager = runtimeManager()

            // Neither the old files nor the stored "133" are trusted for decoding.
            manager.assertPackagedRuntime()
            verifyLegacyMismatchRecorded(times = 1)
            // Offline, the packaged runtime is never signing authority.
            val signing = runCatching { manager.getMutationRuntimeContext() }
            assertTrue(signing.exceptionOrNull() is IOException)
            // 3.8.6.3's files are left exactly as they were; no manifest is invented.
            assertEquals(legacyMetadata, cache[LEGACY_METADATA_FILE])
            assertEquals(legacyTypes, cache[LEGACY_TYPES_FILE])
            assertNull(cache[MANIFEST_FILE])
        }

    @Test
    fun `online after upgrading from 3_8_6_3 the runtime refreshes to live 133 and signs`() =
        runTest {
            seed3863Cache()
            liveChain(specVersion = 133, transactionVersion = 131)
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val manager = runtimeManager()

            // App start: the read runtime moves from the packaged 130 to the live 133.
            manager.getRuntimeSnapshot()
            assertEquals(133, manager.getRuntimeSpecVersion())
            val manifest = checkNotNull(cache[MANIFEST_FILE]) { "no v2 manifest published" }
            assertTrue(manifest.contains("-133-"))
            coVerify { preferences.putInt(RUNTIME_VERSION_PREF, 133) }

            // First send: the live-validated context, reusing the refreshed snapshot.
            val context = manager.getMutationRuntimeContext()
            assertEquals(133, context.runtimeVersion.specVersion)
            assertEquals(131, context.runtimeVersion.transactionVersion)
            assertEquals(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, context.genesisHash)
            assertEquals(bundledMetadata.runtimeMetadataSha256(), context.metadataSha256)
            coVerify(exactly = 1) { http.getUtf8(CONFIG_TYPES_URL, any()) }
            assertEquals(legacyMetadata, cache[LEGACY_METADATA_FILE])
            assertEquals(legacyTypes, cache[LEGACY_TYPES_FILE])

            // The next process starts from the v2 generation even offline.
            offlineChain()
            val restarted = runtimeManager()
            restarted.getRuntimeSnapshot()
            assertEquals(133, restarted.getRuntimeSpecVersion())
            assertEquals(context.typesSha256, restarted.getRuntimeTypesSha256())
        }

    @Test
    fun `a 3_8_6_3 cache that equals the packaged pair is admitted`() = runTest {
        // 3.8.6.3 wrote the metadata without the trailing newline; the canonical hash is equal.
        seed3863Cache(
            metadata = bundledMetadata.trimEnd(),
            types = bundledTypes,
            storedRuntimeVersion = Sora2RuntimeContract.SPEC_VERSION,
        )
        offlineChain()

        runtimeManager().assertPackagedRuntime()

        verify { fileManager.readInternalCacheFile(LEGACY_METADATA_FILE, any()) }
        verify { fileManager.readInternalCacheFile(LEGACY_TYPES_FILE, any()) }
        verifyLegacyMismatchRecorded(times = 0)
    }

    @Test
    fun `a damaged 3_8_6_3 cache falls back to the packaged pair and still reaches live 133`() =
        runTest {
            val damage: List<Pair<String, () -> Unit>> = listOf(
                "oversize metadata" to {
                    every { fileManager.readInternalCacheFile(LEGACY_METADATA_FILE, any()) } throws
                        IOException("UTF8_PAYLOAD_TOO_LARGE:$LEGACY_METADATA_FILE")
                },
                "unreadable types" to {
                    every { fileManager.readInternalCacheFile(LEGACY_TYPES_FILE, any()) } throws
                        IOException("INTERNAL_CACHE_FILE_NOT_REGULAR:$LEGACY_TYPES_FILE")
                },
                "types evicted" to { cache.remove(LEGACY_TYPES_FILE) },
                "empty types" to { cache[LEGACY_TYPES_FILE] = "" },
                "metadata is not hex" to { cache[LEGACY_METADATA_FILE] = "<html>busy</html>" },
                "packaged metadata with foreign types" to {
                    cache[LEGACY_METADATA_FILE] = bundledMetadata
                },
            )
            damage.forEach { (case, apply) ->
                cache.clear()
                every { fileManager.readInternalCacheFile(any(), any()) } answers {
                    cache[firstArg()]
                }
                seed3863Cache()
                apply()

                offlineChain()
                runtimeManager().assertPackagedRuntime(case)

                liveChain(specVersion = 133, transactionVersion = 131)
                coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
                val online = runtimeManager()
                online.getRuntimeSnapshot()
                assertEquals(case, 133, online.getRuntimeSpecVersion())
                assertEquals(
                    case,
                    133,
                    online.getMutationRuntimeContext().runtimeVersion.specVersion,
                )
                assertNotNull(case, cache[MANIFEST_FILE])
            }
        }

    @Test
    fun `metadata is downloaded once per runtime and not for every quote or signature`() =
        runTest {
            liveChain()
            coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns CODE_HASH
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val manager = runtimeManager()

            val first = manager.getMutationRuntimeContext()
            // Later quotes and signatures see newer finalized blocks of the same runtime.
            liveChain(finalizedHash = UPGRADED_HASH)
            val second = manager.getMutationRuntimeContext()
            val third = manager.getMutationRuntimeContext()

            assertSame(first.snapshot, second.snapshot)
            assertSame(first.snapshot, third.snapshot)
            assertEquals(UPGRADED_HASH, second.finalizedHash)
            assertEquals(first.metadataSha256, second.metadataSha256)
            assertEquals(first.typesSha256, third.typesSha256)
            assertEquals(133, third.runtimeVersion.specVersion)
            // One download of 1.25 MB instead of three; each later context costs one 32-byte
            // answer.
            coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }
            coVerify(exactly = 3) { rpc.getRuntimeCodeHashAtFinalized(any()) }
        }

    @Test
    fun `the download that refreshed the runtime for reading also serves the first quote`() =
        runTest {
            // First start after a chain upgrade: the packaged runtime is 130, mainnet is at 133.
            liveChain(specVersion = 133, transactionVersion = 131)
            coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns CODE_HASH
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val manager = runtimeManager()

            manager.getRuntimeSnapshot()
            assertEquals(133, manager.getRuntimeSpecVersion())
            coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }

            val context = manager.getMutationRuntimeContext()

            assertEquals(133, context.runtimeVersion.specVersion)
            assertSame(manager.getRuntimeSnapshot(), context.snapshot)
            coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }
        }

    @Test
    fun `new runtime code or a new version always reads the metadata again`() = runTest {
        liveChain()
        coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns CODE_HASH
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val manager = runtimeManager()
        manager.getMutationRuntimeContext()
        coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }

        // Other runtime code under the same version numbers is another runtime.
        coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns OTHER_CODE_HASH
        manager.getMutationRuntimeContext()
        coVerify(exactly = 2) { rpc.getMetadataAtFinalized(any()) }
        manager.getMutationRuntimeContext()
        coVerify(exactly = 2) { rpc.getMetadataAtFinalized(any()) }

        // The same code reported with another transaction version is not trusted either.
        liveChain(specVersion = 133, transactionVersion = 132)
        manager.getMutationRuntimeContext()
        coVerify(exactly = 3) { rpc.getMetadataAtFinalized(any()) }

        // A runtime upgrade: new version, new code.
        liveChain(specVersion = 134, transactionVersion = 132, finalizedHash = UPGRADED_HASH)
        coEvery { rpc.getRuntimeCodeHashAtFinalized(UPGRADED_HASH) } returns CODE_HASH
        val upgraded = manager.getMutationRuntimeContext()
        assertEquals(134, upgraded.runtimeVersion.specVersion)
        coVerify(exactly = 4) { rpc.getMetadataAtFinalized(any()) }
    }

    @Test
    fun `without a code hash the metadata is compared by its own hash as before`() = runTest {
        liveChain()
        coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } throws IOException("offline")
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val manager = runtimeManager()

        val first = manager.getMutationRuntimeContext()
        val second = manager.getMutationRuntimeContext()

        assertSame(first.snapshot, second.snapshot)
        coVerify(exactly = 2) { rpc.getMetadataAtFinalized(any()) }
        assertNull(cache[CODE_BINDING_FILE])
    }

    @Test
    fun `the next app start does not download the metadata again`() = runTest {
        liveChain()
        coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns CODE_HASH
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
        val firstProcess = runtimeManager().getMutationRuntimeContext()
        coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }
        assertNotNull(cache[CODE_BINDING_FILE])

        // A new process: the saved runtime is loaded for reading, then a fee is quoted.
        coEvery { preferences.getInt(RUNTIME_VERSION_PREF, any()) } returns 133
        val restarted = runtimeManager()
        restarted.getRuntimeSnapshot()
        val context = restarted.getMutationRuntimeContext()

        assertEquals(firstProcess.metadataSha256, context.metadataSha256)
        assertEquals(133, context.runtimeVersion.specVersion)
        coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }

        // A saved binding that is about other metadata is ignored, not trusted.
        cache[CODE_BINDING_FILE] = checkNotNull(cache[CODE_BINDING_FILE])
            .replace(firstProcess.metadataSha256, "0".repeat(64))
        val foreign = runtimeManager()
        foreign.getRuntimeSnapshot()
        foreign.getMutationRuntimeContext()
        coVerify(exactly = 2) { rpc.getMetadataAtFinalized(any()) }
    }

    @Test
    fun `mainnet runtimes 130 and 133 list only signed extensions the encoder writes`() {
        listOf(bundledMetadata, TestRuntimeSnapshots.mainnet133MetadataHex).forEach { metadata ->
            val parsed = v14Metadata(metadata)
            assertEquals(
                listOf(
                    "CheckSpecVersion",
                    "CheckTxVersion",
                    "CheckGenesis",
                    "CheckMortality",
                    "CheckNonce",
                    "CheckWeight",
                    "ChargeTransactionPayment",
                ),
                parsed[RuntimeMetadataSchemaV14.extrinsic][ExtrinsicMetadataV14.signedExtensions]
                    .map { it[SignedExtensionMetadataV14.identifier] },
            )
            assertEquals(
                emptyList<String>(),
                Sora2SignedExtensionAdmission.unsupportedExtensions(parsed),
            )
        }
    }

    @Test
    fun `a new signed extension with data stops signing with an update-the-app error`() =
        runTest {
            // What a runtime with CheckMetadataHash looks like: one more extension whose part
            // of the extrinsic is a byte. The encoder would leave that byte out.
            val upgraded = metadataWithSignedExtension(
                identifier = "CheckMetadataHash",
                carriesDataInExtrinsic = true,
            )
            liveChain(specVersion = 140, transactionVersion = 135)
            coEvery { rpc.getMetadataAtFinalized(FINALIZED_HASH) } returns upgraded
            coEvery { rpc.getRuntimeCodeHashAtFinalized(any()) } returns CODE_HASH
            coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes
            val manager = runtimeManager()

            val error = runCatching { manager.getMutationRuntimeContext() }.exceptionOrNull()

            assertTrue(error is Sora2AppUpdateRequiredException)
            assertEquals(
                listOf("CheckMetadataHash"),
                (error as Sora2AppUpdateRequiredException).signedExtensions,
            )
            assertEquals(
                NetworkFeeUnavailable.APP_UPDATE_REQUIRED,
                NetworkFeeUnavailable.from(IllegalStateException("fee", error)),
            )
            // Balances are still decoded with the new runtime; only signing is refused.
            assertEquals(140, manager.getRuntimeSpecVersion())
            assertEquals(upgraded.runtimeMetadataSha256(), manager.getRuntimeMetadataSha256())
            // It stays refused when the metadata is no longer downloaded.
            val again = runCatching { manager.getMutationRuntimeContext() }.exceptionOrNull()
            assertTrue(again is Sora2AppUpdateRequiredException)
            coVerify(exactly = 1) { rpc.getMetadataAtFinalized(any()) }
        }

    @Test
    fun `a new signed extension in the signed payload only is refused as well`() = runTest {
        val upgraded = metadataWithSignedExtension(
            identifier = "CheckSomethingNew",
            carriesDataInExtrinsic = false,
            carriesDataInSignedPayload = true,
        )
        liveChain(specVersion = 140, transactionVersion = 135)
        coEvery { rpc.getMetadataAtFinalized(FINALIZED_HASH) } returns upgraded
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes

        val error = runCatching { runtimeManager().getMutationRuntimeContext() }.exceptionOrNull()

        assertEquals(
            listOf("CheckSomethingNew"),
            (error as Sora2AppUpdateRequiredException).signedExtensions,
        )
    }

    @Test
    fun `a new signed extension without data does not stop signing`() = runTest {
        val upgraded = metadataWithSignedExtension(
            identifier = "CheckNonZeroSender",
            carriesDataInExtrinsic = false,
        )
        liveChain(specVersion = 140, transactionVersion = 135)
        coEvery { rpc.getMetadataAtFinalized(FINALIZED_HASH) } returns upgraded
        coEvery { http.getUtf8(CONFIG_TYPES_URL, any()) } returns bundledTypes

        val context = runtimeManager().getMutationRuntimeContext()

        assertEquals(140, context.runtimeVersion.specVersion)
        assertTrue("CheckNonZeroSender" in context.snapshot.metadata.extrinsic.signedExtensions)
    }

    @Suppress("UNCHECKED_CAST")
    private fun v14Metadata(metadataHex: String): EncodableStruct<RuntimeMetadataSchemaV14> =
        RuntimeMetadataReader.read(metadataHex.trim()).metadata
            as EncodableStruct<RuntimeMetadataSchemaV14>

    /** The packaged metadata with one more signed extension, re-encoded as a node sends it. */
    private fun metadataWithSignedExtension(
        identifier: String,
        carriesDataInExtrinsic: Boolean,
        carriesDataInSignedPayload: Boolean = false,
    ): String {
        val metadata = v14Metadata(bundledMetadata)
        val types = metadata[RuntimeMetadataSchemaV14.lookup][LookupSchema.types]
        val emptyTuple = types.first { type ->
            (type[PortableType.type][RegistryType.def] as? List<*>)?.isEmpty() == true
        }[PortableType.id]
        val oneByte = types.first { type ->
            type[PortableType.type][RegistryType.def] == TypeDefEnum.u8
        }[PortableType.id]
        val extrinsic = metadata[RuntimeMetadataSchemaV14.extrinsic]
        extrinsic[ExtrinsicMetadataV14.signedExtensions] =
            extrinsic[ExtrinsicMetadataV14.signedExtensions] +
            EncodableStruct(SignedExtensionMetadataV14).also { extension ->
                extension[SignedExtensionMetadataV14.identifier] = identifier
                extension[SignedExtensionMetadataV14.type] =
                    if (carriesDataInExtrinsic) oneByte else emptyTuple
                extension[SignedExtensionMetadataV14.additionalSigned] =
                    if (carriesDataInSignedPayload) oneByte else emptyTuple
            }
        val magicAndVersion = bundledMetadata.trim().fromHex().copyOfRange(0, 5)
        return (magicAndVersion + RuntimeMetadataSchemaV14.toByteArray(metadata))
            .toHexString(withPrefix = true)
    }

    private companion object {
        const val CODE_BINDING_FILE = "sora2_runtime_code_binding_v1.json"
        val CODE_HASH = "0x${"fe".repeat(32)}"
        val OTHER_CODE_HASH = "0x${"0c".repeat(32)}"
        const val LEGACY_METADATA_FILE = "sora2_metadata"
        const val LEGACY_TYPES_FILE = "types_scalecodec_mobile.json"
        const val MANIFEST_FILE = "sora2_runtime_cache_manifest_v2.json"
        const val RUNTIME_VERSION_PREF = "last_used_runtime_version"
        val UPGRADED_HASH = "0x${"6b".repeat(32)}"
        const val CONFIG_TYPES_URL =
            "https://config.polkaswap2.io/prod/substrate_types_mobile_android.json"
        val FINALIZED_HASH = "0x${"5a".repeat(32)}"

        fun repoFile(path: String): String {
            val candidates = listOf(File("../$path"), File(path))
            return candidates.first(File::isFile).readText()
        }
    }
}
