package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.net.UnknownHostException
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestServerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class PolkaswapIndexerClientTest {

    private val restClient = CapturingRestClient()
    private val client = PolkaswapIndexerClient(
        restClient,
        testJson,
        disableChainIdentityPreflightForTests = Unit,
    )

    @Test
    fun `transaction history maps loose json values and sends account filter`() = runTest {
        val rawTransactionId = "AB".repeat(32)
        val rawBlockHash = "0x${"CD".repeat(32)}"
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "$rawTransactionId",
                        "timestamp": 123,
                        "blockHash": "$rawBlockHash",
                        "blockHeight": 99,
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
                          "totalCount": 1,
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
            page = 1,
            pageCount = 25,
        )

        assertThat(result.endReached).isTrue()
        assertThat(result.items).hasSize(1)
        val item = result.items.single()
        assertThat(item.id).isEqualTo("0x${rawTransactionId.lowercase()}")
        assertThat(item.blockHash).isEqualTo(rawBlockHash.lowercase())
        assertThat(item.blockHeight).isEqualTo(99L)
        assertThat(item.address).isEqualTo("issuer")
        assertThat(item.dataFrom).isEqualTo("from-peer")
        assertThat(item.dataTo).isEqualTo("to-peer")
        assertThat(item.timestamp).isEqualTo("123")
        assertThat(item.networkFee).isEqualTo("9")
        assertThat(item.success).isFalse()
        assertThat(item.executionKnown).isTrue()
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
        assertThat(request.query).contains("blockHeight")
        assertThat(request.query).contains("calls(first: 100)")
        assertThat(request.query).doesNotContain("offset")
        assertThat(request.variables.toString()).contains("\"first\":25")
        assertThat(request.variables.toString()).contains("\"after\":null")
        assertThat(request.variables.toString()).contains("\"dataFrom\":{\"equalTo\":\"cnAccount\"}")
        assertThat(request.variables.toString()).contains("\"dataTo\":{\"equalTo\":\"cnAccount\"}")
        assertThat(request.variables.toString()).contains("\"address\":{\"equalTo\":\"cnAccount\"}")
    }

    @Test
    fun `numbered history compatibility walks bounded cursors without offsets`() = runTest {
        fun page(
            transactionByte: String,
            blockByte: String,
            hasNextPage: Boolean,
            endCursor: String?,
            totalCount: Int = 2,
        ) =
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": $totalCount,
                  "pageInfo": {
                    "hasNextPage": $hasNextPage,
                    "endCursor": ${endCursor?.let { "\"$it\"" } ?: "null"}
                  },
                  "edges": [
                    {
                      "node": {
                        "id": "${transactionByte.repeat(32)}",
                        "timestamp": 1,
                        "blockHash": "0x${blockByte.repeat(32)}",
                        "blockHeight": 1,
                        "address": "cnAccount",
                        "networkFee": "0",
                        "execution": { "success": true }
                      }
                    }
                  ]
                }
              }
            }
            """

        restClient.enqueue(page("ab", "cd", true, "history-cursor-1"))
        restClient.enqueue(page("ef", "12", false, null))

        val result = client.getTransactionHistory(
            address = "cnAccount",
            page = 2,
            pageCount = 1,
        )

        assertThat(result.items.single().id).isEqualTo("0x${"ef".repeat(32)}")
        assertThat(result.endReached).isTrue()
        assertThat(restClient.requests).hasSize(2)
        assertThat(restClient.requests[0].query).doesNotContain("offset")
        assertThat(restClient.requests[0].variables.toString())
            .contains("\"after\":null")
        assertThat(restClient.requests[1].variables.toString())
            .contains("\"after\":\"history-cursor-1\"")

        restClient.enqueue(page("34", "56", true, "history-cursor-2", totalCount = 3))
        restClient.enqueue(page("78", "90", false, null, totalCount = 3))
        val truncated = runCatching {
            client.getTransactionHistory(
                address = "cnAccount",
                page = 2,
                pageCount = 1,
            )
        }.exceptionOrNull()
        assertThat(truncated).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_PAGE")
    }

    @Test
    fun `transaction history rejects missing or non boolean execution result`() = runTest {
        val invalidExecutionFields = listOf(
            "",
            ", \"execution\": { \"success\": \"true\" }",
        )

        invalidExecutionFields.forEach { executionField ->
            restClient.enqueue(
                """
                {
                  "data": {
                    "historyElements": {
                      "totalCount": 1,
                      "pageInfo": { "hasNextPage": false },
                      "edges": [
                        {
                          "node": {
                            "id": "${"ab".repeat(32)}",
                            "timestamp": 1,
                            "blockHash": "0x${"cd".repeat(32)}",
                            "blockHeight": 1,
                            "address": "cnAccount",
                            "networkFee": "0"
                            $executionField
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )

            val error = runCatching {
                client.getTransactionHistory(
                    address = "cnAccount",
                    page = 1,
                    pageCount = 1,
                )
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_EXECUTION_INVALID")
        }
    }

    @Test
    fun `transaction history rejects missing malformed negative or decimal fee`() = runTest {
        val invalidFeeFields = listOf(
            "",
            ", \"networkFee\": \"not-a-number\"",
            ", \"networkFee\": \"-1\"",
            ", \"networkFee\": \"0.1\"",
            ", \"networkFee\": \"01\"",
        )

        invalidFeeFields.forEach { feeField ->
            restClient.enqueue(
                """
                {
                  "data": {
                    "historyElements": {
                      "totalCount": 1,
                      "pageInfo": { "hasNextPage": false },
                      "edges": [
                        {
                          "node": {
                            "id": "${"ab".repeat(32)}",
                            "address": "cnAccount",
                            "timestamp": 1,
                            "execution": { "success": true }
                            $feeField
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )

            val error = runCatching {
                client.getTransactionHistory(
                    address = "cnAccount",
                    page = 1,
                    pageCount = 1,
                )
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_FEE_INVALID")
        }
    }

    @Test
    fun `transaction history rejects missing or negative timestamp`() = runTest {
        val invalidTimestampFields = listOf(
            "",
            ", \"timestamp\": -1",
        )

        invalidTimestampFields.forEach { timestampField ->
            restClient.enqueue(
                """
                {
                  "data": {
                    "historyElements": {
                      "totalCount": 1,
                      "pageInfo": { "hasNextPage": false },
                      "edges": [
                        {
                          "node": {
                            "id": "${"ab".repeat(32)}",
                            "address": "cnAccount",
                            "networkFee": "0",
                            "execution": { "success": true }
                            $timestampField
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )

            val error = runCatching {
                client.getTransactionHistory(
                    address = "cnAccount",
                    page = 1,
                    pageCount = 1,
                )
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_TIMESTAMP_INVALID")
        }
    }

    @Test
    fun `transaction history rejects invalid page before network`() = runTest {
        val error = runCatching {
            client.getTransactionHistory(
                address = "cnAccount",
                page = -5,
                pageCount = 10,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("PI_INDEXER_PAGE_LIMIT_EXCEEDED")
        assertThat(restClient.requests).isEmpty()
    }

    @Test
    fun `general history accepts synthetic bridge ids but exact lookup remains hash only`() = runTest {
        val transactionHash = "0x${"ab".repeat(32)}"
        val syntheticId = "$transactionHash-mint"
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "$syntheticId",
                        "timestamp": 1,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "blockHeight": 1,
                        "address": "cnAccount",
                        "networkFee": "0",
                        "execution": { "success": true }
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val page = client.getTransactionHistory(
            address = "cnAccount",
            page = 1,
            pageCount = 1,
        )

        assertThat(page.items.single().id).isEqualTo(syntheticId)
        val requestCount = restClient.requests.size
        val lookupError = runCatching {
            client.getTransaction(syntheticId)
        }.exceptionOrNull()
        assertThat(lookupError).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(lookupError).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_TRANSACTION_ID")
        assertThat(restClient.requests).hasSize(requestCount)
    }

    @Test
    fun `transaction history rejects malformed transaction and block identities`() = runTest {
        listOf(
            Pair(
                """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": " tx-1", "timestamp": 1, "networkFee": "0", "execution": { "success": true } } }
                  ]
                }
              }
            }
            """,
                "PI_INDEXER_INVALID_IDENTIFIER",
            ),
            Pair(
                """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "timestamp": 1,
                        "blockHash": "block-1",
                        "blockHeight": 1,
                        "networkFee": "0",
                        "execution": { "success": true }
                      }
                    }
                  ]
                }
              }
            }
            """,
                "PI_INDEXER_INVALID_BLOCK_HASH",
            ),
            Pair(
                """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "${"x".repeat(513)}",
                        "timestamp": 1,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "blockHeight": 1,
                        "networkFee": "0",
                        "execution": { "success": true }
                      }
                    }
                  ]
                }
              }
            }
            """,
                "PI_INDEXER_INVALID_IDENTIFIER",
            ),
        ).forEach { (response, expectedError) ->
            restClient.enqueue(response)

            val error = runCatching {
                client.getLastTransactions("cnAccount", 1)
            }.exceptionOrNull()

            assertThat(error).hasMessageThat()
                .isEqualTo(expectedError)
        }
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
                  "totalCount": 4,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "0x1111111111111111111111111111111111111111111111111111111111111111", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "dataFrom": "AliceABC", "dataTo": "", "address": "", "data": {} } },
                    { "node": { "id": "0x2222222222222222222222222222222222222222222222222222222222222222", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "dataFrom": "miss", "dataTo": "xxABCxx", "address": "xxABCxx", "data": {} } },
                    { "node": { "id": "0x3333333333333333333333333333333333333333333333333333333333333333", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "dataFrom": "AliceABC", "dataTo": "", "address": null, "data": { "from": "otherABC", "to": "no-match" } } },
                    { "node": { "id": "0x4444444444444444444444444444444444444444444444444444444444444444", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "address": null, "data": { "from": "abcDEF", "memo": "abc should not be used" } } }
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
    fun `transaction peer search walks bounded cursors without truncating contacts`() = runTest {
        restClient.enqueue(
            """
            { "data": { "historyElements": {
              "totalCount": 2,
              "pageInfo": { "hasNextPage": true, "endCursor": "peer-cursor-1" },
              "edges": [ { "node": {
                "id": "0x${"11".repeat(32)}", "timestamp": 1,
                "networkFee": "0", "execution": { "success": true },
                "dataFrom": "firstABC"
              } } ]
            } } }
            """
        )
        restClient.enqueue(
            """
            { "data": { "historyElements": {
              "totalCount": 2,
              "pageInfo": { "hasNextPage": false },
              "edges": [ { "node": {
                "id": "0x${"22".repeat(32)}", "timestamp": 1,
                "networkFee": "0", "execution": { "success": true },
                "dataTo": "secondABC"
              } } ]
            } } }
            """
        )

        assertThat(client.getTransactionPeers("abc"))
            .containsExactly("firstABC", "secondABC")
        assertThat(restClient.requests).hasSize(2)
        assertThat(restClient.requests[0].variables.toString()).contains("\"after\":null")
        assertThat(restClient.requests[1].variables.toString())
            .contains("\"after\":\"peer-cursor-1\"")
    }

    @Test
    fun `transaction peers ignore matches from unrelated data fields`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "0x1111111111111111111111111111111111111111111111111111111111111111", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "address": null, "data": { "memo": "abc", "amount": "abc" } } }
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
        val hash = "0x" + "ab".repeat(32)

        assertThat(client.getTransaction(hash)).isEmpty()

        val request = restClient.singleRequest()
        assertThat(request.variables.toString()).contains("\"id\":{\"equalTo\":\"$hash\"}")
        assertThat(request.variables.toString()).contains("\"first\":1")

        restClient.enqueue(
            """
            { "data": { "historyElements": {
              "totalCount": 2,
              "pageInfo": { "hasNextPage": true, "endCursor": "ambiguous-hash" },
              "edges": [ { "node": {
                "id": "$hash", "timestamp": 1, "networkFee": "0",
                "execution": { "success": true }
              } } ]
            } } }
            """
        )
        val ambiguous = runCatching { client.getTransaction(hash) }.exceptionOrNull()
        assertThat(ambiguous).hasMessageThat()
            .isEqualTo("PI_INDEXER_TRANSACTION_LOOKUP_AMBIGUOUS")
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
                  ],
                  "pageInfo": { "hasNextPage": false },
                  "totalCount": 2
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
        assertThat(variables).contains("\"after\":null")
        assertThat(variables).contains("\"id\":{\"in\":[\"asset-1\",\"asset-2\"]}")
    }

    @Test
    fun `fiat query walks bounded cursors without truncating prices`() = runTest {
        restClient.enqueue(
            """
            { "data": { "assets": { "edges": [
              { "node": { "id": "xor", "priceUSD": "1", "liquidity": "2", "priceChangeDay": 3.0 } }
            ], "pageInfo": { "hasNextPage": true, "endCursor": "asset-cursor-1" }, "totalCount": 2 } } }
            """
        )
        restClient.enqueue(
            """
            { "data": { "assets": { "edges": [
              { "node": { "id": "val", "priceUSD": "4", "liquidity": "5", "priceChangeDay": 6.0 } }
            ], "pageInfo": { "hasNextPage": false, "endCursor": null }, "totalCount": 2 } } }
            """
        )

        assertThat(client.getFiat()).containsExactly(
            IndexerAssetInfo("xor", "1", "2", 3.0),
            IndexerAssetInfo("val", "4", "5", 6.0),
        ).inOrder()

        assertThat(restClient.requests).hasSize(2)
        assertThat(restClient.requests[0].variables.toString()).contains("\"first\":100")
        assertThat(restClient.requests[0].variables.toString()).contains("\"after\":null")
        assertThat(restClient.requests[0].variables.toString()).contains("\"filter\":null")
        assertThat(restClient.requests[1].variables.toString())
            .contains("\"after\":\"asset-cursor-1\"")
        assertThat(restClient.requests[0].query).contains("pageInfo { hasNextPage endCursor }")
        assertThat(restClient.requests[0].query).contains("totalCount")
    }

    @Test
    fun `asset reads reject returned ids outside the request and inexact prices`() = runTest {
        restClient.enqueue(
            """
            { "data": { "assets": { "edges": [
              { "node": { "id": "asset-2", "priceUSD": "1", "liquidity": "2" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )
        val identityError = runCatching {
            client.getAssetsInfo(listOf("asset-1"))
        }.exceptionOrNull()
        assertThat(identityError).hasMessageThat()
            .isEqualTo("PI_INDEXER_ASSET_IDENTITY_MISMATCH")

        restClient.enqueue(
            """
            { "data": { "assets": { "edges": [
              { "node": { "id": "asset-1", "priceUSD": "1e3", "liquidity": "2" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )
        val priceError = runCatching {
            client.getAssetsInfo(listOf("asset-1"))
        }.exceptionOrNull()
        assertThat(priceError).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_PRICE")

    }

    @Test
    fun `referral rewards reject missing values and walk bounded cursors`() = runTest {
        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "id": "reward-missing", "referrer": "cnReferrer", "referral": null, "blockHeight": "1", "amount": null } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )

        val missingError = runCatching {
            client.getReferralRewards("cnReferrer")
        }.exceptionOrNull()
        assertThat(missingError).hasMessageThat()
            .isEqualTo("PI_INDEXER_REFERRAL_IDENTITY_MISSING")

        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "id": "reward-missing-amount", "referrer": "cnReferrer", "referral": "cnReferral", "blockHeight": "1", "amount": null } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )
        val missingAmountError = runCatching {
            client.getReferralRewards("cnReferrer")
        }.exceptionOrNull()
        assertThat(missingAmountError).hasMessageThat()
            .isEqualTo("PI_INDEXER_REWARD_AMOUNT_MISSING")

        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "id": "reward-1", "referrer": "cnReferrer", "referral": "cnReferral1", "blockHeight": "1", "amount": "123" } }
            ], "pageInfo": { "hasNextPage": true, "endCursor": "reward-cursor-1" }, "totalCount": 2 } } }
            """
        )
        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "id": "reward-2", "referrer": "cnReferrer", "referral": "cnReferral2", "blockHeight": "2", "amount": "456" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 2 } } }
            """
        )
        assertThat(client.getReferralRewards("cnReferrer")).containsExactly(
            IndexerReferralReward("cnReferral1", "123"),
            IndexerReferralReward("cnReferral2", "456"),
        ).inOrder()
        assertThat(restClient.requests).hasSize(4)
        assertThat(restClient.requests[2].variables.toString())
            .contains("\"referrer\":{\"equalTo\":\"cnReferrer\"}")
        assertThat(restClient.requests[3].variables.toString())
            .contains("\"after\":\"reward-cursor-1\"")
    }

    @Test
    fun `referral rewards reject another returned referrer`() = runTest {
        restClient.enqueue(
            """
            { "data": { "referrerRewards": { "edges": [
              { "node": { "id": "reward-1", "referrer": "cnOther", "referral": "cnReferral", "blockHeight": "1", "amount": "1" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )

        val error = runCatching {
            client.getReferralRewards("cnReferrer")
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_REFERRER_IDENTITY_MISMATCH")
    }

    @Test
    fun `pool apys allow null values while walking bounded cursors`() = runTest {
        restClient.enqueue(
            """
            { "data": { "poolXYKs": { "edges": [
              { "node": { "id": "pool-1", "strategicBonusApy": null } }
            ], "pageInfo": { "hasNextPage": true, "endCursor": "pool-cursor-1" }, "totalCount": 2 } } }
            """
        )
        restClient.enqueue(
            """
            { "data": { "poolXYKs": { "edges": [
              { "node": { "id": "pool-2", "strategicBonusApy": "0.123" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 2 } } }
            """
        )

        assertThat(client.getPoolApys()).containsExactly(
            IndexerPoolApy("pool-1", null),
            IndexerPoolApy("pool-2", "0.123"),
        ).inOrder()
        assertThat(restClient.requests).hasSize(2)
        assertThat(restClient.requests[1].variables.toString())
            .contains("\"after\":\"pool-cursor-1\"")
        assertThat(restClient.requests[0].variables.toString()).doesNotContain("\"filter\"")
    }

    @Test
    fun `pool apys reject negative values`() = runTest {
        restClient.enqueue(
            """
            { "data": { "poolXYKs": { "edges": [
              { "node": { "id": "pool-1", "strategicBonusApy": "-0.1" } }
            ], "pageInfo": { "hasNextPage": false }, "totalCount": 1 } } }
            """
        )

        val error = runCatching {
            client.getPoolApys()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_POOL_APY")
    }

    @Test
    fun `graphql errors fail without exposing server supplied values`() = runTest {
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
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_GRAPHQL_ERROR")
        assertThat(error).hasMessageThat().doesNotContain("bad filter")
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
    fun `cursor connections reject missing pagination metadata instead of defaulting empty`() =
        runTest {
            listOf(
                """{ "data": { "assets": { "edges": [], "totalCount": 0 } } }""",
                """{ "data": { "assets": { "edges": [], "pageInfo": { "hasNextPage": false } } } }""",
            ).forEach { response ->
                restClient.enqueue(response)
                val error = runCatching { client.getFiat() }.exceptionOrNull()
                assertThat(error).isInstanceOf(SerializationException::class.java)
            }
        }

    @Test
    fun `non object history data is treated as absent params`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "data": ["unexpected"], "calls": { "totalCount": 1, "nodes": [ { "data": ["unexpected"] } ] } } }
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

        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    { "node": { "id": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "timestamp": 1, "networkFee": "0", "execution": { "success": true }, "calls": { "totalCount": 2, "nodes": [ { "data": {} } ] } } }
                  ]
                }
              }
            }
            """
        )
        val truncatedCalls = runCatching {
            client.getLastTransactions("cnAccount", 1)
        }.exceptionOrNull()
        assertThat(truncatedCalls).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_CALLS_TRUNCATED")
    }

    @Test
    fun `health rejects an indexer connected to a different chain`() = runTest {
        restClient.enqueue(healthResponse(chainId = "wrong-chain"))

        val error = runCatching {
            client.requireHealthy(
                expectedChainId = "expected-chain",
                nowEpochSeconds = HEALTH_NOW,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("PI_INDEXER_CHAIN_MISMATCH")
    }

    @Test
    fun `health accepts a fresh coherent production checkpoint`() = runTest {
        restClient.enqueue(healthResponse())

        val health = client.requireHealthy(nowEpochSeconds = HEALTH_NOW)

        assertThat(health.latestIndexedBlock).isNull()
        assertThat(health.workerLatestIndexedBlock).isEqualTo(HEALTH_BLOCK.toString())
        assertThat(health.workerLatestFinalizedBlock)
            .isEqualTo((HEALTH_BLOCK + 4).toString())
        assertThat(restClient.singleRequest().query)
            .contains("workerLastSuccessfulIndexTimestamp")
        assertThat(restClient.singleRequest().query).doesNotContain("genesisHash")
        assertThat(restClient.singleRequest().query).doesNotContain("latestIndexedBlockHash")
    }

    @Test
    fun `health accepts deployed numeric and canonical quoted worker integer tokens`() = runTest {
        val deployedNumericResponse = healthResponse()
        val canonicalQuotedResponse = deployedNumericResponse
            .replace(
                "\"workerLatestFinalizedBlock\": ${HEALTH_BLOCK + 4}",
                "\"workerLatestFinalizedBlock\": \"${HEALTH_BLOCK + 4}\"",
            )
            .replace(
                "\"workerLatestIndexedBlock\": $HEALTH_BLOCK",
                "\"workerLatestIndexedBlock\": \"$HEALTH_BLOCK\"",
            )
            .replace(
                "\"workerLag\": 4",
                "\"workerLag\": \"4\"",
            )
            .replace(
                "\"workerLastSuccessfulIndexTimestamp\": $HEALTH_NOW",
                "\"workerLastSuccessfulIndexTimestamp\": \"$HEALTH_NOW\"",
            )

        restClient.enqueue(deployedNumericResponse)
        val numeric = client.getHealth()
        restClient.enqueue(canonicalQuotedResponse)
        val quoted = client.getHealth()

        assertThat(quoted).isEqualTo(numeric)
        assertThat(numeric.workerLatestIndexedBlock).isEqualTo(HEALTH_BLOCK.toString())
        assertThat(numeric.workerLastSuccessfulIndexTimestamp)
            .isEqualTo(HEALTH_NOW.toString())
    }

    @Test
    fun `health rejects stale and incoherent checkpoints`() = runTest {
        restClient.enqueue(healthResponse(indexedAt = HEALTH_NOW - 301))

        val stale = runCatching {
            client.requireHealthy(nowEpochSeconds = HEALTH_NOW)
        }.exceptionOrNull()

        assertThat(stale).hasMessageThat().contains("PI_INDEXER_CHECKPOINT_STALE")

        restClient.enqueue(
            healthResponse(
                workerIndexedBlock = HEALTH_BLOCK - 1,
                workerLag = 4,
            )
        )
        val incoherent = runCatching {
            client.requireHealthy(nowEpochSeconds = HEALTH_NOW)
        }.exceptionOrNull()

        assertThat(incoherent).hasMessageThat().contains("PI_INDEXER_CHECKPOINT_MISMATCH")
    }

    @Test
    fun `health rejects non canonical or oversized checkpoint integers`() = runTest {
        val indexedCheckpoint =
            "\"workerLatestIndexedBlock\": $HEALTH_BLOCK"
        restClient.enqueue(
            healthResponse().replace(
                indexedCheckpoint,
                "\"workerLatestIndexedBlock\": 2.7e7",
            )
        )

        val exponent = runCatching {
            client.requireHealthy(nowEpochSeconds = HEALTH_NOW)
        }.exceptionOrNull()

        assertThat(exponent)
            .hasMessageThat()
            .contains("PI_INDEXER_INTEGER_LEXEME_INVALID")

        restClient.enqueue(
            healthResponse().replace(
                indexedCheckpoint,
                "\"workerLatestIndexedBlock\": 99999999999999999999",
            )
        )
        val oversized = runCatching {
            client.requireHealthy(nowEpochSeconds = HEALTH_NOW)
        }.exceptionOrNull()

        assertThat(oversized)
            .hasMessageThat()
            .contains("PI_INDEXER_WORKER_CHECKPOINT_INVALID")
    }

    @Test
    fun `response block heights cannot exceed attributed PI checkpoint`() = runTest {
        restClient.enqueue(healthResponse())
        val health = client.requireHealthy(nowEpochSeconds = HEALTH_NOW)

        PiResponseCheckpointValidator.requireBounded(
            blockHeights = listOf(null, 0L, HEALTH_BLOCK),
            health = health,
        )
        val error = runCatching {
            PiResponseCheckpointValidator.requireBounded(
                blockHeights = listOf(HEALTH_BLOCK + 1),
                health = health,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_RESPONSE_BLOCK_AFTER_CHECKPOINT")
    }

    @Test
    fun `qualified history rejects a row after its attributed PI checkpoint`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            qualifiedRestClient,
            testJson,
        )
        qualifiedRestClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        qualifiedRestClient.enqueue(
            historyResponse(
                address = "cnAccount",
                blockHeight = HEALTH_BLOCK + 1,
            )
        )

        val error = runCatching {
            qualifiedClient.getLastTransactionsQualified(
                address = "cnAccount",
                count = 10,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_RESPONSE_BLOCK_AFTER_CHECKPOINT")
        assertThat(qualifiedRestClient.requests).hasSize(2)
    }

    @Test
    fun `row after attributed checkpoint is rejected before offline cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            historyResponse(
                address = "cnAccount",
                blockHeight = HEALTH_BLOCK + 1,
            )
        )

        val error = runCatching {
            qualifiedClient.getLastTransactionsQualified(
                address = "cnAccount",
                count = 10,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_RESPONSE_BLOCK_AFTER_CHECKPOINT")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `history without canonical block coordinates is rejected before cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            historyResponse(
                address = "cnAccount",
                blockHeight = HEALTH_BLOCK,
                blockHash = null,
            )
        )

        val error = runCatching {
            qualifiedClient.getLastTransactionsQualified(
                address = "cnAccount",
                count = 10,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_CHAIN_COORDINATES_INVALID")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `qualified account history rejects a row for another account before cache write`() =
        runTest {
            val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
            val qualifiedRestClient = CapturingRestClient()
            val qualifiedClient = PolkaswapIndexerClient(
                restClient = qualifiedRestClient,
                json = testJson,
                responseCache = cache,
                nowEpochMillis = { HEALTH_NOW * 1_000 },
                nowMonotonicNanos = { 1_000 },
            )
            qualifiedRestClient.enqueue(healthResponse())
            qualifiedRestClient.enqueue(
                historyResponse(
                    address = "cnDifferentAccount",
                    blockHeight = HEALTH_BLOCK,
                )
            )

            val error = runCatching {
                qualifiedClient.getLastTransactionsQualified(
                    address = "cnAccount",
                    count = 10,
                )
            }.exceptionOrNull()

            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_ACCOUNT_MISMATCH")
            assertThat(cache.entries).isEmpty()
        }

    @Test
    fun `qualified transaction lookup rejects a different returned hash`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            qualifiedRestClient,
            testJson,
        )
        qualifiedRestClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        qualifiedRestClient.enqueue(
            historyResponse(
                address = "cnAccount",
                blockHeight = HEALTH_BLOCK,
                transactionId = "ab".repeat(32),
            )
        )

        val error = runCatching {
            qualifiedClient.getTransactionQualified("ef".repeat(32))
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_TRANSACTION_MISMATCH")
    }

    @Test
    fun `filtered asset identity is rejected before offline cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            { "data": { "assets": {
              "edges": [
                { "node": { "id": "different-asset", "priceUSD": "1" } }
              ],
              "pageInfo": { "hasNextPage": false },
              "totalCount": 1
            } } }
            """
        )

        val error = runCatching {
            qualifiedClient.getAssetsInfo(listOf("expected-asset"))
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_ASSET_IDENTITY_MISMATCH")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `qualified market lookup rejects another market before cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "market": {
                  "id": "different-market",
                  "marketId": 1,
                  "status": "Open",
                  "updatedAtBlock": $HEALTH_BLOCK
                }
              }
            }
            """
        )

        val error = runCatching {
            qualifiedClient.getMarketQualified("expected-market")
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_IDENTITY_MISMATCH")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `qualified market catalog rejects a mismatched requested status`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            qualifiedRestClient,
            testJson,
        )
        qualifiedRestClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "market-1",
                      "node": {
                        "id": "market-1",
                        "marketId": 1,
                        "status": "Closed",
                        "updatedAtBlock": $HEALTH_BLOCK
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val error = runCatching {
            qualifiedClient.getMarketsQualified(
                status = "Open",
                pageSize = 1,
                maxPages = 1,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_STATUS_MISMATCH")
    }

    @Test
    fun `invalid market quantities are rejected before offline cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "market": {
                  "id": "market-1",
                  "marketId": 1,
                  "status": "Open",
                  "liquidityUSD": "-1",
                  "updatedAtBlock": $HEALTH_BLOCK
                }
              }
            }
            """
        )

        val error = runCatching {
            qualifiedClient.getMarketQualified("market-1")
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_LIQUIDITY")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `invalid dpm enrichment is rejected before offline cache write`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "market": {
                  "id": "market-1",
                  "marketId": 1,
                  "status": "Open",
                  "dpmCollateral": "-1",
                  "updatedAtBlock": $HEALTH_BLOCK
                }
              }
            }
            """
        )

        val error = runCatching {
            qualifiedClient.getMarketQualified("market-1")
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_DPM_STATE")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `polkamarkt rows require checkpoint semantics and consistent aliases before cache write`() =
        runTest {
            val snapshotCache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
            val snapshotRestClient = CapturingRestClient()
            val snapshotClient = PolkaswapIndexerClient(
                restClient = snapshotRestClient,
                json = testJson,
                responseCache = snapshotCache,
                nowEpochMillis = { HEALTH_NOW * 1_000 },
                nowMonotonicNanos = { 1_000 },
            )
            snapshotRestClient.enqueue(healthResponse())
            snapshotRestClient.enqueue(
                """
                {
                  "data": {
                    "marketSnapshots": {
                      "totalCount": 1,
                      "pageInfo": { "hasNextPage": false, "endCursor": null },
                      "edges": [
                        {
                          "cursor": "snapshot-1",
                          "node": {
                            "id": "snapshot-1",
                            "marketId": 7,
                            "timestamp": 100,
                            "type": "DEFAULT"
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )

            val snapshotError = runCatching {
                snapshotClient.getMarketSnapshotsQualified(
                    marketId = 7,
                    pageSize = 1,
                    maxPages = 1,
                )
            }.exceptionOrNull()
            assertThat(snapshotError).hasMessageThat()
                .isEqualTo("PI_INDEXER_INVALID_MARKET_SNAPSHOT_SEMANTICS")
            assertThat(snapshotCache.entries).isEmpty()
            assertThat(snapshotRestClient.requests.last().variables.toString())
                .contains("\"type\":{\"equalTo\":\"DEFAULT\"}")

            val tradeCache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
            val tradeRestClient = CapturingRestClient()
            val tradeClient = PolkaswapIndexerClient(
                restClient = tradeRestClient,
                json = testJson,
                responseCache = tradeCache,
                nowEpochMillis = { HEALTH_NOW * 1_000 },
                nowMonotonicNanos = { 1_000 },
            )
            tradeRestClient.enqueue(healthResponse())
            tradeRestClient.enqueue(
                """
                {
                  "data": {
                    "accountTrades": {
                      "totalCount": 1,
                      "pageInfo": { "hasNextPage": false, "endCursor": null },
                      "edges": [
                        {
                          "cursor": "trade-1",
                          "node": {
                            "id": "trade-1",
                            "account": "cnAccount",
                            "marketId": 7,
                            "shares": "1",
                            "sharesAmount": "2",
                            "blockNumber": $HEALTH_BLOCK,
                            "blockHash": "0x${"cd".repeat(32)}",
                            "extrinsicHash": "0x${"ef".repeat(32)}",
                            "market": {
                              "id": "market-7",
                              "marketId": 7,
                              "status": "Open",
                              "updatedAtBlock": $HEALTH_BLOCK
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )

            val tradeError = runCatching {
                tradeClient.getAccountTradesQualified(
                    account = "cnAccount",
                    pageSize = 1,
                    maxPages = 1,
                )
            }.exceptionOrNull()
            assertThat(tradeError).hasMessageThat()
                .isEqualTo("PI_INDEXER_INVALID_SHARES")
            assertThat(tradeCache.entries).isEmpty()
        }

    @Test
    fun `polkamarkt trade block hashes are canonical ASCII and nonzero before cache write`() =
        runTest {
            listOf(
                "cd".repeat(32),
                "0x${"00".repeat(32)}",
                "0x${"\u0661".repeat(64)}",
            ).forEach { blockHash ->
                val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
                val rest = CapturingRestClient()
                val qualifiedClient = PolkaswapIndexerClient(
                    restClient = rest,
                    json = testJson,
                    responseCache = cache,
                    nowEpochMillis = { HEALTH_NOW * 1_000 },
                    nowMonotonicNanos = { 1_000 },
                )
                rest.enqueue(healthResponse())
                rest.enqueue(
                    """
                    {
                      "data": {
                        "accountTrades": {
                          "totalCount": 1,
                          "pageInfo": { "hasNextPage": false, "endCursor": null },
                          "edges": [
                            {
                              "cursor": "trade-1",
                              "node": {
                                "id": "trade-1",
                                "account": "cnAccount",
                                "marketId": 7,
                                "blockNumber": $HEALTH_BLOCK,
                                "blockHash": "$blockHash",
                                "extrinsicHash": "0x${"ef".repeat(32)}",
                                "market": {
                                  "id": "market-7",
                                  "marketId": 7,
                                  "status": "Open",
                                  "updatedAtBlock": $HEALTH_BLOCK
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                    """
                )

                val error = runCatching {
                    qualifiedClient.getAccountTradesQualified(
                        account = "cnAccount",
                        pageSize = 1,
                        maxPages = 1,
                    )
                }.exceptionOrNull()

                assertThat(error).hasMessageThat()
                    .isEqualTo("PI_INDEXER_INVALID_BLOCK_HASH")
                assertThat(cache.entries).isEmpty()
            }
        }

    @Test
    fun `chart values derive only from canonical exact PI decimals`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "market": {
                  "id": "market-1",
                  "marketId": 1,
                  "status": "Open",
                  "probability": 1e-1,
                  "updatedAtBlock": $HEALTH_BLOCK
                }
              }
            }
            """
        )

        val error = runCatching {
            qualifiedClient.getMarketQualified("market-1")
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_CHART_PROBABILITY")
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `chart percentage and unit prices preserve canonical web semantics`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            responseCache = DisabledPiIndexerResponseCache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        qualifiedRestClient.enqueue(healthResponse())
        qualifiedRestClient.enqueue(
            """
            {
              "data": {
                "market": {
                  "id": "market-1",
                  "marketId": 1,
                  "status": "Open",
                  "mechanism": "LMSR",
                  "metadataUri": "ipfs://market-metadata",
                  "resolutionSource": "sora:governance:democracy:referendum:124",
                  "cancellationEvidenceUri": "https://evidence.example/cancel",
                  "probability": "50.66",
                  "priceYes": "0.5066",
                  "priceNo": "0.4934",
                  "virtualDepth": "999999999999999999999999.0001",
                  "dpmCollateral": "200.5",
                  "realYesShares": "101.25",
                  "realNoShares": "99.25",
                  "impliedYesProbabilityBps": 0,
                  "impliedNoProbabilityBps": 0,
                  "updatedAtBlock": $HEALTH_BLOCK
                }
              }
            }
            """
        )

        val market = qualifiedClient.getMarketQualified("market-1").value

        assertThat(market?.chartProbability).isWithin(0.000_000_1).of(0.5066)
        assertThat(market?.chartPriceYes).isWithin(0.000_000_1).of(0.5066)
        assertThat(market?.chartPriceNo).isWithin(0.000_000_1).of(0.4934)
        assertThat(market?.displayYesProbabilityBps).isEqualTo(5_066)
        assertThat(market?.displayNoProbabilityBps).isEqualTo(4_934)
        assertThat(market?.metadataUri).isEqualTo("ipfs://market-metadata")
        assertThat(market?.resolutionSource)
            .isEqualTo("sora:governance:democracy:referendum:124")
        assertThat(market?.cancellationEvidenceUri)
            .isEqualTo("https://evidence.example/cancel")
        assertThat(market?.virtualDepth)
            .isEqualTo("999999999999999999999999.0001")
        assertThat(market?.dpmCollateral).isEqualTo("200.5")
        assertThat(market?.realYesShares).isEqualTo("101.25")
        assertThat(market?.realNoShares).isEqualTo("99.25")
    }

    @Test
    fun `data query requires a fresh identity health preflight`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            qualifiedRestClient,
            testJson,
        )
        qualifiedRestClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        qualifiedRestClient.enqueue(emptyHistoryResponse(hasNextPage = false))

        assertThat(
            qualifiedClient.getLastTransactions(
                address = "cnAccount",
                count = 10,
            )
        ).isEmpty()
        assertThat(qualifiedRestClient.requests).hasSize(2)
        assertThat(qualifiedRestClient.requests[0].query).contains("_health")
        assertThat(qualifiedRestClient.requests[1].query).contains("historyElements")
    }

    @Test
    fun `production read is bracketed by one stable indexer checkpoint`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            enforceResponseCheckpointPostflight = true,
        )
        val now = System.currentTimeMillis() / 1_000
        qualifiedRestClient.enqueue(healthResponse(indexedAt = now))
        qualifiedRestClient.enqueue(emptyHistoryResponse(hasNextPage = false))
        qualifiedRestClient.enqueue(healthResponse(indexedAt = now))

        val read = qualifiedClient.getLastTransactionsQualified(
            address = "cnAccount",
            count = 10,
        )

        assertThat(read.fromCache).isFalse()
        assertThat(qualifiedRestClient.requests).hasSize(3)
        assertThat(qualifiedRestClient.requests[0].query).contains("_health")
        assertThat(qualifiedRestClient.requests[1].query).contains("historyElements")
        assertThat(qualifiedRestClient.requests[2].query).contains("_health")
    }

    @Test
    fun `production read rejects checkpoint drift across its response`() = runTest {
        val qualifiedRestClient = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = qualifiedRestClient,
            json = testJson,
            enforceResponseCheckpointPostflight = true,
        )
        val now = System.currentTimeMillis() / 1_000
        qualifiedRestClient.enqueue(healthResponse(indexedAt = now))
        qualifiedRestClient.enqueue(emptyHistoryResponse(hasNextPage = false))
        qualifiedRestClient.enqueue(
            healthResponse(
                indexedAt = now,
                workerIndexedBlock = HEALTH_BLOCK + 1,
            )
        )

        val error = runCatching {
            qualifiedClient.getLastTransactionsQualified(
                address = "cnAccount",
                count = 10,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_RESPONSE_CHECKPOINT_CHANGED")
    }

    @Test
    fun `mobile config requires secure bounded capabilities`() = runTest {
        restClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        restClient.enqueue(
            """
            {
              "data": {
                "mobileConfig": {
                  "blockExplorerUrl": "https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}",
                  "substrateTypesUrl": "https://example.org/types.json",
                  "soracard": false,
                  "nodes": [{ "name": "Sora", "address": "wss://mof2.sora.org" }],
                  "nexusAvailable": true,
                  "nexusSendsAvailable": false,
                  "polkamarktVisible": true,
                  "polkamarktMutationsAvailable": false,
                  "tairaDefaultVisible": true
                }
              }
            }
            """
        )

        val config = client.requireMobileConfig()

        assertThat(config.nodes.single().address).isEqualTo("wss://mof2.sora.org")
        assertThat(config.tairaDefaultVisible).isTrue()
        assertThat(config.nexusSendsAvailable).isFalse()
        assertThat(config.typedAccountBalancesAvailable).isFalse()
    }

    @Test
    fun `typed account balances stay fail closed for PI schema v1`() = runTest {
        restClient.enqueue(
            healthResponse(indexedAt = System.currentTimeMillis() / 1_000)
        )
        restClient.enqueue(
            """
            {
              "data": {
                "mobileConfig": {
                  "blockExplorerUrl": "https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}",
                  "substrateTypesUrl": null,
                  "soracard": false,
                  "nodes": [{ "name": "Sora", "address": "wss://mof2.sora.org" }],
                  "nexusAvailable": true,
                  "nexusSendsAvailable": false,
                  "polkamarktVisible": true,
                  "polkamarktMutationsAvailable": false,
                  "tairaDefaultVisible": true
                }
              }
            }
            """
        )

        val error = runCatching {
            client.requireTypedAccountBalancesCapability()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_TYPED_ACCOUNT_BALANCES_UNAVAILABLE")
        assertThat(restClient.requests).hasSize(2)
        assertThat(restClient.requests.none { it.query.contains("account(") }).isTrue()
    }

    @Test
    fun `polkamarkt cursor pagination preserves arbitrary precision quantities`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 2,
                  "pageInfo": { "hasNextPage": true, "endCursor": "cursor-1" },
                  "edges": [
                    {
                      "cursor": "market-1",
                      "node": {
                        "id": "market-1",
                        "marketId": 1,
                        "status": "Open",
                        "updatedAtBlock": 1,
                        "collateral": "999999999999999999999999999999999999999999.000000000000000001",
                        "creatorFees": "123456789012345678901234567890"
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 2,
                  "pageInfo": { "hasNextPage": false, "endCursor": "cursor-2" },
                  "edges": [
                    {
                      "cursor": "market-2",
                      "node": {
                        "id": "market-2",
                        "marketId": 2,
                        "status": "Open",
                        "updatedAtBlock": 1
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val result = client.getMarkets(pageSize = 1, maxPages = 2)

        assertThat(result.map { it.id }).containsExactly("market-1", "market-2").inOrder()
        assertThat(result.first().collateral)
            .isEqualTo("999999999999999999999999999999999999999999.000000000000000001")
        assertThat(result.first().creatorFees).isEqualTo("123456789012345678901234567890")
        assertThat(restClient.requests[0].variables.toString()).contains("\"after\":null")
        assertThat(restClient.requests[1].variables.toString()).contains("\"after\":\"cursor-1\"")
    }

    @Test
    fun `polkamarkt signal quantity strings preserve exact decimals`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "polkamarktSignals": {
                  "totalVolumeUsd": "123456789012345678901234567890.123456789012345678",
                  "activeMarkets": 12,
                  "activeAccounts": 34,
                  "liquidityUsd": "999999999999999999999999.000000000000000001",
                  "liquiditySeries": [
                    {
                      "label": "now",
                      "value": "777777777777777777777.000000000000000007"
                    }
                  ],
                  "answerBreakdown": [
                    {
                      "answer": "yes",
                      "volumeUsd": "555555555555555555555.000000000000000005",
                      "markets": 9
                    }
                  ],
                  "accuracySummary": {
                    "accuracyPercent": 99.125
                  }
                }
              }
            }
            """
        )

        val result = client.getPolkamarktSignals()

        assertThat(result.totalVolumeUsd)
            .isEqualTo("123456789012345678901234567890.123456789012345678")
        assertThat(result.liquidityUsd)
            .isEqualTo("999999999999999999999999.000000000000000001")
        assertThat(result.liquiditySeries.single().value)
            .isEqualTo("777777777777777777777.000000000000000007")
        assertThat(result.answerBreakdown.single().volumeUsd)
            .isEqualTo("555555555555555555555.000000000000000005")
        assertThat(result.accuracyPercent)
            .isEqualTo("99.125")
    }

    @Test
    fun `polkamarkt signal quantities reject numeric json tokens`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "polkamarktSignals": {
                  "totalVolumeUsd": 1.25,
                  "activeMarkets": 1,
                  "activeAccounts": 1,
                  "liquidityUsd": "1",
                  "liquiditySeries": [],
                  "answerBreakdown": []
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getPolkamarktSignals()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_TOTAL_VOLUME")
    }

    @Test
    fun `polkamarkt signals reject negative counts and duplicate labels`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "polkamarktSignals": {
                  "totalVolumeUsd": "1",
                  "activeMarkets": -1,
                  "activeAccounts": 1,
                  "liquidityUsd": "1",
                  "liquiditySeries": [
                    { "label": "same", "value": "1" },
                    { "label": "same", "value": "2" }
                  ],
                  "answerBreakdown": []
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getPolkamarktSignals()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_SIGNALS")
    }

    @Test
    fun `polkamarkt signals reject negative volume quantities`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "polkamarktSignals": {
                  "totalVolumeUsd": "-1",
                  "activeMarkets": 1,
                  "activeAccounts": 1,
                  "liquidityUsd": "1",
                  "liquiditySeries": [],
                  "answerBreakdown": []
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getPolkamarktSignals()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_TOTAL_VOLUME")
    }

    @Test
    fun `polkamarkt signals reject out of range accuracy`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "polkamarktSignals": {
                  "totalVolumeUsd": "1",
                  "activeMarkets": 1,
                  "activeAccounts": 1,
                  "liquidityUsd": "1",
                  "liquiditySeries": [],
                  "answerBreakdown": [],
                  "accuracySummary": { "accuracyPercent": 100.1 }
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getPolkamarktSignals()
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_ACCURACY")
    }

    @Test
    fun `polkamarkt quantities reject non canonical exponent notation`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "market-1",
                      "node": {
                        "id": "market-1",
                        "marketId": 1,
                        "status": "Open",
                        "updatedAtBlock": 1,
                        "collateral": "1e18"
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 1)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("PI_INDEXER_INVALID_COLLATERAL")
    }

    @Test
    fun `polkamarkt cursor pagination rejects repeated pages`() = runTest {
        repeat(2) {
            restClient.enqueue(
                """
                {
                  "data": {
                    "markets": {
                      "totalCount": 3,
                      "pageInfo": { "hasNextPage": true, "endCursor": "same-cursor" },
                      "edges": [
                        {
                          "cursor": "same-market",
                          "node": {
                            "id": "same-market",
                            "marketId": 1,
                            "status": "Open",
                            "updatedAtBlock": 1
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )
        }

        val error = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 3)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("PI_INDEXER_REPEATED_PAGE")
    }

    @Test
    fun `market snapshots reject ordering reversals across cursor pages`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "marketSnapshots": {
                  "totalCount": 2,
                  "pageInfo": { "hasNextPage": true, "endCursor": "cursor-1" },
                  "edges": [
                    {
                      "cursor": "snapshot-1",
                      "node": {
                        "id": "snapshot-1",
                        "marketId": 7,
                        "timestamp": 100,
                        "blockHeight": 100,
                        "type": "DEFAULT"
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        restClient.enqueue(
            """
            {
              "data": {
                "marketSnapshots": {
                  "totalCount": 2,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "snapshot-2",
                      "node": {
                        "id": "snapshot-2",
                        "marketId": 7,
                        "timestamp": 101,
                        "blockHeight": 101,
                        "type": "DEFAULT"
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getMarketSnapshotsQualified(
                marketId = 7,
                pageSize = 1,
                maxPages = 2,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID")
    }

    @Test
    fun `polkamarkt cursor pagination rejects server overdelivery`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 2,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "market-1",
                      "node": {
                        "id": "market-1",
                        "marketId": 1,
                        "status": "Open",
                        "updatedAtBlock": 1
                      }
                    },
                    {
                      "cursor": "market-2",
                      "node": {
                        "id": "market-2",
                        "marketId": 2,
                        "status": "Open",
                        "updatedAtBlock": 1
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val error = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 1)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("PI_INDEXER_INVALID_PAGE")
    }

    @Test
    fun `polkamarkt cursor pagination rejects overlapping item identities`() = runTest {
        restClient.enqueue(
            marketsPage("same-market", hasNextPage = true, endCursor = "cursor-1")
        )
        restClient.enqueue(
            marketsPage(
                id = "same-market",
                hasNextPage = false,
                endCursor = null,
                updatedAtBlock = 2,
            )
        )

        val error = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 2)
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_DUPLICATE_ITEM")

        restClient.enqueue(
            marketsPage(
                id = "different-row-a",
                marketId = 7,
                hasNextPage = true,
                endCursor = "cursor-2",
            )
        )
        restClient.enqueue(
            marketsPage(
                id = "different-row-b",
                marketId = 7,
                hasNextPage = false,
                endCursor = null,
            )
        )

        val duplicateRuntimeMarketError = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 2)
        }.exceptionOrNull()

        assertThat(duplicateRuntimeMarketError).hasMessageThat()
            .isEqualTo("PI_INDEXER_DUPLICATE_RUNTIME_MARKET_ID")
    }

    @Test
    fun `cursor history pagination rejects overlapping transaction identities`() = runTest {
        val transactionHash = "0x${"ab".repeat(32)}"
        repeat(2) { page ->
            restClient.enqueue(
                """
                {
                  "data": {
                    "historyElements": {
                      "totalCount": 2,
                      "pageInfo": {
                        "hasNextPage": ${page == 0},
                        "endCursor": ${if (page == 0) "\"history-cursor-1\"" else "null"}
                      },
                      "edges": [
                        {
                          "node": {
                            "id": "$transactionHash",
                            "address": "cnAccount",
                            "timestamp": ${page + 1},
                            "blockHash": "0x${"cd".repeat(32)}",
                            "blockHeight": 10,
                            "networkFee": "0",
                            "execution": { "success": true }
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )
        }

        val error = runCatching {
            client.getAllTransactionHistoryQualified(
                address = "cnAccount",
                pageSize = 1,
                maxPages = 2,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_DUPLICATE_TRANSACTION")
        assertThat(restClient.requests[0].variables.toString()).contains("\"after\":null")
        assertThat(restClient.requests[1].variables.toString())
            .contains("\"after\":\"history-cursor-1\"")
    }

    @Test
    fun `cursor history pagination rejects a non advancing cursor`() = runTest {
        listOf("ab", "ef").forEach { byte ->
            restClient.enqueue(
                """
                {
                  "data": {
                    "historyElements": {
                      "totalCount": 3,
                      "pageInfo": {
                        "hasNextPage": true,
                        "endCursor": "same-history-cursor"
                      },
                      "edges": [
                        {
                          "node": {
                            "id": "0x${byte.repeat(32)}",
                            "address": "cnAccount",
                            "timestamp": 1,
                            "blockHash": "0x${"cd".repeat(32)}",
                            "blockHeight": 10,
                            "networkFee": "0",
                            "execution": { "success": true }
                          }
                        }
                      ]
                    }
                  }
                }
                """
            )
        }

        val error = runCatching {
            client.getAllTransactionHistoryQualified(
                address = "cnAccount",
                pageSize = 1,
                maxPages = 3,
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_REPEATED_PAGE")
        assertThat(restClient.requests).hasSize(2)
    }

    @Test
    fun `pending lookup queries only exact hashes for the exact account`() = runTest {
        val transactionHash = "0x${"ab".repeat(32)}"
        restClient.enqueue(
            """
            {
              "data": {
                "historyElements": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false },
                  "edges": [
                    {
                      "node": {
                        "id": "$transactionHash",
                        "address": "cnAccount",
                        "timestamp": 1,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "blockHeight": 10,
                        "networkFee": "0",
                        "execution": { "success": true }
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val result = client.getAccountTransactionsByHashesQualified(
            address = "cnAccount",
            transactionHashes = listOf(transactionHash),
        )

        assertThat(result.value.map(IndexerHistoryElement::id))
            .containsExactly(transactionHash)
        val variables = restClient.singleRequest().variables.toString()
        assertThat(variables).contains("\"and\"")
        assertThat(variables).contains("\"in\":[\"$transactionHash\"]")
        assertThat(variables).contains("\"equalTo\":\"cnAccount\"")
    }

    @Test
    fun `polkamarkt account and market reads reject mismatched returned identities`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "marketSnapshots": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "snapshot-1",
                      "node": {
                        "id": "snapshot-1",
                        "marketId": 8,
                        "timestamp": 100,
                        "blockHeight": 100,
                        "type": "DEFAULT"
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        val snapshotError = runCatching {
            client.getMarketSnapshotsQualified(marketId = 7, pageSize = 1, maxPages = 1)
        }.exceptionOrNull()
        assertThat(snapshotError).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_SNAPSHOT_IDENTITY_MISMATCH")

        restClient.enqueue(
            """
            {
              "data": {
                "accountPositions": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "position-1",
                      "node": {
                        "id": "position-1",
                        "account": "cnOther",
                        "marketId": 7,
                        "market": {
                          "id": "market-7",
                          "marketId": 7,
                          "status": "Open",
                          "updatedAtBlock": 100
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        val positionError = runCatching {
            client.getAccountPositionsQualified(
                account = "cnAccount",
                pageSize = 1,
                maxPages = 1,
            )
        }.exceptionOrNull()
        assertThat(positionError).hasMessageThat()
            .isEqualTo("PI_INDEXER_POSITION_IDENTITY_MISMATCH")

        restClient.enqueue(
            """
            {
              "data": {
                "accountTrades": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "trade-1",
                      "node": {
                        "id": "trade-1",
                        "account": "cnOther",
                        "marketId": 7,
                        "blockNumber": 100,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "extrinsicHash": "0x${"ef".repeat(32)}",
                        "market": {
                          "id": "market-7",
                          "marketId": 7,
                          "status": "Open",
                          "updatedAtBlock": 100
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        val tradeError = runCatching {
            client.getAccountTradesQualified(
                account = "cnAccount",
                pageSize = 1,
                maxPages = 1,
            )
        }.exceptionOrNull()
        assertThat(tradeError).hasMessageThat()
            .isEqualTo("PI_INDEXER_TRADE_IDENTITY_MISMATCH")
    }

    @Test
    fun `polkamarkt market ids preserve full runtime u32 range`() = runTest {
        val maximumMarketId = 4_294_967_295L
        restClient.enqueue(
            """
            {
              "data": {
                "marketSnapshots": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "snapshot-u32-max",
                      "node": {
                        "id": "snapshot-u32-max",
                        "marketId": $maximumMarketId,
                        "timestamp": 100,
                        "blockHeight": 100,
                        "type": "DEFAULT"
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val snapshots = client.getMarketSnapshotsQualified(
            marketId = maximumMarketId,
            pageSize = 1,
            maxPages = 1,
        ).value

        assertThat(snapshots.single().marketId).isEqualTo(maximumMarketId)
        assertThat(restClient.singleRequest().variables.toString())
            .contains("\"marketId\":{\"equalTo\":4294967295}")

        val requestCount = restClient.requests.size
        val overflow = runCatching {
            client.getMarketSnapshotsQualified(
                marketId = maximumMarketId + 1L,
                pageSize = 1,
                maxPages = 1,
            )
        }.exceptionOrNull()

        assertThat(overflow).hasMessageThat().isEqualTo("PI_INDEXER_INVALID_MARKET_ID")
        assertThat(restClient.requests).hasSize(requestCount)
    }

    @Test
    fun `polkamarkt batch trade history preserves every claimed market`() = runTest {
        restClient.enqueue(
            """
            {
              "data": {
                "accountTrades": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "batch-claim",
                      "node": {
                        "id": "batch-claim",
                        "account": "cnAccount",
                        "marketId": 7,
                        "marketIds": [7, 8],
                        "side": "claim",
                        "blockNumber": $HEALTH_BLOCK,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "extrinsicHash": "0x${"ef".repeat(32)}",
                        "market": {
                          "id": "market-7",
                          "marketId": 7,
                          "status": "Resolved",
                          "updatedAtBlock": $HEALTH_BLOCK
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val trade = client.getAccountTrades(
            account = "cnAccount",
            pageSize = 1,
            maxPages = 1,
        ).single()
        assertThat(trade.marketId).isEqualTo(7L)
        assertThat(trade.marketIds).containsExactly(7L, 8L).inOrder()

        restClient.enqueue(
            """
            {
              "data": {
                "accountTrades": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "legacy-cached-trade",
                      "node": {
                        "id": "legacy-cached-trade",
                        "account": "cnAccount",
                        "marketId": 7,
                        "side": "claim",
                        "blockNumber": $HEALTH_BLOCK,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "extrinsicHash": "0x${"ef".repeat(32)}",
                        "market": {
                          "id": "market-7",
                          "marketId": 7,
                          "status": "Resolved",
                          "updatedAtBlock": $HEALTH_BLOCK
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )
        val legacyCachedTrade = client.getAccountTrades(
            account = "cnAccount",
            pageSize = 1,
            maxPages = 1,
        ).single()
        assertThat(legacyCachedTrade.marketIds).isEmpty()

        val invalidRest = CapturingRestClient()
        val invalidClient = PolkaswapIndexerClient(
            restClient = invalidRest,
            json = testJson,
            responseCache = InMemoryResponseCache { HEALTH_NOW * 1_000 },
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        invalidRest.enqueue(healthResponse())
        invalidRest.enqueue(
            """
            {
              "data": {
                "accountTrades": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "invalid-batch-claim",
                      "node": {
                        "id": "invalid-batch-claim",
                        "account": "cnAccount",
                        "marketId": 7,
                        "marketIds": [8, 7],
                        "side": "claim",
                        "blockNumber": $HEALTH_BLOCK,
                        "blockHash": "0x${"cd".repeat(32)}",
                        "extrinsicHash": "0x${"ef".repeat(32)}",
                        "market": {
                          "id": "market-7",
                          "marketId": 7,
                          "status": "Resolved",
                          "updatedAtBlock": $HEALTH_BLOCK
                        }
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val invalid = runCatching {
            invalidClient.getAccountTrades(
                account = "cnAccount",
                pageSize = 1,
                maxPages = 1,
            )
        }.exceptionOrNull()
        assertThat(invalid).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_TRADE_SEMANTICS")
    }

    @Test
    fun `polkamarkt close blocks preserve only the runtime u32 range`() = runTest {
        val maximumRuntimeBlock = 4_294_967_295L
        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "market-u32-close",
                      "node": {
                        "id": "market-u32-close",
                        "marketId": 1,
                        "closeBlock": $maximumRuntimeBlock,
                        "status": "Open",
                        "updatedAtBlock": 1
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val market = client.getMarkets(pageSize = 1, maxPages = 1).single()
        assertThat(market.closeBlock).isEqualTo(maximumRuntimeBlock)

        restClient.enqueue(
            """
            {
              "data": {
                "markets": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "cursor": "market-overflow-close",
                      "node": {
                        "id": "market-overflow-close",
                        "marketId": 2,
                        "closeBlock": ${maximumRuntimeBlock + 1L},
                        "status": "Open",
                        "updatedAtBlock": 1
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        val overflow = runCatching {
            client.getMarkets(pageSize = 1, maxPages = 1)
        }.exceptionOrNull()
        assertThat(overflow).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_MARKET_SEMANTICS")
    }

    @Test
    fun `serializable market rejects cached close block overflow`() {
        val maximumRuntimeBlock = 4_294_967_295L
        val maximum = polkamarktMarket(closeBlock = maximumRuntimeBlock)
        assertThat(maximum.closeBlock).isEqualTo(maximumRuntimeBlock)

        val directOverflow = runCatching {
            polkamarktMarket(closeBlock = maximumRuntimeBlock + 1L)
        }.exceptionOrNull()
        assertThat(directOverflow).hasMessageThat()
            .isEqualTo("PI_INDEXER_INVALID_CLOSE_BLOCK")

        val cachedPayload = testJson.encodeToString(
            PolkamarktMarket.serializer(),
            maximum,
        )
        val cachedMaximum = testJson.decodeFromString(
            PolkamarktMarket.serializer(),
            cachedPayload,
        )
        assertThat(cachedMaximum.closeBlock).isEqualTo(maximumRuntimeBlock)
        val overflowPayload = cachedPayload.replace(
            "\"closeBlock\":$maximumRuntimeBlock",
            "\"closeBlock\":${maximumRuntimeBlock + 1L}",
        )
        assertThat(overflowPayload).isNotEqualTo(cachedPayload)
        val cachedOverflow = runCatching {
            testJson.decodeFromString(
                PolkamarktMarket.serializer(),
                overflowPayload,
            )
        }.exceptionOrNull()
        assertThat(cachedOverflow).isNotNull()
    }

    @Test
    fun `polkamarkt cursor pagination rejects cached and live page mixing`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val onlineRest = CapturingRestClient()
        val online = PolkaswapIndexerClient(
            restClient = onlineRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        onlineRest.enqueue(healthResponse())
        onlineRest.enqueue(marketsPage("market-1", hasNextPage = true, endCursor = "cursor-1"))
        onlineRest.enqueue(
            marketsPage(
                id = "market-2",
                marketId = 2,
                hasNextPage = false,
                endCursor = null,
            )
        )
        online.getMarketsQualified(pageSize = 1, maxPages = 2)

        val mixedRest = CapturingRestClient()
        val mixed = PolkaswapIndexerClient(
            restClient = mixedRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 2_000 },
        )
        mixedRest.enqueue(healthResponse())
        mixedRest.enqueueFailure(UnknownHostException("first page offline"))
        mixedRest.enqueue(
            marketsPage(
                id = "market-2",
                marketId = 2,
                hasNextPage = false,
                endCursor = null,
            )
        )

        val error = runCatching {
            mixed.getMarketsQualified(pageSize = 1, maxPages = 2)
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_PAGE_PROVENANCE_CHANGED")
    }

    @Test
    fun `polkamarkt cursor pagination rejects cached pages from different checkpoints`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val onlineRest = CapturingRestClient()
        val online = PolkaswapIndexerClient(
            restClient = onlineRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        onlineRest.enqueue(healthResponse())
        onlineRest.enqueue(marketsPage("market-1", hasNextPage = true, endCursor = "cursor-1"))
        onlineRest.enqueue(
            marketsPage(
                id = "market-2",
                marketId = 2,
                hasNextPage = false,
                endCursor = null,
            )
        )
        online.getMarketsQualified(pageSize = 1, maxPages = 2)

        val secondPage = cache.entries.entries.single {
            it.value.responseJson.contains("\"id\":\"market-2\"")
        }
        cache.entries[secondPage.key] = secondPage.value.copy(
            qualification = secondPage.value.qualification.copy(
                health = secondPage.value.qualification.health.copy(
                    workerLatestIndexedBlock = (HEALTH_BLOCK + 1).toString(),
                    workerLatestFinalizedBlock = (HEALTH_BLOCK + 5).toString(),
                )
            )
        )

        val offlineRest = CapturingRestClient()
        val offline = PolkaswapIndexerClient(
            restClient = offlineRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 2_000 },
        )
        offlineRest.enqueueFailure(UnknownHostException("offline preflight 1"))
        offlineRest.enqueueFailure(UnknownHostException("offline preflight 2"))

        val error = runCatching {
            offline.getMarketsQualified(pageSize = 1, maxPages = 2)
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_PAGE_CHECKPOINT_CHANGED")
    }

    @Test
    fun `snapshot order failures remain rejected when cursor pages replay from cache`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val onlineRest = CapturingRestClient()
        val online = PolkaswapIndexerClient(
            restClient = onlineRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        onlineRest.enqueue(healthResponse())
        onlineRest.enqueue(
            marketSnapshotsPage(
                id = "snapshot-1",
                timestamp = 100,
                blockHeight = 100,
                hasNextPage = true,
                endCursor = "cursor-1",
            )
        )
        onlineRest.enqueue(
            marketSnapshotsPage(
                id = "snapshot-2",
                timestamp = 101,
                blockHeight = 101,
                hasNextPage = false,
                endCursor = null,
            )
        )

        val liveError = runCatching {
            online.getMarketSnapshotsQualified(
                marketId = 7,
                pageSize = 1,
                maxPages = 2,
            )
        }.exceptionOrNull()

        assertThat(liveError).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID")
        assertThat(cache.entries).hasSize(2)

        val offlineRest = CapturingRestClient()
        val offline = PolkaswapIndexerClient(
            restClient = offlineRest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 2_000 },
        )
        offlineRest.enqueueFailure(UnknownHostException("offline preflight 1"))
        offlineRest.enqueueFailure(UnknownHostException("offline preflight 2"))

        val cachedError = runCatching {
            offline.getMarketSnapshotsQualified(
                marketId = 7,
                pageSize = 1,
                maxPages = 2,
            )
        }.exceptionOrNull()

        assertThat(cachedError).hasMessageThat()
            .isEqualTo("PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID")
    }

    @Test
    fun `qualified live read is available from bounded cache after offline preflight`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val onlineRestClient = CapturingRestClient()
        val onlineClient = PolkaswapIndexerClient(
            restClient = onlineRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        onlineRestClient.enqueue(healthResponse(indexedAt = HEALTH_NOW))
        onlineRestClient.enqueue(emptyHistoryResponse(hasNextPage = false))
        onlineRestClient.enqueue(emptyHistoryResponse(hasNextPage = false))

        val live = onlineClient.getLastTransactionsQualified("cnAccount", 10)
        onlineClient.getLastTransactionsQualified("anotherAccount", 10)

        assertThat(live.fromCache).isFalse()
        assertThat(cache.entries).hasSize(2)
        assertThat(
            cache.entries.keys.all { key ->
                key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }
            }
        ).isTrue()

        val offlineRestClient = CapturingRestClient()
        val offlineClient = PolkaswapIndexerClient(
            restClient = offlineRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 2_000 },
        )
        offlineRestClient.enqueueFailure(UnknownHostException("offline"))

        val cached = offlineClient.getLastTransactionsQualified("cnAccount", 10)

        assertThat(cached.fromCache).isTrue()
        assertThat(cached.value).isEmpty()
        assertThat(cached.health.chainId).isEqualTo("sora:mainnet")
        assertThat(offlineRestClient.requests).hasSize(1)
        assertThat(offlineRestClient.requests.single().query).contains("_health")
    }

    @Test
    fun `offline cache keeps live string only quantity admission`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val onlineRestClient = CapturingRestClient()
        val onlineClient = PolkaswapIndexerClient(
            restClient = onlineRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        onlineRestClient.enqueue(healthResponse(indexedAt = HEALTH_NOW))
        onlineRestClient.enqueue(
            """
            {
              "data": {
                "assets": {
                  "totalCount": 1,
                  "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "edges": [
                    {
                      "node": {
                        "id": "xor",
                        "priceUSD": "1",
                        "liquidity": "2",
                        "priceChangeDay": null
                      }
                    }
                  ]
                }
              }
            }
            """
        )

        assertThat(onlineClient.getAssetsInfo(listOf("xor"))).hasSize(1)
        val (cacheKey, qualifiedEntry) = cache.entries.entries.single()
        val numericQuantity = qualifiedEntry.responseJson.replace(
            Regex("\\\"priceUSD\\\"\\s*:\\s*\\\"1\\\""),
            "\"priceUSD\": 1",
        )
        assertThat(numericQuantity).isNotEqualTo(qualifiedEntry.responseJson)
        cache.entries[cacheKey] = qualifiedEntry.copy(responseJson = numericQuantity)

        val offlineRestClient = CapturingRestClient()
        val offlineClient = PolkaswapIndexerClient(
            restClient = offlineRestClient,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 2_000 },
        )
        offlineRestClient.enqueueFailure(UnknownHostException("offline"))

        val error = runCatching {
            offlineClient.getAssetsInfo(listOf("xor"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(UnknownHostException::class.java)
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `graphql and decoding failures never fall back to a cached response`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val rest = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = rest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        rest.enqueue(healthResponse(indexedAt = HEALTH_NOW))
        rest.enqueue(emptyHistoryResponse(hasNextPage = false))
        qualifiedClient.getLastTransactions("cnAccount", 10)
        assertThat(cache.entries).hasSize(1)

        rest.enqueue("""{ "errors": [{ "message": "schema mismatch" }] }""")
        val graphQlError = runCatching {
            qualifiedClient.getLastTransactions("cnAccount", 10)
        }.exceptionOrNull()

        assertThat(graphQlError).hasMessageThat().isEqualTo("PI_INDEXER_GRAPHQL_ERROR")

        rest.enqueue("""{ "data": { "historyElements": """)
        val decodingError = runCatching {
            qualifiedClient.getLastTransactions("cnAccount", 10)
        }.exceptionOrNull()

        assertThat(decodingError).isNotNull()
        assertThat(decodingError).isNotInstanceOf(UnknownHostException::class.java)
    }

    @Test
    fun `mobile config is live only and cancellation is never hidden`() = runTest {
        val cache = InMemoryResponseCache { HEALTH_NOW * 1_000 }
        val rest = CapturingRestClient()
        val qualifiedClient = PolkaswapIndexerClient(
            restClient = rest,
            json = testJson,
            responseCache = cache,
            nowEpochMillis = { HEALTH_NOW * 1_000 },
            nowMonotonicNanos = { 1_000 },
        )
        rest.enqueue(healthResponse(indexedAt = HEALTH_NOW))
        rest.enqueue(mobileConfigResponse())

        qualifiedClient.requireMobileConfig()

        assertThat(cache.entries).isEmpty()

        rest.enqueueFailure(UnknownHostException("offline config"))
        val offlineConfig = runCatching {
            qualifiedClient.getMobileConfig()
        }.exceptionOrNull()

        assertThat(offlineConfig).isInstanceOf(UnknownHostException::class.java)

        rest.enqueueFailure(CancellationException("cancelled"))
        val cancellation = runCatching {
            qualifiedClient.getLastTransactions("cnAccount", 10)
        }.exceptionOrNull()

        assertThat(cancellation).isInstanceOf(CancellationException::class.java)
        assertThat(cache.entries).isEmpty()
    }

    private fun emptyHistoryResponse(hasNextPage: Boolean) =
        """
        {
          "data": {
            "historyElements": {
              "totalCount": 0,
              "pageInfo": { "hasNextPage": $hasNextPage },
              "edges": []
            }
          }
        }
        """

    private fun historyResponse(
        address: String,
        blockHeight: Long,
        transactionId: String = "ab".repeat(32),
        blockHash: String? = "0x${"cd".repeat(32)}",
    ) =
        """
        {
          "data": {
            "historyElements": {
              "totalCount": 1,
              "pageInfo": { "hasNextPage": false },
              "edges": [
                {
                  "node": {
                    "id": "$transactionId",
                    "timestamp": 1,
                    "blockHash": ${blockHash?.let { "\"$it\"" } ?: "null"},
                    "blockHeight": $blockHeight,
                    "address": "$address",
                    "networkFee": "0",
                    "execution": { "success": true }
                  }
                }
              ]
            }
          }
        }
        """

    private fun marketsPage(
        id: String,
        marketId: Long = 1,
        hasNextPage: Boolean,
        endCursor: String?,
        updatedAtBlock: Long = 1,
    ) =
        """
        {
          "data": {
            "markets": {
              "totalCount": 2,
              "pageInfo": {
                "hasNextPage": $hasNextPage,
                "endCursor": ${endCursor?.let { "\"$it\"" } ?: "null"}
              },
              "edges": [
                {
                  "cursor": "$id",
                  "node": {
                    "id": "$id",
                    "marketId": $marketId,
                    "status": "Open",
                    "updatedAtBlock": $updatedAtBlock
                  }
                }
              ]
            }
          }
        }
        """

    private fun marketSnapshotsPage(
        id: String,
        timestamp: Long,
        blockHeight: Long,
        hasNextPage: Boolean,
        endCursor: String?,
    ) =
        """
        {
          "data": {
            "marketSnapshots": {
              "totalCount": 2,
              "pageInfo": {
                "hasNextPage": $hasNextPage,
                "endCursor": ${endCursor?.let { "\"$it\"" } ?: "null"}
              },
              "edges": [
                {
                  "cursor": "$id",
                  "node": {
                    "id": "$id",
                    "marketId": 7,
                    "timestamp": $timestamp,
                    "blockHeight": $blockHeight,
                    "type": "DEFAULT"
                  }
                }
              ]
            }
          }
        }
        """

    private fun mobileConfigResponse() =
        """
        {
          "data": {
            "mobileConfig": {
              "blockExplorerUrl": "https://sorametrics.org/sorav2?tab=extrinsics&q={transaction}",
              "substrateTypesUrl": "https://example.org/types.json",
              "soracard": false,
              "nodes": [{ "name": "Sora", "address": "wss://mof2.sora.org" }],
              "nexusAvailable": true,
              "nexusSendsAvailable": false,
              "polkamarktVisible": true,
              "polkamarktMutationsAvailable": false,
              "tairaDefaultVisible": true
            }
          }
        }
        """

    private fun healthResponse(
        chainId: String = "sora:mainnet",
        indexedAt: Long = HEALTH_NOW,
        workerIndexedBlock: Long = HEALTH_BLOCK,
        workerLag: Long = HEALTH_BLOCK + 4 - workerIndexedBlock,
    ) =
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
              "chainId": "$chainId",
              "network": "mainnet",
              "publicBaseUrl": "https://pi.soramitsu.io/graphql",
              "readOnly": true,
              "workerAvailable": true,
              "workerReady": true,
              "workerReadinessReason": null,
              "workerLifecycle": "running",
              "workerStartupComplete": true,
              "workerLatestFinalizedBlock": ${HEALTH_BLOCK + 4},
              "workerLatestIndexedBlock": $workerIndexedBlock,
              "workerLag": $workerLag,
              "workerLastSuccessfulIndexTimestamp": $indexedAt,
              "workerLastError": null,
              "workerLastErrorTimestamp": null
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
        private val responses = ArrayDeque<Any>()

        fun enqueue(response: String) {
            responses.addLast(response.trimIndent())
        }

        fun enqueueFailure(error: Throwable) {
            responses.addLast(error)
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

            if (response is Throwable) throw response
            return testJson.decodeFromString(
                request.responseDeserializer,
                response as String,
            )
        }

        override suspend fun postAsString(request: AbstractRestServerRequest.WithBody<String>): String =
            error("postAsString is not used by PolkaswapIndexerClient")

        override suspend fun <T> get(request: AbstractRestServerRequest<T>): T =
            error("get is not used by PolkaswapIndexerClient")

        override suspend fun getAsString(request: AbstractRestServerRequest<String>): String =
            error("getAsString is not used by PolkaswapIndexerClient")
    }

    private fun polkamarktMarket(closeBlock: Long) = PolkamarktMarket(
        id = "market-cache",
        marketId = 1L,
        title = null,
        category = null,
        tags = null,
        description = null,
        rulesUri = null,
        resolutionSource = null,
        closeBlock = closeBlock,
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
        updatedAtBlock = 1L,
        timestamp = null,
    )

    private class InMemoryResponseCache(
        private val nowEpochMillis: () -> Long,
    ) : PiIndexerResponseCache {
        val entries = mutableMapOf<String, PiIndexerCacheEntry>()

        override suspend fun load(
            key: String,
            maximumAgeMillis: Long,
        ): PiIndexerCacheEntry? = entries[key]?.takeIf {
            val age = nowEpochMillis() - it.savedAtEpochMillis
            age in 0L..maximumAgeMillis
        }

        override suspend fun store(
            key: String,
            entry: PiIndexerCacheEntry,
        ) {
            entries[key] = entry
        }

        override suspend fun remove(key: String) {
            entries.remove(key)
        }
    }

    private companion object {
        const val HEALTH_NOW = 1_800_000_000L
        const val HEALTH_BLOCK = 26_000_001L

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
