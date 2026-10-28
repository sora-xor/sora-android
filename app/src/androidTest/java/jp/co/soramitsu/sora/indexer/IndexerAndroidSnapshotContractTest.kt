@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.indexer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextResponse
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.UnknownHostException
import java.util.UUID

/** Strict production wire decoding and persistent response-cache restart on Android's runtime. */
@RunWith(AndroidJUnit4::class)
class IndexerAndroidSnapshotContractTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val now = 1_800_000_000_000L
    private val http = mockk<BoundedHttpTextClient>()
    private val requests = mutableListOf<JsonObject>()
    private lateinit var directory: File
    private lateinit var client: PolkaswapIndexerClient
    private var offline = false
    private var response: (String) -> String = { error("unexpected query: $it") }
    private val tx = "0x" + "ab".repeat(32)
    private val block = "0x" + "cd".repeat(32)
    private val market = """{"id":"market-7","marketId":7,"status":"Open","updatedAtBlock":100,"probability":"33.335","priceYes":"0.2","priceNo":"0.8"}"""
    private fun connection(name: String, node: String) = """{"data":{"$name":{"totalCount":1,"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[{"cursor":"one","node":$node}]}}}"""
    private fun history() = connection("historyElements", """{"id":"$tx","timestamp":100,"blockHash":"$block","blockHeight":100,"address":"cnAccount","dataFrom":"cnAccount","dataTo":"cnPeer","module":"Assets","method":"transfer","networkFee":"1000","execution":{"success":true},"data":{"amount":"12345678901234567890.1234","assetId":"xor"},"calls":{"nodes":[{"module":"Assets","method":"transfer","data":{"amount":"2"}}]}}""")
    private fun health() = """{"data":{"_health":{"ok":true,"repositoryReady":true,"service":"polkaswap-indexer","serviceId":"pi.soramitsu.io","schemaVersion":1,"ecosystem":"sora2","chainId":"sora:mainnet","network":"mainnet","publicBaseUrl":"https://pi.soramitsu.io/graphql","readOnly":true,"workerAvailable":true,"workerReady":true,"workerReadinessReason":null,"workerLifecycle":"running","workerStartupComplete":true,"workerLatestFinalizedBlock":26000005,"workerLatestIndexedBlock":26000001,"workerLag":4,"workerLastSuccessfulIndexTimestamp":1800000000,"workerLastError":null,"workerLastErrorTimestamp":null}}}"""
    private fun newClient() = PolkaswapIndexerClient(http, json, FilePiIndexerResponseCache(directory, json, { now }, Unit), { now }, { 1_000L })
    @Before fun setup() {
        directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "indexer-contract-${UUID.randomUUID()}")
        coEvery { http.postJson(any(), any(), any(), any(), any()) } coAnswers {
            if (offline) throw UnknownHostException("fixture offline")
            val body = json.parseToJsonElement(secondArg<String>()).jsonObject
            requests += body
            val query = body.getValue("query").jsonPrimitive.content
            BoundedHttpTextResponse(if (query.contains("_health")) health() else response(query), now)
        }
        client = newClient()
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun qualifiedHistorySurvivesCacheRestartWithItsExactCheckpointAndDecimalText() = runBlocking {
        response = { history() }
        val live = client.getTransactionHistoryQualified("cnAccount", 1, 20)
        assertFalse(live.fromCache); assertEquals("26000001", live.health.workerLatestIndexedBlock)
        val row = live.value.items.single()
        assertEquals(tx, row.id); assertEquals(block, row.blockHash); assertEquals("1000", row.networkFee)
        assertEquals("12345678901234567890.1234", row.data!!.single { it.paramName == "amount" }.paramValue)
        assertEquals("2", row.nestedData!!.single().data!!.single().paramValue)
        assertTrue(directory.listFiles()!!.isNotEmpty())
        offline = true
        val restored = newClient().getTransactionHistoryQualified("cnAccount", 1, 20)
        assertTrue(restored.fromCache); assertEquals(live.value, restored.value); assertEquals(live.health, restored.health)
        assertTrue(runCatching { newClient().getTransactionHistoryQualified("cnOther", 1, 20) }.isFailure)
    }
    @Test fun everyHistoryEntryPointKeepsTheRequestedAccountAndHash() = runBlocking {
        response = { history() }
        assertEquals(tx, client.getTransaction(tx).single().id)
        assertEquals(tx, client.getLastTransactions("cnAccount", 1).single().id)
        assertEquals(tx, client.getAllTransactionHistory("cnAccount").single().id)
        assertEquals(tx, client.getAccountTransactionsByHashesQualified("cnAccount", listOf(tx)).value.single().id)
        assertTrue(requests.any { it["variables"].toString().contains(tx) })
    }
    @Test fun assetAndRewardCatalogsKeepLargeQuantitiesExactAcrossAndroidSerialization() = runBlocking {
        response = { query -> when {
            query.contains("assets(") -> connection("assets", """{"id":"xor","priceUSD":"12345678901234567890.1234","strategicBonusApy":"1.5"}""")
            query.contains("poolXYKs(") -> connection("poolXYKs", """{"id":"pool","strategicBonusApy":"99.1234567890123456789"}""")
            query.contains("referrerRewards(") -> connection("referrerRewards", """{"id":"reward","referrer":"cnAccount","referral":"cnPeer","amount":"123456789012345678901234567890","blockHeight":"100"}""")
            else -> error(query)
        } }
        assertEquals("12345678901234567890.1234", client.getAssetsInfo(listOf("xor")).single().priceUSD)
        assertEquals("xor", client.getFiat().single().id)
        assertEquals("99.1234567890123456789", client.getPoolApys().single().value)
        val reward = client.getReferralRewards("cnAccount").single()
        assertEquals("cnPeer", reward.referral); assertEquals("123456789012345678901234567890", reward.amount)
    }
    @Test fun marketAndAccountCatalogsRetainNestedIdentityAndCheckpointSemantics() = runBlocking {
        response = { query -> when {
            query.contains("market(id:") -> """{"data":{"market":$market}}"""
            query.contains("marketSnapshots(") -> connection("marketSnapshots", """{"id":"snapshot","marketId":7,"timestamp":100,"blockHeight":100,"type":"DEFAULT","probability":"33.335"}""")
            query.contains("accountPositions(") -> connection("accountPositions", """{"id":"position","account":"cnAccount","marketId":7,"shares":"1.2345","market":$market}""")
            query.contains("accountTrades(") -> connection("accountTrades", """{"id":"trade","account":"cnAccount","marketId":7,"blockNumber":100,"blockHash":"$block","extrinsicHash":"$tx","market":$market}""")
            query.contains("markets(") -> connection("markets", market)
            else -> error(query)
        } }
        assertEquals(2000, client.getMarket("market-7")!!.displayYesProbabilityBps)
        assertEquals("market-7", client.getMarkets().single().id)
        assertEquals(7L, client.getMarketSnapshots(7).single().marketId)
        assertEquals("1.2345", client.getAccountPositions("cnAccount").single().shares)
        assertEquals("cnAccount", client.getAccountTrades("cnAccount").single().account)
        offline = true
        assertTrue(newClient().getAccountPositionsQualified("cnAccount").fromCache)
    }
    @Test fun capabilityReadDoesNotInventTypedBalancesOrEnableDisabledMutations() = runBlocking {
        response = { """{"data":{"mobileConfig":{"blockExplorerUrl":"https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}","substrateTypesUrl":"https://example.org/types.json","nodes":[{"name":"Sora","address":"wss://mof2.sora.org"}],"nexusAvailable":true,"nexusSendsAvailable":false,"polkamarktVisible":true,"polkamarktMutationsAvailable":false,"tairaDefaultVisible":true}}}""" }
        val config = client.requireMobileConfig()
        assertTrue(config.nexusAvailable); assertFalse(config.nexusSendsAvailable); assertFalse(config.polkamarktMutationsAvailable)
        assertEquals("wss://mof2.sora.org", config.nodes.single().address)
        assertEquals("PI_INDEXER_TYPED_ACCOUNT_BALANCES_UNAVAILABLE", runCatching { client.requireTypedAccountBalancesCapability() }.exceptionOrNull()?.message)
    }
    @Test fun strictWireDecoderRejectsDuplicateKeysAndNeverCachesThem() = runBlocking {
        response = { history().replace("\"totalCount\":1", "\"totalCount\":1,\"totalCount\":2") }
        assertTrue(runCatching { client.getTransactionHistoryQualified("cnAccount", 1, 20) }.isFailure)
        assertTrue(directory.listFiles().isNullOrEmpty())
        offline = true
        assertTrue(runCatching { newClient().getTransactionHistoryQualified("cnAccount", 1, 20) }.isFailure)
    }
}
