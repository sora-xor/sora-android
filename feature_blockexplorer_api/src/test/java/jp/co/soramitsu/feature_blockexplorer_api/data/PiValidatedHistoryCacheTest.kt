package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PiValidatedHistoryCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `canonical validated history survives restart but not account change or expiry`() = runTest {
        var now = NOW
        val directory = temporaryFolder.newFolder("validated-history")
        val first = cache(directory) { now }

        first.storeLast(
            address = ACCOUNT,
            count = 1,
            validation = validation(
                items = listOf(historyElement()),
                address = ACCOUNT,
                health = health(now),
            ),
        )

        val reopened = cache(directory) { now }
        assertThat(reopened.readLast(ACCOUNT, 1)?.items)
            .containsExactly(historyElement())
        assertThat(reopened.readLast("cnOtherAccount", 1)).isNull()

        now += ONE_DAY_MILLIS + 1
        assertThat(reopened.readLast(ACCOUNT, 1)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `canonical validation receipt cannot be reused for another account`() = runTest {
        val directory = temporaryFolder.newFolder("wrong-account")
        val cache = cache(directory) { NOW }
        val otherAccount = "cnOtherAccount"

        val error = runCatching {
            cache.storeLast(
                address = ACCOUNT,
                count = 1,
                validation = validation(
                    items = listOf(historyElement().copy(address = otherAccount)),
                    address = otherAccount,
                    health = health(NOW),
                ),
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_VALIDATED_HISTORY_RECEIPT_MISMATCH")
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test fun transactionKeysCanonicalizeHashesWithoutMixingAccounts() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        subject.storeTransaction(ACCOUNT, TRANSACTION_HASH.uppercase(), receipt)
        assertEquals(listOf(historyElement()), subject.readTransaction(ACCOUNT, TRANSACTION_HASH.removePrefix("0x"))?.items)
        assertNull(subject.readTransaction("another", TRANSACTION_HASH))
        assertNull(subject.readLast(ACCOUNT, 1))
        assertEquals("PI_VALIDATED_HISTORY_RECEIPT_MISMATCH", runCatching {
            subject.storeTransaction(ACCOUNT, "0x" + "cc".repeat(32), receipt)
        }.exceptionOrNull()?.message)
    }

    @Test fun paginatedRecordsRetainExactCompletenessMetadata() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val item = historyElement(); val receipt = validation(listOf(item), ACCOUNT, health(NOW))
        subject.storePage(ACCOUNT, 1, 1, IndexerHistoryPage(listOf(item), false, 2), receipt)
        assertEquals(PiValidatedHistoryRecord(listOf(item), health(NOW), false, 2), subject.readPage(ACCOUNT, 1, 1))
        subject.storePage(ACCOUNT, 2, 1, IndexerHistoryPage(listOf(item), true, 2), receipt)
        assertEquals(true, subject.readPage(ACCOUNT, 2, 1)?.endReached)
        assertNull(subject.readPage(ACCOUNT, 3, 1))
    }

    @Test fun invalidPageMetadataAndMismatchedReceiptNeverWriteAFile() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val item = historyElement(); val receipt = validation(listOf(item), ACCOUNT, health(NOW))
        for (page in listOf(IndexerHistoryPage(listOf(item), false, 1), IndexerHistoryPage(listOf(item), true, 2), IndexerHistoryPage(listOf(item), true, -1))) {
            assertEquals("PI_VALIDATED_HISTORY_PAGE_INVALID", runCatching { subject.storePage(ACCOUNT, 1, 1, page, receipt) }.exceptionOrNull()?.message)
        }
        assertEquals("PI_VALIDATED_HISTORY_RECEIPT_MISMATCH", runCatching {
            subject.storePage(ACCOUNT, 1, 1, IndexerHistoryPage(emptyList(), true, 0), receipt)
        }.exceptionOrNull()?.message)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun requestsRejectBlankOversizedAndNoncanonicalIdentifiers() = runTest {
        val subject = cache(temporaryFolder.newFolder()) { NOW }
        for (account in listOf("", " spaced", "spaced ", "a".repeat(513)))
            assertEquals("PI_VALIDATED_HISTORY_REQUEST_INVALID", runCatching { subject.readLast(account, 1) }.exceptionOrNull()?.message)
        for (count in listOf(-1, 0, 101)) assertEquals("PI_VALIDATED_HISTORY_REQUEST_INVALID", runCatching { subject.readLast(ACCOUNT, count) }.exceptionOrNull()?.message)
        for (page in listOf(0L, -1L, 1001L)) assertEquals("PI_VALIDATED_HISTORY_REQUEST_INVALID", runCatching { subject.readPage(ACCOUNT, page, 1) }.exceptionOrNull()?.message)
        for (hash in listOf("", "0x123", " " + TRANSACTION_HASH, "g".repeat(64))) assertEquals("PI_VALIDATED_HISTORY_REQUEST_INVALID", runCatching { subject.readTransaction(ACCOUNT, hash) }.exceptionOrNull()?.message)
    }

    private fun rewritePayload(file: File, change: (MutableMap<String, JsonElement>) -> Unit) {
        val envelope = JSON.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
        val payload = JSON.parseToJsonElement(envelope.getValue("payloadJson").jsonPrimitive.content).jsonObject.toMutableMap()
        change(payload)
        val encoded = JsonObject(payload).toString()
        envelope["payloadJson"] = JsonPrimitive(encoded)
        envelope["payloadSha256"] = JsonPrimitive(MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) })
        file.writeText(JsonObject(envelope).toString())
    }

    @Test fun persistedEnvelopeTamperingIsEvictedInsteadOfPresentedAsHistory() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        for ((key, replacement) in listOf("formatVersion" to JsonPrimitive(99), "requestKey" to JsonPrimitive("another"), "payloadSha256" to JsonPrimitive("Z".repeat(64)), "payloadSha256" to JsonPrimitive("00".repeat(32)), "payloadJson" to JsonPrimitive("{}"))) {
            subject.storeLast(ACCOUNT, 1, receipt)
            val file = directory.listFiles()!!.single()
            val envelope = JSON.parseToJsonElement(file.readText()).jsonObject.toMutableMap(); envelope[key] = replacement
            file.writeText(JsonObject(envelope).toString())
            assertNull(subject.readLast(ACCOUNT, 1)); assertFalse(file.exists())
        }
    }

    @Test fun validChecksumDoesNotAdmitTamperedIdentityOrCheckpoint() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        val mutations = listOf<Pair<String, JsonElement>>("service" to JsonPrimitive("other"), "genesisHash" to JsonPrimitive("0x" + "ff".repeat(32)), "workerLag" to JsonPrimitive("999"), "workerLatestFinalizedBlock" to JsonPrimitive("99"), "workerLastSuccessfulIndexTimestamp" to JsonPrimitive("1"), "workerLatestIndexedBlock" to JsonPrimitive("9223372036854775808"), "workerLastError" to JsonPrimitive("failure"), "workerLastErrorTimestamp" to JsonPrimitive("NaN"))
        for ((key, value) in mutations) {
            subject.storeLast(ACCOUNT, 1, receipt)
            val file = directory.listFiles()!!.single()
            rewritePayload(file) { payload ->
                val health = payload.getValue("health").jsonObject.toMutableMap(); health[key] = value
                payload["health"] = JsonObject(health)
            }
            assertNull("Accepted invalid $key", subject.readLast(ACCOUNT, 1)); assertFalse(file.exists())
        }
    }

    @Test fun timestampAndRecordTamperingWithValidDigestIsRejected() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        val changes: List<(MutableMap<String, JsonElement>) -> Unit> = listOf(
            { it["savedAtEpochMillis"] = JsonPrimitive(-1) },
            { it["savedAtEpochMillis"] = JsonPrimitive(NOW + 30_001) },
            { it["healthReferenceEpochSeconds"] = JsonPrimitive(-1) },
            { it["requestKey"] = JsonPrimitive("different") },
            { it["formatVersion"] = JsonPrimitive(2) },
            { it["items"] = JsonArray(it.getValue("items").jsonArray + it.getValue("items").jsonArray) },
            { it["endReached"] = JsonPrimitive(true) },
        )
        for (change in changes) {
            subject.storeLast(ACCOUNT, 1, receipt)
            val file = directory.listFiles()!!.single(); rewritePayload(file, change)
            assertNull(subject.readLast(ACCOUNT, 1)); assertFalse(file.exists())
        }
    }

    @Test fun olderFormatWithoutHealthReferenceUsesItsSavedTimestamp() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        subject.storeLast(ACCOUNT, 1, validation(listOf(historyElement()), ACCOUNT, health(NOW)))
        rewritePayload(directory.listFiles()!!.single()) { it.remove("healthReferenceEpochSeconds") }
        assertEquals(listOf(historyElement()), subject.readLast(ACCOUNT, 1)?.items)
    }

    @Test fun symlinkDirectoriesAndDestinationsAreNeverFollowed() = runTest {
        val directory = temporaryFolder.newFolder(); val subject = cache(directory) { NOW }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        subject.storeLast(ACCOUNT, 1, receipt)
        val file = directory.listFiles()!!.single(); file.delete()
        val outside = temporaryFolder.newFile().apply { writeText("untouched") }
        Files.createSymbolicLink(file.toPath(), outside.toPath())
        subject.storeLast(ACCOUNT, 1, receipt); assertNull(subject.readLast(ACCOUNT, 1))
        assertEquals("untouched", outside.readText()); assertTrue(Files.isSymbolicLink(file.toPath()))
        val link = File(temporaryFolder.root, "directory-link"); Files.createSymbolicLink(link.toPath(), directory.toPath())
        cache(link) { NOW }.storeLast(ACCOUNT, 1, receipt)
        assertNull(cache(link) { NOW }.readLast(ACCOUNT, 1))
    }

    @Test fun pruningBoundsEntriesAndRemovesJunkButPreservesForeignDirectories() = runTest {
        val directory = temporaryFolder.newFolder(); var now = NOW
        val subject = cache(directory) { now }
        val receipt = validation(listOf(historyElement()), ACCOUNT, health(NOW))
        val junk = File(directory, "interrupted.tmp").apply { writeText("partial") }
        val foreign = File(directory, "subdirectory").apply { mkdir() }
        repeat(70) { index ->
            val name = index.toString(16).padStart(64, '0') + ".json"
            File(directory, name).apply { writeText("{}"); setLastModified(NOW - index - 1000) }
        }
        subject.storeLast(ACCOUNT, 1, receipt)
        assertFalse(junk.exists()); assertTrue(foreign.isDirectory)
        assertEquals(64, directory.listFiles()!!.count { it.isFile })
        assertNotNull(subject.readLast(ACCOUNT, 1))
        now += ONE_DAY_MILLIS + 1
        assertNull(subject.readLast(ACCOUNT, 1))
    }

    private suspend fun validation(
        items: List<IndexerHistoryElement>,
        address: String,
        health: PiIndexerHealth,
    ) = PiHistoryCheckpointValidator.validate(
        elements = items,
        expectedAddress = address,
        health = health,
        maximumRecords = 1,
        canonicalBlockHash = { BLOCK_HASH },
    )

    private fun cache(
        directory: File,
        now: () -> Long,
    ) = PiValidatedHistoryCache(
        directory = directory,
        json = JSON,
        nowEpochMillis = now,
        testOnly = Unit,
    )

    private fun historyElement() = IndexerHistoryElement(
        id = TRANSACTION_HASH,
        blockHash = BLOCK_HASH,
        blockHeight = 100,
        module = "Assets",
        method = "transfer",
        address = ACCOUNT,
        timestamp = "1800000000",
        networkFee = "1",
        success = true,
        data = null,
        nestedData = null,
        executionKnown = true,
    )

    private fun health(now: Long) = PiIndexerHealth(
        ok = true,
        repositoryReady = true,
        service = "polkaswap-indexer",
        serviceId = "pi.soramitsu.io",
        schemaVersion = 1,
        ecosystem = "sora2",
        chainId = "sora:mainnet",
        network = "mainnet",
        publicBaseUrl = "https://pi.soramitsu.io/graphql",
        readOnly = true,
        genesisHash =
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
        latestIndexedBlock = "100",
        latestIndexedBlockHash = BLOCK_HASH,
        latestIndexedAt = (now / 1_000).toString(),
        workerAvailable = true,
        workerReady = true,
        workerReadinessReason = null,
        workerLifecycle = "running",
        workerStartupComplete = true,
        workerLatestFinalizedBlock = "104",
        workerLatestIndexedBlock = "100",
        workerLag = "4",
        workerLastSuccessfulIndexTimestamp = (now / 1_000).toString(),
        workerLastError = null,
        workerLastErrorTimestamp = null,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1_000
        const val ACCOUNT = "cnExistingAccount"
        const val TRANSACTION_HASH =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val BLOCK_HASH =
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val JSON = Json { ignoreUnknownKeys = false }
    }
}
