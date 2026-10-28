package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.common.data.SoraPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test

class PolkamarktCatalogCacheTest {

    @Test
    fun `stored live catalog round trips through preferences with its exact runtime identities`() = runTest {
        var payload = ""
        val preferences = mockk<SoraPreferences>()
        coEvery { preferences.putString(any(), any()) } coAnswers { payload = secondArg(); Unit }
        coEvery { preferences.getString(any()) } coAnswers { payload }
        val cache = PolkamarktCatalogCache(preferences, JSON, PiIndexerClock())
        val markets = listOf(market("first", 7), market("second", 8))
        cache.store(PiQualifiedRead(markets, health(NOW), false), NOW)
        assertThat(cache.read(NOW)?.map { it.marketId }).containsExactly(7L, 8L).inOrder()
        assertThat(cache.read(NOW + 86_401)).isNull()
        assertThat(cache.read(NOW - 31)).isNull()
    }

    @Test
    fun `blank and oversized preference payloads are unavailable without decoding`() = runTest {
        val preferences = mockk<SoraPreferences>()
        val cache = PolkamarktCatalogCache(preferences, JSON, PiIndexerClock())
        for (payload in listOf(" ", "x".repeat(512 * 1024 + 1), "{", "{}")) {
            coEvery { preferences.getString(any()) } returns payload
            assertThat(cache.read(NOW)).isNull()
        }
    }

