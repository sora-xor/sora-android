package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestServerRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class PolkaswapIndexerClientTest {

    private val restClient = CapturingRestClient()
    private val client = PolkaswapIndexerClient(restClient, testJson)

    @Test
    fun `transaction history maps loose json values and sends account filter`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "tx-1",
                        "timestamp": 123,
                        "blockHash": "block-1",
                        "module": "Assets",
                        "method": "transfer",
                        "address": "issuer",
                        "dataFrom": "from-peer",
                        "dataTo": "to-peer",
                        "networkFee": "9",
                        "execution": { "success": false, "extra": "ignored" },
                        "data": {
                          "from": "from-peer",
                          "to": "to-peer",
                          "amount": 42,
                          "assetId": "asset-1",
                          "nullValue": null,
                          "objectValue": { "nested": true }
                        },
                        "calls": {
                          "nodes": [
                            {
                              "module": "PoolXYK",
                              "method": "deposit_liquidity",
                              "data": {
                                "input_a_desired": "7",
                                "objectParam": { "a": 1 }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val result = client.getTransactionHistory(
            address = "cnAccount",
            page = 3,
            pageCount = 25,
        )

        assertThat(result.endReached).isTrue()
        assertThat(result.items).hasSize(1)
        val item = result.items.single()
        assertThat(item.id).isEqualTo("tx-1")
        assertThat(item.blockHash).isEqualTo("block-1")
        assertThat(item.address).isEqualTo("issuer")
        assertThat(item.dataFrom).isEqualTo("from-peer")
        assertThat(item.dataTo).isEqualTo("to-peer")
        assertThat(item.timestamp).isEqualTo("123")
        assertThat(item.networkFee).isEqualTo("9")
        assertThat(item.success).isFalse()
        assertThat(item.data).contains(IndexerHistoryItemParam("amount", "42"))
        assertThat(item.data).contains(IndexerHistoryItemParam("nullValue", ""))
        assertThat(item.data?.first { it.paramName == "objectValue" }?.paramValue)
            .contains("\"nested\":true")
        assertThat(item.nestedData).hasSize(1)
        assertThat(item.nestedData?.single()?.data?.first { it.paramName == "objectParam" }?.paramValue)
            .contains("\"a\":1")

        val request = restClient.singleRequest()
        assertThat(request.url).isEqualTo(OptionsProvider.polkaswapIndexerEndpoint)
        assertThat(request.contentType).isEqualTo(RestClient.ContentType.JSON)
        assertThat(request.query).contains("historyElements")
        assertThat(request.variables.toString()).contains("\"first\":25")
        assertThat(request.variables.toString()).contains("\"offset\":50")
        assertThat(request.variables.toString()).contains("\"dataFrom\":{\"equalTo\":\"cnAccount\"}")
        assertThat(request.variables.toString()).contains("\"dataTo\":{\"equalTo\":\"cnAccount\"}")
        assertThat(request.variables.toString()).contains("\"address\":{\"equalTo\":\"cnAccount\"}")
    }

    @Test
    fun `transaction history clamps zero and negative pages to zero offset`() = runTest {
        restClient.enqueue(emptyHistoryResponse(hasNextPage = true))

        val result = client.getTransactionHistory(
            address = "cnAccount",
            page = -5,
            pageCount = 10,
        )

        assertThat(result.endReached).isFalse()
        assertThat(result.items).isEmpty()
        assertThat(restClient.singleRequest().variables.toString()).contains("\"offset\":0")
    }

    @Test
    fun `blank transaction peer query skips network`() = runTest {
        val result = client.getTransactionPeers(" \n\t ")

        assertThat(result).isEmpty()
        assertThat(restClient.requests).isEmpty()
    }

    @Test
    fun `transaction peers include dataFrom dataTo address and from-to params without duplicates`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "1", "timestamp": 1, "dataFrom": "AliceABC", "dataTo": "", "address": "", "data": {} } },
                    { "node": { "id": "2", "timestamp": 1, "dataFrom": "miss", "dataTo": "xxABCxx", "address": "xxABCxx", "data": {} } },
                    { "node": { "id": "3", "timestamp": 1, "dataFrom": "AliceABC", "dataTo": "", "address": null, "data": { "from": "otherABC", "to": "no-match" } } },
                    { "node": { "id": "4", "timestamp": 1, "address": null, "data": { "from": "abcDEF", "memo": "abc should not be used" } } }
                  ]
                }
              }
            }
            """
        )

        val result = client.getTransactionPeers("abc")

        assertThat(result).containsExactly(
            "AliceABC",
            "xxABCxx",
            "otherABC",
            "abcDEF",
        )
        val request = restClient.singleRequest()
        assertThat(request.variables.toString()).contains("\"includesInsensitive\":\"abc\"")
    }

    @Test
    fun `transaction peers ignore matches from unrelated data fields`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "1", "timestamp": 1, "address": null, "data": { "memo": "abc", "amount": "abc" } } }
                  ]
                }
              }
            }
            """
        )

        assertThat(client.getTransactionPeers("abc")).isEmpty()
    }

    @Test
    fun `transaction lookup uses exact hash filter`() = runTest {
        restClient.enqueue(emptyHistoryResponse(hasNextPage = false))

        assertThat(client.getTransaction("0xhash")).isEmpty()

        val request = restClient.singleRequest()
        assertThat(request.variables.toString()).contains("\"id\":{\"equalTo\":\"0xhash\"}")
        assertThat(request.variables.toString()).contains("\"first\":1")
    }

    @Test
    fun `assets info skips network for empty ids`() = runTest {
        assertThat(client.getAssetsInfo(emptyList())).isEmpty()
        assertThat(restClient.requests).isEmpty()
    }

    @Test
    fun `assets info sends id in filter and decodes null market fields`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "assets": {
                  "edges": [
                    { "node": { "id": "asset-1", "priceUSD": null, "liquidity": "100", "priceChangeDay": null } },
                    { "node": { "id": "asset-2", "priceUSD": "2.5", "liquidity": null, "priceChangeDay": -4.5 } }
                  ]
                }
              }
            }
            """
        )

        val result = client.getAssetsInfo(listOf("asset-1", "asset-2"))

        assertThat(result).containsExactly(
            IndexerAssetInfo("asset-1", null, "100", null),
            IndexerAssetInfo("asset-2", "2.5", null, -4.5),
        ).inOrder()
        val variables = restClient.singleRequest().variables.toString()
        assertThat(variables).contains("\"first\":2")
        assertThat(variables).contains("\"id\":{\"in\":[\"asset-1\",\"asset-2\"]}")
    }

    @Test
    fun `fiat query sends null filter with default connection limit`() = runTest {
        restClient.enqueue(
            """
            { "data": { "assets": { "edges": [
              { "node": { "id": "xor", "priceUSD": "1", "liquidity": "2", "priceChangeDay": 3.0 } }
            ] } } }
            """
        )

        assertThat(client.getFiat()).containsExactly(IndexerAssetInfo("xor", "1", "2", 3.0))

        val variables = restClient.singleRequest().variables.toString()
        assertThat(variables).contains("\"first\":500")
        assertThat(variables).contains("\"filter\":null")
    }

    @Test
    fun `referral rewards coerce missing referral and amount to empty strings`() = runTest {
        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "referral": null, "amount": null } },
              { "node": { "referral": "cnReferral", "amount": "123" } }
            ] } } }
            """
        )

        assertThat(client.getReferralRewards("cnReferrer")).containsExactly(
            IndexerReferralReward("", ""),
            IndexerReferralReward("cnReferral", "123"),
        ).inOrder()
        assertThat(restClient.singleRequest().variables.toString())
            .contains("\"referrer\":{\"equalTo\":\"cnReferrer\"}")
    }

    @Test
    fun `pool apys allow null strategic bonus apy`() = runTest {
        restClient.enqueue(
            """
            { "data": { "poolXYKs": { "edges": [
              { "node": { "id": "pool-1", "strategicBonusApy": null } },
              { "node": { "id": "pool-2", "strategicBonusApy": "0.123" } }
            ] } } }
            """
        )

        assertThat(client.getPoolApys()).containsExactly(
            IndexerPoolApy("pool-1", null),
            IndexerPoolApy("pool-2", "0.123"),
        ).inOrder()
    }

    @Test
    fun `graphql errors fail with all returned messages`() = runTest {
        restClient.enqueue(
            """
            {
              "errors": [
                { "message": "bad filter" },
                { "message": "schema rejected request" }
              ]
            }
            """
        )

        val error = runCatching {
            client.getFiat()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("bad filter")
        assertThat(error).hasMessageThat().contains("schema rejected request")
    }

    @Test
    fun `missing graphql data fails instead of returning defaults`() = runTest {
        restClient.enqueue("""{ "data": null }""")

        val error = runCatching {
            client.getPoolApys()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `non object history data is treated as absent params`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "array-data", "timestamp": 1, "data": ["unexpected"], "calls": { "nodes": [ { "data": ["unexpected"] } ] } } }
                  ]
                }
              }
            }
            """
        )

        val item = client.getLastTransactions("cnAccount", 1).single()

        assertThat(item.data).isNull()
        assertThat(item.nestedData).hasSize(1)
        assertThat(item.nestedData?.single()?.data).isEmpty()
    }

    private fun emptyHistoryResponse(hasNextPage: Boolean) =
        """
        {
          "data": {
            "historyElements": {
              "pageInfo": { "hasNextPage": $hasNextPage },
              "edges": []
            }
          }
        }
        """

    private data class CapturedGraphQlRequest(
        val url: String,
        val contentType: RestClient.ContentType,
        val query: String,
        val variables: JsonObject,
    )

    private class CapturingRestClient : RestClient() {
        val requests = mutableListOf<CapturedGraphQlRequest>()
        private val responses = ArrayDeque<String>()

        fun enqueue(response: String) {
            responses.addLast(response.trimIndent())
        }

        fun singleRequest(): CapturedGraphQlRequest = requests.single()

        override suspend fun <T> post(request: AbstractRestServerRequest.WithBody<T>): T {
            requests += CapturedGraphQlRequest(
                url = request.url,
                contentType = request.requestContentType,
                query = request.body.readField("query") as String,
                variables = request.body.readField("variables") as JsonObject,
            )

            val response = if (responses.isEmpty()) {
                error("No queued response for ${request.body.readField("query")}")
            } else {
                responses.removeFirst()
            }

            return testJson.decodeFromString(request.responseDeserializer, response)
        }

        override suspend fun postAsString(request: AbstractRestServerRequest.WithBody<String>): String =
            error("postAsString is not used by PolkaswapIndexerClient")

        override suspend fun <T> get(request: AbstractRestServerRequest<T>): T =
            error("get is not used by PolkaswapIndexerClient")

        override suspend fun getAsString(request: AbstractRestServerRequest<String>): String =
            error("getAsString is not used by PolkaswapIndexerClient")
    }

    private companion object {
        val testJson = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        fun Any.readField(name: String): Any? {
            val field = javaClass.getDeclaredField(name)
            field.isAccessible = true
            return field.get(this)
        }
    }
}
