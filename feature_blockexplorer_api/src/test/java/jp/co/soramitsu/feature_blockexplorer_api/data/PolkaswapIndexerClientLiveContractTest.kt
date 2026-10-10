package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * LIVE NETWORK TEST against the deployed PI indexer (https://pi.soramitsu.io/graphql). Off by
 * default and not part of CI; run it with:
 *
 *     RUN_LIVE_PI_CLIENT_TESTS=true ./gradlew :feature_blockexplorer_api:testProductionDebugUnitTest \
 *       --tests '*PolkaswapIndexerClientLiveContractTest*' --rerun
 *
 * Keep `--rerun`: the environment variable is not a Gradle task input, so an up-to-date test
 * task would otherwise be skipped without contacting the indexer.
 *
 * The client is built the way production builds it: the bounded HTTPS transport with strict JSON
 * (no lenient parsing, no duplicate keys, the live response size limit), the health preflight and
 * checkpoint postflight, and the production file response cache (in a temporary directory). So a
 * pass means the deployed schema accepts every query the app sends AND the app's production
 * decoding accepts the real responses. History rows are also checked against canonical SORA
 * mainnet block hashes with [PiHistoryCheckpointValidator], as `TransactionHistoryRepositoryImpl`
 * does, and read back from the response cache with the network off.
 */
class PolkaswapIndexerClientLiveContractTest {

    // The application-wide Json (CommonModule.provideJson). The PI transport derives its strict
    // wire parser from it, exactly as in the app.
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val http = BoundedHttpTextClient()
    private lateinit var cacheDirectory: File

    private val cache by lazy {
        FilePiIndexerResponseCache(cacheDirectory, json, System::currentTimeMillis, Unit)
    }

    private val client by lazy { PolkaswapIndexerClient(http, json, cache) }

    /** The same client and cache with every HTTPS request failing as it does offline. */
    private val offlineClient by lazy {
        val offline = mockk<BoundedHttpTextClient>()
        coEvery { offline.postJsonUtf8(any(), any(), any()) } throws
            BoundedHttpTextException("BOUNDED_HTTP_IO")
        coEvery { offline.getUtf8(any(), any()) } throws BoundedHttpTextException("BOUNDED_HTTP_IO")
        PolkaswapIndexerClient(offline, json, cache)
    }

    @Before
    fun requireOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_PI_CLIENT_TESTS") == "true")
        cacheDirectory = Files.createTempDirectory("pi-live-cache").toFile()
    }

    @After
    fun deleteCache() {
        if (::cacheDirectory.isInitialized) cacheDirectory.deleteRecursively()
    }

    @Test
    fun `capabilities read on the deployed schema`() = runBlocking {
        val config = client.requireMobileConfig()
        assertThat(config.blockExplorerUrl).contains("{transaction}")
        assertThat(config.nodes).isNotEmpty()
    }

    @Test
    fun `prices liquidity and pool APYs read across many pages`() = runBlocking {
        val fiat = client.getFiat()
        assertThat(fiat.size).isGreaterThan(100)
        val ids = fiat.map { it.id }.take(150)
        assertThat(client.getAssetsInfo(ids).map { it.id }).containsExactlyElementsIn(ids)
        assertThat(client.getPoolApys()).isNotEmpty()
    }

    @Test
    fun `account history decodes, matches the chain and is served offline from the cache`() =
        runBlocking {
            val page = client.getTransactionHistoryQualified(ACTIVE_ACCOUNT, page = 1, pageCount = 100)
            assertThat(page.fromCache).isFalse()
            assertThat(page.value.items).isNotEmpty()
            PiHistoryCheckpointValidator.validate(
                elements = page.value.items,
                expectedAddress = ACTIVE_ACCOUNT,
                health = page.health,
                maximumRecords = 100,
                canonicalBlockHash = ::canonicalBlockHash,
            )

            val last = client.getLastTransactionsQualified(ACTIVE_ACCOUNT, count = 10)
            assertThat(last.value).isNotEmpty()
            PiHistoryCheckpointValidator.validate(
                elements = last.value,
                expectedAddress = ACTIVE_ACCOUNT,
                health = last.health,
                maximumRecords = 10,
                canonicalBlockHash = ::canonicalBlockHash,
            )

            val newest = page.value.items.first().id
            assertThat(client.getTransactionQualified(newest).value.map { it.id })
                .containsExactly(newest)

            // Offline, the same page comes from the bytes the production transport saved.
            val offline = offlineClient.getTransactionHistoryQualified(
                ACTIVE_ACCOUNT,
                page = 1,
                pageCount = 100,
            )
            assertThat(offline.fromCache).isTrue()
            assertThat(offline.value.items).isEqualTo(page.value.items)
            PiHistoryCheckpointValidator.validatePersisted(
                elements = offline.value.items,
                expectedAddress = ACTIVE_ACCOUNT,
                health = offline.health,
                maximumRecords = 100,
            )
        }

    @Test
    fun `referral rewards decode for a referrer that has rewards`() = runBlocking {
        val referrer = anyReferrerWithRewards()
        assertThat(client.getReferralRewards(referrer)).isNotEmpty()
        // An account with no rewards reads as an empty list, not an error.
        client.getReferralRewards(ACTIVE_ACCOUNT)
        Unit
    }

    /** chain_getBlockHash on the reviewed mainnet endpoint (block hashes are never pruned). */
    private suspend fun canonicalBlockHash(height: Int): String {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", height)
            put("method", "chain_getBlockHash")
            put("params", JsonArray(listOf(JsonPrimitive(height))))
        }
        val response = http.postJsonUtf8(
            rawUrl = MAINNET_RPC,
            requestBody = request.toString(),
            maximumBytes = 4_096,
        )
        return json.parseToJsonElement(response).jsonObject.getValue("result").jsonPrimitive.content
    }

    /**
     * Finds a referrer through a small read-only query (outside the app's query set), so the
     * test does not depend on one account keeping its rewards.
     */
    private suspend fun anyReferrerWithRewards(): String {
        val body = JsonObject(
            mapOf(
                "query" to JsonPrimitive(
                    "query { referrerRewards(first: 1, orderBy: [ID_ASC]) " +
                        "{ edges { node { referrer } } } }"
                ),
                "variables" to JsonObject(emptyMap()),
            )
        )
        val response = http.postJsonUtf8(
            rawUrl = INDEXER,
            requestBody = body.toString(),
            maximumBytes = 64 * 1_024,
        )
        return json.parseToJsonElement(response).jsonObject
            .getValue("data").jsonObject
            .getValue("referrerRewards").jsonObject
            .getValue("edges").jsonArray
            .first().jsonObject
            .getValue("node").jsonObject
            .getValue("referrer").jsonPrimitive.content
    }

    private companion object {
        // A public mainnet account with recent swaps; only used as a read-only query input.
        const val ACTIVE_ACCOUNT = "cnRwt3q7DkvJqr3YkuN7dFibTx6yu8rqDDYKmBp4Sko5TW2Dd"
        const val MAINNET_RPC = "https://ws.mof.sora.org"
        const val INDEXER = "https://pi.soramitsu.io/graphql"
    }
}