    @Test
    fun `ambiguous market identities and oversized serialized records are never published`() = runTest {
        val preferences = mockk<SoraPreferences>()
        val cache = PolkamarktCatalogCache(preferences, JSON, PiIndexerClock())
        val rows = listOf(listOf(market("row", 7), market("other", 7)), listOf(market(" row", 7)),
            listOf(market("row", 7).copy(title = "x".repeat(512 * 1024))))
        for (markets in rows) {
            assertThat(runCatching { cache.store(PiQualifiedRead(markets, health(NOW), false), NOW) }.isFailure).isTrue()
        }
        io.mockk.coVerify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `invalid checkpoint hashes timestamps and error pairing cannot be saved`() = runTest {
        val preferences = mockk<SoraPreferences>()
        val cache = PolkamarktCatalogCache(preferences, JSON, PiIndexerClock())
        val original = health(NOW)
        val cases = listOf(original.copy(genesisHash = "0x" + "11".repeat(32)),
            original.copy(latestIndexedBlockHash = "0x" + "00".repeat(32)),
            original.copy(workerLatestIndexedBlock = "9223372036854775808"),
            original.copy(workerLastError = "error", workerLastErrorTimestamp = null),
            original.copy(workerLastError = "x".repeat(1001), workerLastErrorTimestamp = NOW.toString()))
        for (health in cases) {
            assertThat(runCatching { cache.store(PiQualifiedRead(listOf(market("row", 7)), health, false), NOW) }.isFailure).isTrue()
        }
        io.mockk.coVerify(exactly = 0) { preferences.putString(any(), any()) }
    }

    @Test
    fun `qualified positive block hash and bounded prior error preserve a usable catalog`() = runTest {
        val preferences = mockk<SoraPreferences>(relaxed = true)
        val cache = PolkamarktCatalogCache(preferences, JSON, PiIndexerClock())
        val qualified = health(NOW).copy(genesisHash = "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
            latestIndexedBlockHash = "0x" + "12".repeat(32), workerLastError = "previous retry", workerLastErrorTimestamp = (NOW - 30).toString())
        cache.store(PiQualifiedRead(listOf(market("row", 7)), qualified, false), NOW)
        io.mockk.coVerify(exactly = 1) { preferences.putString(any(), any()) }
    }

    @Test
    fun `legacy catalog cache rejects duplicate runtime market ids across distinct rows`() {
        val duplicateRuntimeMarketPayload = payload(
            listOf(
                market(id = "pi-row-a", marketId = 7),
                market(id = "pi-row-b", marketId = 7),
            )
        )

        assertThat(
            PolkamarktCatalogCache.decodeValidatedCatalogPayload(
                json = JSON,
                payload = duplicateRuntimeMarketPayload,
                nowEpochSeconds = NOW,
            )
        ).isNull()

        val distinct = PolkamarktCatalogCache.decodeValidatedCatalogPayload(
            json = JSON,
            payload = payload(
                listOf(
                    market(id = "pi-row-a", marketId = 7),
                    market(id = "pi-row-b", marketId = 8),
                )
            ),
            nowEpochSeconds = NOW,
        )

        assertThat(distinct?.map(PolkamarktMarket::marketId))
            .containsExactly(7L, 8L)
            .inOrder()
    }

    @Test
    fun `a catalog read on a phone with a wrong clock is saved, a stale one is not`() = runTest {
        val saved = slot<String>()
        val preferences = mockk<SoraPreferences>().also {
            coEvery { it.putString(any(), capture(saved)) } returns Unit
        }
        // The indexer indexed a block five seconds ago by its own clock.
        val catalog = PiQualifiedRead(
            value = listOf(market(id = "pi-row-a", marketId = 7)),
            health = health(indexedAtEpochSeconds = NOW - 5),
            fromCache = false,
        )

        listOf(-45L, 6 * 60L, 3 * 60 * 60L).forEach { deviceOffset ->
            val device = NOW + deviceOffset
            // What a health check that passed measured from the HTTP Date of its response.
            val measured = PiIndexerClock().also {
                it.recordHealthReference(deviceEpochSeconds = device, referenceEpochSeconds = NOW)
            }
            saved.clear()

            PolkamarktCatalogCache(preferences, JSON, measured).store(catalog, device)

            assertThat(saved.isCaptured).isTrue()
            // The catalog's own age is still counted by the phone's clock.
            assertThat(
                PolkamarktCatalogCache.decodeValidatedCatalogPayload(JSON, saved.captured, device)
                    ?.map(PolkamarktMarket::marketId)
            ).containsExactly(7L)

            // Without the measurement (the behaviour before the fix) the save is refused.
            val unmeasured = runCatching {
                PolkamarktCatalogCache(preferences, JSON, PiIndexerClock()).store(catalog, device)
            }.exceptionOrNull()
            assertThat(unmeasured).hasMessageThat()
                .isEqualTo("PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID")

            // Six minutes later the checkpoint is too old by the indexer's own clock as well.
            val stale = runCatching {
                PolkamarktCatalogCache(preferences, JSON, measured).store(catalog, device + 6 * 60)
            }.exceptionOrNull()
            assertThat(stale).hasMessageThat()
                .isEqualTo("PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID")
        }
    }

    private fun health(indexedAtEpochSeconds: Long) = PiIndexerHealth(
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
        genesisHash = null,
        latestIndexedBlock = "100",
        latestIndexedBlockHash = null,
        latestIndexedAt = indexedAtEpochSeconds.toString(),
        workerAvailable = true,
        workerReady = true,
        workerReadinessReason = null,
        workerLifecycle = "running",
        workerStartupComplete = true,
        workerLatestFinalizedBlock = "104",
        workerLatestIndexedBlock = "100",
        workerLag = "4",
        workerLastSuccessfulIndexTimestamp = indexedAtEpochSeconds.toString(),
        workerLastError = null,
        workerLastErrorTimestamp = null,
    )

    private fun payload(markets: List<PolkamarktMarket>): String =
        buildJsonObject {
            put("version", JsonPrimitive(1))
            put("chainId", JsonPrimitive("sora:mainnet"))
            put(
                "genesisHash",
                JsonPrimitive(
                    "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
                )
            )
            put("savedAt", JsonPrimitive(NOW))
            put(
                "markets",
                JsonArray(
                    markets.map {
                        JSON.encodeToJsonElement(
                            PolkamarktMarket.serializer(),
                            it,
                        )
                    }
                )
            )
        }.toString()

    private fun market(id: String, marketId: Long?) = PolkamarktMarket(
        id = id,
        marketId = marketId,
        title = null,
        category = null,
        tags = null,
        description = null,
        rulesUri = null,
        resolutionSource = null,
        closeBlock = null,
        status = "Open",
        mechanism = null,
        creator = null,
        collateralAsset = null,
        creatorFees = null,
        liquidityUsd = null,
        volumeUsd = null,
        chartProbability = null,
        chartPriceYes = null,
        chartPriceNo = null,
        virtualDepth = null,
        dpmCollateral = null,
        realYesShares = null,
        realNoShares = null,
        marginalYesPriceBps = null,
        marginalNoPriceBps = null,
        impliedYesProbabilityBps = null,
        impliedNoProbabilityBps = null,
        collateral = null,
        yesShares = null,
        noShares = null,
        resolutionOutcome = null,
        resolutionEvidenceUri = null,
        governanceUrl = null,
        updatedAtBlock = 1,
        timestamp = null,
    )

    private companion object {
        const val NOW = 1_800_000_000L
        val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
