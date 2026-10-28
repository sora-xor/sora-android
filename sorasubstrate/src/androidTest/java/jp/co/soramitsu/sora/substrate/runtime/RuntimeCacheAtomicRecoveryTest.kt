package jp.co.soramitsu.sora.substrate.runtime

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.io.FileManagerImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeCacheAtomicRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fileManager = FileManagerImpl(context)
    private val gson = Gson()
    private val oldMetadata = "0x0102\n"
    private val oldTypes = "{\"runtime_id\":130,\"generation\":\"old\"}\n"
    private val newMetadata = "0x0304\n"
    private val newTypes = "{\"runtime_id\":130,\"generation\":\"new\"}\n"
    private val oldManifest = createRuntimeCacheManifest(
        oldMetadata,
        oldTypes,
        130,
        Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
    )
    private val newManifest = createRuntimeCacheManifest(
        newMetadata,
        newTypes,
        130,
        Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
    )

    @Before
    fun clearExactRuntimeEntriesBeforeRun() {
        clearExactRuntimeEntries()
    }

    @After
    fun clearExactRuntimeEntries() {
        listOf(oldManifest, newManifest).forEach { manifest ->
            deleteAtomicEntry(checkNotNull(manifest.metadataFile))
            deleteAtomicEntry(checkNotNull(manifest.typesFile))
        }
        deleteAtomicEntry(MANIFEST_FILE)
    }

    @Test
    fun interruptionAfterNewMetadataWriteRetainsTheOldCompletePointer() {
        publishComplete(oldManifest, oldMetadata, oldTypes)
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.metadataFile),
            newMetadata,
        )

        assertPublished(oldMetadata, oldTypes)
    }

    @Test
    fun interruptionAfterBothGenerationWritesRetainsTheOldCompletePointer() {
        publishComplete(oldManifest, oldMetadata, oldTypes)
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.metadataFile),
            newMetadata,
        )
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.typesFile),
            newTypes,
        )

        assertPublished(oldMetadata, oldTypes)
    }

    @Test
    fun interruptedManifestReplacementRecoversTheOldCompletePointer() {
        publishComplete(oldManifest, oldMetadata, oldTypes)
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.metadataFile),
            newMetadata,
        )
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.typesFile),
            newTypes,
        )

        val atomicManifest = AtomicFile(File(context.cacheDir, MANIFEST_FILE))
        val interruptedOutput = atomicManifest.startWrite()
        interruptedOutput.write(gson.toJson(newManifest).take(24).toByteArray(Charsets.UTF_8))
        interruptedOutput.flush()
        interruptedOutput.fd.sync()
        interruptedOutput.close()

        assertPublished(oldMetadata, oldTypes)
    }

    @Test
    fun completedManifestReplacementPublishesOnlyTheNewCompleteGeneration() {
        publishComplete(oldManifest, oldMetadata, oldTypes)
        publishComplete(newManifest, newMetadata, newTypes)

        assertPublished(newMetadata, newTypes)
    }

    @Test
    fun aPointerWhoseGenerationIsIncompleteFailsWithoutMixingOldAndNewFiles() {
        publishComplete(oldManifest, oldMetadata, oldTypes)
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(newManifest.metadataFile),
            newMetadata,
        )
        fileManager.writeInternalCacheFileAtomically(MANIFEST_FILE, gson.toJson(newManifest))

        assertEquals(
            "SORA2_RUNTIME_CACHE_TYPES_MISSING",
            assertThrows(Sora2RuntimeIdentityException::class.java) {
                readPublishedRuntimeCache()
            }.message,
        )
    }

    private fun publishComplete(
        manifest: RuntimeCacheManifest,
        metadata: String,
        types: String,
    ) {
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(manifest.metadataFile),
            metadata,
        )
        fileManager.writeInternalCacheFileAtomically(
            checkNotNull(manifest.typesFile),
            types,
        )
        fileManager.writeInternalCacheFileAtomically(MANIFEST_FILE, gson.toJson(manifest))
    }

    private fun assertPublished(expectedMetadata: String, expectedTypes: String) {
        val published = readPublishedRuntimeCache()
        assertEquals(expectedMetadata, published.metadata)
        assertEquals(expectedTypes, published.sora2Types)
    }

    private fun readPublishedRuntimeCache(): PublishedRuntimeCache {
        val manifestRaw = checkNotNull(
            fileManager.readInternalCacheFileAtomically(MANIFEST_FILE),
        )
        val manifest = gson.fromJson(manifestRaw, RuntimeCacheManifest::class.java)
        return requirePublishedRuntimeCache(
            manifest = manifest,
            metadata = fileManager.readInternalCacheFileAtomically(
                checkNotNull(manifest.metadataFile),
            ),
            sora2Types = fileManager.readInternalCacheFileAtomically(
                checkNotNull(manifest.typesFile),
            ),
        )
    }

    private fun deleteAtomicEntry(fileName: String) {
        listOf(fileName, "$fileName.bak", "$fileName.new").forEach { exactName ->
            Files.deleteIfExists(File(context.cacheDir, exactName).toPath())
        }
    }

    private companion object {
        const val MANIFEST_FILE = "sora2_runtime_cache_manifest_v2.json"
    }
}
