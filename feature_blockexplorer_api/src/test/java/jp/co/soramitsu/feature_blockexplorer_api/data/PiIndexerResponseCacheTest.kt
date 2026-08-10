package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PiIndexerResponseCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `qualified response persists across cache instances and expires`() = runTest {
        var now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("pi-cache")
        val first = cache(directory) { now }
        val entry = entry(savedAt = now, response = """{"data":{"value":"1"}}""")

        first.store(KEY, entry)

        val reopened = cache(directory) { now }
        assertThat(reopened.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS))
            .isEqualTo(entry)

        now += ONE_DAY_MILLIS + 1
        assertThat(reopened.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `cache writes all seven health integer fields as strings and reads numeric tokens`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("pi-integer-cache")
        val cache = cache(directory) { now }
        val baseEntry = entry(savedAt = now, response = """{"data":{"value":"1"}}""")
        val entry = baseEntry.copy(
            qualification = baseEntry.qualification.copy(
                health = baseEntry.qualification.health.copy(
                    workerLastErrorTimestamp = "1799999999",
                ),
            ),
        )

        cache.store(KEY, entry)

        val file = directory.listFiles().orEmpty().single()
        val canonical = file.readText()
        assertThat(canonical).contains("\"latestIndexedBlock\":\"26000001\"")
        assertThat(canonical).contains("\"latestIndexedAt\":\"1800000000\"")
        assertThat(canonical).contains("\"workerLatestFinalizedBlock\":\"26000005\"")
        assertThat(canonical).contains("\"workerLatestIndexedBlock\":\"26000001\"")
        assertThat(canonical).contains("\"workerLag\":\"4\"")
        assertThat(canonical).contains(
            "\"workerLastSuccessfulIndexTimestamp\":\"1800000000\""
        )
        assertThat(canonical).contains(
            "\"workerLastErrorTimestamp\":\"1799999999\""
        )

        val deployedNumeric = canonical
            .replace(
                "\"latestIndexedBlock\":\"26000001\"",
                "\"latestIndexedBlock\":26000001",
            )
            .replace(
                "\"latestIndexedAt\":\"1800000000\"",
                "\"latestIndexedAt\":1800000000",
            )
            .replace(
                "\"workerLatestFinalizedBlock\":\"26000005\"",
                "\"workerLatestFinalizedBlock\":26000005",
            )
            .replace(
                "\"workerLatestIndexedBlock\":\"26000001\"",
                "\"workerLatestIndexedBlock\":26000001",
            )
            .replace("\"workerLag\":\"4\"", "\"workerLag\":4")
            .replace(
                "\"workerLastSuccessfulIndexTimestamp\":\"1800000000\"",
                "\"workerLastSuccessfulIndexTimestamp\":1800000000",
            )
            .replace(
                "\"workerLastErrorTimestamp\":\"1799999999\"",
                "\"workerLastErrorTimestamp\":1799999999",
            )
        assertThat(deployedNumeric).isNotEqualTo(canonical)
        file.writeText(deployedNumeric)

        assertThat(cache.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isEqualTo(entry)
    }

    @Test
    fun `oversized response and invalid key are never persisted`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("bounded-cache")
        val cache = cache(directory) { now }

        cache.store(
            KEY,
            entry(
                savedAt = now,
                response = "\"" + "x".repeat(4 * 1_024 * 1_024) + "\"",
            ),
        )
        cache.store(
            "../not-a-request-digest",
            entry(savedAt = now, response = "{}"),
        )
        cache.store(
            KEY,
            entry(savedAt = now, response = """{"data":1,"data":2}"""),
        )

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `ambiguous cache envelope is deleted before materialization`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("ambiguous-cache")
        val cache = cache(directory) { now }
        cache.store(
            KEY,
            entry(savedAt = now, response = """{"data":{"value":"1"}}"""),
        )
        val file = directory.listFiles().orEmpty().single()
        val original = file.readText()
        val corrupted = original.replace(
            "\"requestKey\":\"$KEY\"",
            "\"requestKey\":\"$KEY\",\"requestKey\":\"$KEY\"",
        )
        assertThat(corrupted).isNotEqualTo(original)
        file.writeText(corrupted)

        assertThat(cache.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `ambiguous cached response is deleted before publication`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("ambiguous-response-cache")
        val cache = cache(directory) { now }
        cache.store(
            KEY,
            entry(savedAt = now, response = """{"data":{"value":"1"}}"""),
        )
        val file = directory.listFiles().orEmpty().single()
        val original = file.readText()
        val corrupted = original.replace(
            "\\\"value\\\":\\\"1\\\"",
            "\\\"value\\\":\\\"1\\\",\\\"value\\\":\\\"2\\\"",
        )
        assertThat(corrupted).isNotEqualTo(original)
        file.writeText(corrupted)

        assertThat(cache.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `response is rejected when health did not immediately precede it`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("qualification-cache")
        val cache = cache(directory) { now }
        val late = entry(
            savedAt = now,
            response = """{"data":{"value":"1"}}""",
        ).copy(
            qualification = qualification(now - 30_001),
        )

        cache.store(KEY, late)

        assertThat(cache.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `corrupt extreme timestamps are rejected without arithmetic overflow`() = runTest {
        val now = 1_800_000_000_000L
        val directory = temporaryFolder.newFolder("timestamp-cache")
        val cache = cache(directory) { now }

        cache.store(
            KEY,
            entry(savedAt = 0, response = "{}").copy(
                savedAtEpochMillis = Long.MIN_VALUE,
                qualification = qualification(Long.MIN_VALUE),
            ),
        )
        cache.store(
            KEY,
            entry(savedAt = 0, response = "{}").copy(
                savedAtEpochMillis = Long.MAX_VALUE,
                qualification = qualification(0),
            ),
        )

        assertThat(cache.load(KEY, maximumAgeMillis = ONE_DAY_MILLIS)).isNull()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    private fun cache(
        directory: File,
        now: () -> Long,
    ) = FilePiIndexerResponseCache(
        directory = directory,
        json = JSON,
        nowEpochMillis = now,
        testOnly = Unit,
    )

    private fun entry(
        savedAt: Long,
        response: String,
    ) = PiIndexerCacheEntry(
        requestKey = KEY,
        savedAtEpochMillis = savedAt,
        qualification = qualification(savedAt),
        responseJson = response,
    )

    private fun qualification(
        qualifiedAt: Long,
    ) = PiIndexerCacheQualification(
        health = PiIndexerHealth(
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
            latestIndexedBlock = "26000001",
            latestIndexedBlockHash = null,
            latestIndexedAt = (qualifiedAt / 1_000).toString(),
            workerAvailable = true,
            workerReady = true,
            workerReadinessReason = null,
            workerLifecycle = "running",
            workerStartupComplete = true,
            workerLatestFinalizedBlock = "26000005",
            workerLatestIndexedBlock = "26000001",
            workerLag = "4",
            workerLastSuccessfulIndexTimestamp = (qualifiedAt / 1_000).toString(),
            workerLastError = null,
            workerLastErrorTimestamp = null,
        ),
        qualifiedAtEpochMillis = qualifiedAt,
    )

    private companion object {
        const val KEY =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1_000
        val JSON = Json { ignoreUnknownKeys = false }
    }
}
