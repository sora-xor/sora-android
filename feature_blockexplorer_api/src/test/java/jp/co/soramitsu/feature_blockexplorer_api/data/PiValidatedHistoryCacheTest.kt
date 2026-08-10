package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
