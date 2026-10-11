package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A phone whose clock is somewhat wrong must not only load Activity while it is online; the
 * pages it loaded must also be saved, so that it has them when it is offline (audit LIVE-13).
 *
 * The health check runs through the production client and wire path with a scripted HTTP `Date`;
 * the store is the production [PiValidatedHistoryCache] on a temporary directory.
 */
class PiIndexerClockSkewStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `history loaded on a phone with a wrong clock is saved and read back offline`() = runTest {
        // 45 seconds slow, six minutes fast, ten minutes slow, a wrong time zone.
        listOf(-45L, 6 * 60L, -10 * 60L, 3 * 60 * 60L).forEach { deviceOffset ->
            val device = SERVER_NOW + deviceOffset
            val clock = PiIndexerClock()
            val health = qualifiedHealth(deviceEpochSeconds = device, clock = clock)
            val directory = temporaryFolder.newFolder("skew-$deviceOffset")
            val cache = cache(directory, deviceEpochSeconds = device, clock = clock)

            cache.storeLast(
                address = ACCOUNT,
                count = 1,
                validation = validation(health),
            )

            // A new process, offline: the same phone clock, no health check yet.
            val offline = cache(
                directory = directory,
                deviceEpochSeconds = device + 60,
                clock = PiIndexerClock(),
            )
            assertThat(offline.readLast(ACCOUNT, 1)?.items).containsExactly(historyElement())
        }
    }

    @Test
    fun `without a measured clock difference the phone clock decides as before`() = runTest {
        val device = SERVER_NOW + 6 * 60
        val measured = PiIndexerClock()
        val health = qualifiedHealth(deviceEpochSeconds = device, clock = measured)
        val directory = temporaryFolder.newFolder("unmeasured")

        // The store was not told what the health check measured (the behaviour before the fix
        // for every phone): the indexer's timestamps look more than five minutes old.
        val error = runCatching {
            cache(directory, deviceEpochSeconds = device, clock = PiIndexerClock()).storeLast(
                address = ACCOUNT,
                count = 1,
                validation = validation(health),
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_VALIDATED_HISTORY_CHECKPOINT_INVALID")
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `an indexer that really is behind is not saved whatever the phone clock says`() = runTest {
        val device = SERVER_NOW - 45
        val clock = PiIndexerClock()
        val health = qualifiedHealth(deviceEpochSeconds = device, clock = clock)
        val directory = temporaryFolder.newFolder("stale")
        // Six minutes pass before the page is saved: the checkpoint is now too old to vouch
        // for it, by the indexer's own clock.
        val later = cache(directory, deviceEpochSeconds = device + 6 * 60, clock = clock)

        val error = runCatching {
            later.storeLast(address = ACCOUNT, count = 1, validation = validation(health))
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_VALIDATED_HISTORY_CHECKPOINT_INVALID")
    }

    @Test
    fun `the clock keeps no difference larger than a live health check accepts`() {
        val clock = PiIndexerClock()

        clock.recordHealthReference(deviceEpochSeconds = 1_000_000, referenceEpochSeconds = 1_000_360)
        assertThat(clock.indexerEpochSeconds(2_000_000)).isEqualTo(2_000_360)

        clock.recordHealthReference(deviceEpochSeconds = 1_000_000, referenceEpochSeconds = 999_955)
        assertThat(clock.indexerEpochSeconds(2_000_000)).isEqualTo(1_999_955)

        // The phone clock decided that health check (no usable Date): nothing to correct.
        clock.recordHealthReference(deviceEpochSeconds = 1_000_000, referenceEpochSeconds = 1_000_000)
        assertThat(clock.indexerEpochSeconds(2_000_000)).isEqualTo(2_000_000)

        clock.recordHealthReference(
            deviceEpochSeconds = 1_000_000,
            referenceEpochSeconds = 1_000_001 + PI_MAX_DEVICE_CLOCK_SKEW_SECONDS,
        )
        assertThat(clock.indexerEpochSeconds(2_000_000)).isEqualTo(2_000_000)
    }

    /** One health check through the production client: phone clock, HTTP Date, indexed time. */
    private suspend fun qualifiedHealth(
        deviceEpochSeconds: Long,
        clock: PiIndexerClock,
    ): PiIndexerHealth {
        val http = mockk<BoundedHttpTextClient>()
        coEvery { http.postJson(any(), any(), any(), any(), any()) } returns
            BoundedHttpTextResponse(
                body = healthResponse(indexedAt = SERVER_NOW - 5),
                serverDateEpochMillis = SERVER_NOW * 1_000,
            )
        val client = PolkaswapIndexerClient(
            boundedHttpTextClient = http,
            json = CLIENT_JSON,
            responseCache = DisabledPiIndexerResponseCache,
            nowEpochMillis = { deviceEpochSeconds * 1_000 },
            nowMonotonicNanos = { 1_000 },
            indexerClock = clock,
        )
        return client.requireHealthy()
    }

    private fun cache(
        directory: File,
        deviceEpochSeconds: Long,
        clock: PiIndexerClock,
    ) = PiValidatedHistoryCache(
        directory = directory,
        json = CACHE_JSON,
        nowEpochMillis = { deviceEpochSeconds * 1_000 },
        testOnly = Unit,
        indexerClock = clock,
    )

    private suspend fun validation(health: PiIndexerHealth) =
        PiHistoryCheckpointValidator.validate(
            elements = listOf(historyElement()),
            expectedAddress = ACCOUNT,
            health = health,
            maximumRecords = 1,
            canonicalBlockHash = { BLOCK_HASH },
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

    private fun healthResponse(indexedAt: Long) =
        """
        {
          "data": {
            "_health": {
              "ok": true,
              "repositoryReady": true,
              "service": "polkaswap-indexer",
              "serviceId": "pi.soramitsu.io",
              "schemaVersion": 1,
              "ecosystem": "sora2",
              "chainId": "sora:mainnet",
              "network": "mainnet",
              "publicBaseUrl": "https://pi.soramitsu.io/graphql",
              "readOnly": true,
              "workerAvailable": true,
              "workerReady": true,
              "workerReadinessReason": null,
              "workerLifecycle": "running",
              "workerStartupComplete": true,
              "workerLatestFinalizedBlock": 104,
              "workerLatestIndexedBlock": 100,
              "workerLag": 4,
              "workerLastSuccessfulIndexTimestamp": $indexedAt,
              "workerLastError": null,
              "workerLastErrorTimestamp": null
            }
          }
        }
        """.trimIndent()

    private companion object {
        const val SERVER_NOW = 1_800_000_100L
        const val ACCOUNT = "cnExistingAccount"
        const val TRANSACTION_HASH =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val BLOCK_HASH =
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val CLIENT_JSON = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
        val CACHE_JSON = Json { ignoreUnknownKeys = false }
    }
}
