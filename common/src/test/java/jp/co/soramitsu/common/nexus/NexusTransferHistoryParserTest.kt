package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NexusTransferHistoryParserTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `parses exact arbitrary precision outgoing XOR transfer`() {
        val page = NexusTransferHistoryParser.page(
            json = json,
            result = json.parseToJsonElement(historyResult(AMOUNT)),
            network = NexusNetworks.minamoto,
            account = SENDER,
            assetDefinitionId = ASSET,
        )

        assertEquals(1, page.sourceItemCount)
        assertEquals(1, page.items.size)
        assertEquals(AMOUNT, page.items.single().amount)
        assertEquals(SENDER, page.items.single().sender)
        assertEquals(RECEIVER, page.items.single().receiver)
        assertEquals(HASH, page.items.single().transactionHash)
    }

    @Test
    fun `parses standard MCP structured content wrapper`() {
        val page = NexusTransferHistoryParser.page(
            json = json,
            result = JsonObject(
                mapOf(
                    "structuredContent" to json.parseToJsonElement(historyResult(AMOUNT))
                )
            ),
            network = NexusNetworks.minamoto,
            account = SENDER,
            assetDefinitionId = ASSET,
        )

        assertEquals(AMOUNT, page.items.single().amount)
    }

    @Test
    fun `MCP history requires a complete embedded route fanout proof`() {
        NexusMcpResultContract.validateEmbeddedRoute(
            result = mcpResult(
                contentType = JsonPrimitive("application/json; charset=UTF-8"),
                headers = completeFanout(),
            ),
            requireFanout = true,
        )
        NexusMcpResultContract.validateEmbeddedRoute(
            result = mcpResult(headers = completeFanout()),
            requireFanout = true,
        )
        assertEquals(
            "NEXUS_HISTORY_PAGE_SIZE_EXCEEDED",
            assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryPage(emptyList(), 101)
                    .requireBoundedSourceCount(100)
            }.safeCode,
        )

        listOf(
            mcpResult(),
            mcpResult(
                headers = mapOf("x-iroha-fanout-routes-attempted" to JsonPrimitive("4"))
            ),
            mcpResult(
                headers = mapOf("x-iroha-routed-by" to JsonPrimitive("Proxy"))
            ),
            mcpResult(status = JsonPrimitive(500)),
            mcpResult(isError = JsonPrimitive(true)),
            mcpResult(contentType = JsonPrimitive("text/plain")),
            mcpResult(contentType = JsonPrimitive("application/json; profile=unexpected")),
            mcpResult(contentType = JsonPrimitive("application/json;")),
            mcpResult(contentType = JsonPrimitive("application/json, text/plain")),
            mcpResult(body = JsonNull),
            mcpResult(body = JsonPrimitive("not-an-object")),
            mcpResult(includesStructuredShadowItems = true),
            mcpResult(
                body = JsonNull,
                includesStructuredShadowItems = true,
            ),
            mcpResult(
                headers = mapOf(
                    "x-iroha-routed-by" to JsonPrimitive("proxy"),
                    "X-Iroha-Routed-By" to JsonPrimitive("proxy"),
                )
            ),
            mcpResult(
                headers = mapOf("x-iroha-routed-by" to JsonPrimitive(1))
            ),
            JsonObject(
                mcpResult().toMutableMap().apply {
                    put("body", JsonObject(mapOf("items" to json.parseToJsonElement("[]"))))
                }
            ),
        ).forEach { invalid ->
            assertEquals(
                true,
                assertThrows(NexusToriiException::class.java) {
                    NexusMcpResultContract.validateEmbeddedRoute(
                        result = invalid,
                        requireFanout = true,
                    )
                }.safeCode in setOf(
                    "NEXUS_MCP_RESULT_INVALID",
                    "NEXUS_PARTIAL_FANOUT",
                    "NEXUS_FANOUT_HEADERS_INVALID",
                ),
            )
        }
    }

    @Test
    fun `parses exact decimal wire amount without floating point`() {
        val page = NexusTransferHistoryParser.page(
            json = json,
            result = json.parseToJsonElement(historyResult("1.500000000000000001")),
            network = NexusNetworks.minamoto,
            account = SENDER,
            assetDefinitionId = ASSET,
        )
        assertEquals("1.500000000000000001", page.items.single().amount)
    }

    @Test
    fun `rejects exponent wire amount instead of coercing it`() {
        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(historyResult("1e18")),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(
                    historyResult(AMOUNT).replace(HASH, "0".repeat(64))
                ),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
    }

    @Test
    fun `history shares the exact 255 digit scale boundary`() {
        val maximumScale = quantityWithScale(NexusQuantityContract.MAX_SCALE)
        val page = NexusTransferHistoryParser.page(
            json = json,
            result = json.parseToJsonElement(historyResult(maximumScale)),
            network = NexusNetworks.minamoto,
            account = SENDER,
            assetDefinitionId = ASSET,
        )

        assertEquals(maximumScale, page.items.single().amount)
        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(
                    historyResult(quantityWithScale(NexusQuantityContract.MAX_SCALE + 1))
                ),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
    }

    @Test
    fun `rejects a cross-network transfer counterparty`() {
        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(
                    historyResult(AMOUNT).replace(RECEIVER, TAIRA_RECEIVER)
                ),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
    }

    @Test
    fun `rejects conflicting embedded and explicit transfer sources`() {
        val conflicting = historyResult(AMOUNT).replace(
            "\"source\": \"$ASSET#$SENDER\",",
            "\"source\": \"$ASSET#$RECEIVER\", \"source_account\": \"$SENDER\",",
        )

        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(conflicting),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
    }

    @Test
    fun `batch history requires the exact XOR definition on every leg`() {
        val mixed = NexusTransferHistoryParser.page(
            json = json,
            result = json.parseToJsonElement(
                batchHistoryResult(
                    """{"from":"$SENDER","to":"$RECEIVER","asset_definition":"$ASSET","amount":"1"},
                    {"from":"$SENDER","to":"$RECEIVER","asset_definition":"$OTHER_ASSET","amount":"1"}"""
                )
            ),
            network = NexusNetworks.minamoto,
            account = SENDER,
            assetDefinitionId = ASSET,
        )

        assertEquals(1, mixed.sourceItemCount)
        assertEquals(1, mixed.items.size)
        assertEquals("1", mixed.items.single().amount)

        assertThrows(NexusToriiException::class.java) {
            NexusTransferHistoryParser.page(
                json = json,
                result = json.parseToJsonElement(
                    batchHistoryResult(
                        """{"from":"$SENDER","to":"$RECEIVER","amount":"1"}"""
                    )
                ),
                network = NexusNetworks.minamoto,
                account = SENDER,
                assetDefinitionId = ASSET,
            )
        }
    }

    private fun historyResult(amount: String): String =
        """
        {
          "body": {
            "items": [{
              "transaction_hash": "$HASH",
              "created_at": "2026-08-02T00:00:00Z",
              "transaction_status": "Committed",
              "box": {
                "json": {
                  "payload": {
                    "variant": "Asset",
                    "value": {
                      "source": "$ASSET#$SENDER",
                      "destination": "$RECEIVER",
                      "object": "$amount"
                    }
                  }
                }
              }
            }]
          }
        }
        """.trimIndent()

    private fun batchHistoryResult(entries: String): String =
        """
        {
          "body": {
            "items": [{
              "transaction_hash": "$HASH",
              "created_at": "2026-08-02T00:00:00Z",
              "transaction_status": "Committed",
              "box": {
                "json": {
                  "payload": {
                    "variant": "AssetBatch",
                    "value": {"entries": [$entries]}
                  }
                }
              }
            }]
          }
        }
        """.trimIndent()

    private fun mcpResult(
        headers: Map<String, JsonPrimitive> = emptyMap(),
        status: JsonPrimitive = JsonPrimitive(200),
        isError: JsonPrimitive = JsonPrimitive(false),
        contentType: JsonPrimitive = JsonPrimitive("application/json"),
        body: JsonElement = JsonObject(
            mapOf("items" to json.parseToJsonElement("[]"))
        ),
        includesStructuredShadowItems: Boolean = false,
    ): JsonObject {
        val structured = mutableMapOf<String, JsonElement>(
            "status" to status,
            "headers" to JsonObject(headers),
            "content_type" to contentType,
            "body" to body,
        )
        if (includesStructuredShadowItems) {
            structured["items"] = json.parseToJsonElement("[]")
        }
        return JsonObject(
            mapOf(
                "isError" to isError,
                "structuredContent" to JsonObject(structured),
            )
        )
    }

    private fun completeFanout(): Map<String, JsonPrimitive> = mapOf(
        "x-iroha-routed-by" to JsonPrimitive("proxy"),
        "x-iroha-fanout-routes-attempted" to JsonPrimitive("4"),
        "x-iroha-fanout-routes-succeeded" to JsonPrimitive("4"),
        "x-iroha-fanout-routes-failed" to JsonPrimitive("0"),
        "x-iroha-fanout-routes-unavailable" to JsonPrimitive("0"),
        "x-iroha-fanout-routes-denied" to JsonPrimitive("0"),
        "x-iroha-fanout-routes-not-found" to JsonPrimitive("0"),
    )

    private fun quantityWithScale(scale: Int): String =
        "0." + "0".repeat(scale - 1) + "1"

    private companion object {
        const val ASSET = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val OTHER_ASSET = "61CtjvNd9T3THAR65GsMVHr82Bjc"
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val AMOUNT = "340282366920938463463374607431768211455"
        const val SENDER =
            "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val RECEIVER =
            "sorauﾛ1NﾍﾖﾁﾘﾗoEuKﾗﾁK2ｴA9ｸxmxBﾈｴDﾋﾐﾐﾅｴjuXvｾﾍｵn5FAXTS3"
        const val TAIRA_RECEIVER =
            "testuﾛ1Q1ﾘﾚxgﾁﾃﾀdRZﾀWｿfXLGﾜﾘPﾐﾉﾉkﾃ7ﾖｶBｹssﾙﾈjｷｹUNYWHP"
    }
}
