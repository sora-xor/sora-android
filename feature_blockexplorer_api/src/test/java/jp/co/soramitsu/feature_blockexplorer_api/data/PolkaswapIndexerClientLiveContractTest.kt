package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig
import jp.co.soramitsu.xnetworking.lib.engines.rest.impl.RestClientImpl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * LIVE NETWORK TEST against the deployed PI indexer (https://pi.soramitsu.io/graphql), using the
 * app's own client with the production health preflight and postflight. Off by default and not
 * part of CI; run it with:
 *
 *     RUN_LIVE_PI_CLIENT_TESTS=true ./gradlew :feature_blockexplorer_api:testProductionDebugUnitTest \
 *       --tests '*PolkaswapIndexerClientLiveContractTest*' --rerun
 *
 * Keep `--rerun`: the environment variable is not a Gradle task input, so an up-to-date test
 * task would otherwise be skipped without contacting the indexer.
 *
 * It checks that every query the app sends is one the deployed schema serves.
 */
class PolkaswapIndexerClientLiveContractTest {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val rest: RestClient = RestClientImpl(
        restClientConfig = object : AbstractRestClientConfig() {
            override fun getConnectTimeoutMillis(): Long = 30_000L
            override fun getOrCreateJsonConfig(): Json = json
            override fun getRequestTimeoutMillis(): Long = 30_000L
            override fun getSocketTimeoutMillis(): Long = 30_000L
            override fun isLoggingEnabled(): Boolean = false
        }
    )

    private val client by lazy {
        PolkaswapIndexerClient(rest, json, enforceResponseCheckpointPostflight = true)
    }

    @Before
    fun requireOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_PI_CLIENT_TESTS") == "true")
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
    fun `account history and referral rewards use plans the indexer serves`() = runBlocking {
        client.getTransactionHistory(ACTIVE_ACCOUNT, page = 1, pageCount = 100)
        client.getLastTransactions(ACTIVE_ACCOUNT, count = 10)
        client.getReferralRewards(ACTIVE_ACCOUNT)
        Unit
    }

    private companion object {
        // A public mainnet account with recent swaps; only used as a read-only query input.
        const val ACTIVE_ACCOUNT = "cnRwt3q7DkvJqr3YkuN7dFibTx6yu8rqDDYKmBp4Sko5TW2Dd"
    }
}
