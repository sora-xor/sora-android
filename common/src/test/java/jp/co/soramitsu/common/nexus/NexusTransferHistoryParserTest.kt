package jp.co.soramitsu.common.nexus

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusTransferHistoryParserTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val sender = IrohaAddressCodec.encode(ByteArray(32) { 1 }, 369)
    private val receiver = IrohaAddressCodec.encode(ByteArray(32) { 2 }, 369)

    @Test
    fun `submit and wait request uses the exact public Taira contract`() {
        val request = TairaMcpContract.submitAndWaitRequest(
            requestId = "submit-1",
            signedNorito = byteArrayOf(1, 2, 3, 4),
            expectedHash = HASH,
        )
        val params = request.params as JsonObject
        assertEquals("tools/call", request.method)
        assertEquals(TairaMcpContract.SUBMIT_AND_WAIT_TOOL, params.string("name"))
        val arguments = params["arguments"] as JsonObject
        assertEquals(
            setOf(
                "body_base64",
                "hash",
                "status_accept",
                "terminal_statuses",
                "timeout_ms",
            ),
            arguments.keys,
        )
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)), arguments.string("body_base64"))
        assertEquals(HASH, arguments.string("hash"))
        assertEquals("application/json", arguments.string("status_accept"))
        assertEquals(JsonArray(listOf(JsonPrimitive("Applied"))), arguments["terminal_statuses"])
        assertEquals(JsonPrimitive(120_000), arguments["timeout_ms"])
    }

    @Test
    fun `account assets request uses the current routed query selector`() {
        val request = TairaMcpContract.accountAssetsRequest(
            requestId = "assets-1",
            accountId = sender,
            definitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
            limit = 100,
            offset = 0,
        )
        val params = request.params as JsonObject
        assertEquals(TairaMcpContract.ACCOUNT_ASSETS_TOOL, params.string("name"))
        val arguments = params["arguments"] as JsonObject
        assertEquals(
            TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
            arguments.string("asset"),
        )
        assertTrue("asset_id" !in arguments)
        assertTrue("count_mode" !in arguments)
        assertEquals("global", arguments.string("scope"))
    }

    @Test
    fun `instruction history request uses the bidirectional definition selector`() {
        val request = TairaMcpContract.instructionsRequest(
            requestId = "history-1",
            accountId = sender,
            definitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
            page = 1,
            perPage = 100,
        )
        val arguments = (request.params as JsonObject)["arguments"] as JsonObject
        assertEquals(
            TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
            arguments.string("asset_definition_id"),
        )
        assertTrue("asset_id" !in arguments)
        assertEquals("Transfer", arguments.string("kind"))
        assertEquals("committed", arguments.string("transaction_status"))
    }

    @Test
    fun `instruction discovery requires the explicit definition selector`() {
        fun listing(selector: String): JsonObject = json.parseToJsonElement(
            """
            {
              "tools": [{
                "name": "iroha.instructions.list",
                "inputSchema": {
                  "type": "object",
                  "additionalProperties": true,
                  "properties": {
                    "account": {"type": "string"},
                    "$selector": {"type": "string"},
                    "kind": {"type": "string"},
                    "page": {"type": "integer"},
                    "per_page": {"type": "integer"},
                    "transaction_hash": {"type": "string"},
                    "transaction_status": {"type": "string"},
                    "accept": {"type": "string"}
                  }
                }
              }],
              "nextCursor": null,
              "listChanged": false,
              "toolsetVersion": "${"c".repeat(64)}"
            }
            """.trimIndent()
        ) as JsonObject

        assertEquals(
            "TAIRA_MCP_CONTRACT_MISMATCH",
            assertThrows(NexusToriiException::class.java) {
                TairaMcpContract.validateToolPage(
                    result = listing("asset_id"),
                    tools = setOf(TairaMcpContract.INSTRUCTIONS_TOOL),
                    expectedToolsetVersion = "c".repeat(64),
                )
            }.safeCode,
        )
        assertEquals(
            setOf(TairaMcpContract.INSTRUCTIONS_TOOL),
            TairaMcpContract.validateToolPage(
                result = listing("asset_definition_id"),
                tools = setOf(TairaMcpContract.INSTRUCTIONS_TOOL),
                expectedToolsetVersion = "c".repeat(64),
            ).matched,
        )
    }

    @Test
    fun `account assets discovery requires extension query names to be admitted`() {
        fun listing(additionalProperties: Boolean): JsonObject = json.parseToJsonElement(
            """
            {
              "tools": [{
                "name": "iroha.accounts.assets",
                "inputSchema": {
                  "type": "object",
                  "additionalProperties": $additionalProperties,
                  "properties": {
                    "account_id": {"type": "string"},
                    "asset_id": {"type": "string"},
                    "limit": {"type": "integer"},
                    "offset": {"type": "integer"},
                    "accept": {"type": "string"}
                  }
                }
              }],
              "nextCursor": null,
              "listChanged": false,
              "toolsetVersion": "${"b".repeat(64)}"
            }
            """.trimIndent()
        ) as JsonObject

        assertEquals(
            setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL),
            TairaMcpContract.validateToolPage(
                result = listing(additionalProperties = true),
                tools = setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL),
                expectedToolsetVersion = "b".repeat(64),
            ).matched,
        )
        assertEquals(
            "TAIRA_MCP_CONTRACT_MISMATCH",
            assertThrows(NexusToriiException::class.java) {
                TairaMcpContract.validateToolPage(
                    result = listing(additionalProperties = false),
                    tools = setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL),
                    expectedToolsetVersion = "b".repeat(64),
                )
            }.safeCode,
        )

        val corrected = json.parseToJsonElement(
            """
            {
              "tools": [{
                "name": "iroha.accounts.assets",
                "inputSchema": {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "account_id": {"type": "string"},
                    "asset": {"type": "string"},
                    "limit": {"type": "integer"},
                    "offset": {"type": "integer"},
                    "scope": {"type": "string"},
                    "accept": {"type": "string"}
                  }
                }
              }],
              "nextCursor": null,
              "listChanged": false,
              "toolsetVersion": "${"b".repeat(64)}"
            }
            """.trimIndent()
        )
        assertEquals(
            setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL),
            TairaMcpContract.validateToolPage(
                result = corrected,
                tools = setOf(TairaMcpContract.ACCOUNT_ASSETS_TOOL),
                expectedToolsetVersion = "b".repeat(64),
            ).matched,
        )
    }

    @Test
    fun `discovery validates relevant current schemas without exact full schema equality`() {
        val initialize = json.parseToJsonElement(
            """
            {
              "protocolVersion": "2025-06-18",
              "capabilities": {"tools": {"toolsetVersion": "${"a".repeat(64)}", "extra": true}},
              "serverInfo": {"name": "iroha-torii-mcp", "version": "next"}
            }
            """.trimIndent()
        )
        assertEquals("a".repeat(64), TairaMcpContract.toolsetVersion(initialize))

        val listing = json.parseToJsonElement(
            """
            {
              "tools": [
                {
                  "name": "iroha.transactions.submit_and_wait",
                  "description": "current",
                  "inputSchema": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["body_base64"],
                    "properties": {
                      "body_base64": {"type": "string"},
                      "hash": {"type": "string"},
                      "status_accept": {"type": "string"},
                      "terminal_statuses": {
                        "type": "array",
                        "items": {"type": "string"}
                      },
                      "timeout_ms": {"type": "integer"},
                      "future_optional_field": {"type": "string"}
                    }
                  },
                  "outputSchema": {"type": "object"}
                }
              ],
              "nextCursor": null,
              "listChanged": false,
              "toolsetVersion": "${"a".repeat(64)}"
            }
            """.trimIndent()
        )
        val page = TairaMcpContract.validateToolPage(
            result = listing,
            tools = setOf(TairaMcpContract.SUBMIT_AND_WAIT_TOOL),
            expectedToolsetVersion = "a".repeat(64),
        )
        assertEquals(setOf(TairaMcpContract.SUBMIT_AND_WAIT_TOOL), page.matched)
        assertEquals(null, page.nextCursor)

        val request = TairaMcpContract.discoveryRequest(
            requestId = "tools-2",
            toolsetVersion = "a".repeat(64),
            cursor = "64",
        )
        val params = request.params as JsonObject
        assertEquals("a".repeat(64), params.string("toolset_version"))
        assertEquals("64", params.string("cursor"))
    }

    @Test
    fun `submit and wait accepts only matching final Applied result`() {
        val receipt = TairaMcpContract.validateSubmitAndWaitResult(
            json = json,
            result = submitAndWaitResult(),
            expectedHash = HASH,
        )

        assertEquals(HASH, receipt.payload.transactionHash)
        assertEquals(null, receipt.payload.entrypointHash)
    }

    @Test
    fun `submit and wait rejects every hash mismatch and non Applied terminal`() {
        listOf(
            submitAndWaitResult(receiptHash = "b".repeat(64)),
            submitAndWaitResult(headerHash = "b".repeat(64)),
        ).forEach { mismatched ->
            assertEquals(
                "NEXUS_TRANSACTION_HASH_MISMATCH",
                assertThrows(NexusToriiException::class.java) {
                    TairaMcpContract.validateSubmitAndWaitResult(json, mismatched, HASH)
                }.safeCode,
            )
        }

        assertEquals(
            "TAIRA_SUBMIT_AND_WAIT_FAILED",
            assertThrows(NexusToriiException::class.java) {
                TairaMcpContract.validateSubmitAndWaitResult(
                    json,
                    submitAndWaitResult(terminalKind = "Rejected"),
                    HASH,
                )
            }.safeCode,
        )
    }

    @Test
    fun `current instruction projection parses bidirectional Taira transfer history`() {
        val page = NexusTransferHistoryParser.page(
            result = routedHistory("1.000000001"),
            network = NexusNetworks.taira,
            account = sender,
            assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
        )
        page.requirePageContract(expectedPage = 1, maximum = 100)

        assertEquals(1, page.sourceItemCount)
        assertEquals(1, page.totalItems)
        assertEquals(
            NexusTransferHistoryItem(
                transactionHash = HASH,
                timestampMillis = 1_775_260_800_000L,
                amount = "1.000000001",
                sender = sender,
                receiver = receiver,
            ),
            page.items.single(),
        )
        assertEquals(
            "NEXUS_HISTORY_PAGE_INVALID",
            assertThrows(NexusToriiException::class.java) {
                page.copy(perPage = 99).requirePageContract(expectedPage = 1, maximum = 100)
            }.safeCode,
        )
        assertEquals(
            "NEXUS_HISTORY_PAGE_INVALID",
            assertThrows(NexusToriiException::class.java) {
                page.copy(totalItems = 101).requirePageContract(expectedPage = 1, maximum = 100)
            }.safeCode,
        )
        assertEquals(
            "NEXUS_HISTORY_PAGE_INVALID",
            assertThrows(NexusToriiException::class.java) {
                page.copy(
                    page = Long.MAX_VALUE,
                    perPage = Long.MAX_VALUE,
                    totalPages = 1,
                    totalItems = 1,
                ).requirePageContract(expectedPage = 1, maximum = 100)
            }.safeCode,
        )
    }

    @Test
    fun `Taira history rejects more than nine fractional digits`() {
        assertEquals(
            "TAIRA_XOR_SCALE_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryParser.page(
                    result = routedHistory("0.0000000001"),
                    network = NexusNetworks.taira,
                    account = sender,
                    assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
                )
            }.safeCode,
        )
        assertTrue(NexusQuantityContract.isTairaXorQuantity("1.123456789"))
    }

    @Test
    fun `Taira history rejects duplicate instruction identity and non committed rows`() {
        val routed = routedHistory("1")
        val body = routed.getValue("body") as JsonObject
        val item = (body.getValue("items") as JsonArray).single() as JsonObject
        val duplicateBody = JsonObject(
            body + mapOf(
                "items" to JsonArray(listOf(item, item)),
                "pagination" to JsonObject(
                    mapOf(
                        "page" to JsonPrimitive(1),
                        "per_page" to JsonPrimitive(100),
                        "total_pages" to JsonPrimitive(1),
                        "total_items" to JsonPrimitive(2),
                    )
                ),
            )
        )
        assertEquals(
            "NEXUS_HISTORY_DUPLICATE_ITEM",
            assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryParser.page(
                    result = JsonObject(routed + ("body" to duplicateBody)),
                    network = NexusNetworks.taira,
                    account = sender,
                    assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
                )
            }.safeCode,
        )

        val rejectedItem = JsonObject(item + ("transaction_status" to JsonPrimitive("Rejected")))
        val rejectedBody = JsonObject(
            body + ("items" to JsonArray(listOf(rejectedItem)))
        )
        assertEquals(
            "NEXUS_HISTORY_IDENTITY_MISMATCH",
            assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryParser.page(
                    result = JsonObject(routed + ("body" to rejectedBody)),
                    network = NexusNetworks.taira,
                    account = sender,
                    assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
                )
            }.safeCode,
        )

        val appliedItem = JsonObject(item + ("transaction_status" to JsonPrimitive("Applied")))
        val appliedBody = JsonObject(
            body + ("items" to JsonArray(listOf(appliedItem)))
        )
        assertEquals(
            "NEXUS_HISTORY_IDENTITY_MISMATCH",
            assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryParser.page(
                    result = JsonObject(routed + ("body" to appliedBody)),
                    network = NexusNetworks.taira,
                    account = sender,
                    assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
                )
            }.safeCode,
        )
    }

    @Test
    fun `Taira history requires canonical committed explorer evidence`() {
        val routed = routedHistory("1")
        val body = routed.getValue("body") as JsonObject
        val item = (body.getValue("items") as JsonArray).single() as JsonObject

        fun failureCode(mutated: JsonObject): String {
            val mutatedBody = JsonObject(
                body + ("items" to JsonArray(listOf(mutated)))
            )
            return assertThrows(NexusToriiException::class.java) {
                NexusTransferHistoryParser.page(
                    result = JsonObject(routed + ("body" to mutatedBody)),
                    network = NexusNetworks.taira,
                    account = sender,
                    assetDefinitionId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
                )
            }.safeCode
        }

        assertEquals(
            "NEXUS_HISTORY_INVALID_HASH",
            failureCode(JsonObject(item + ("transaction_hash" to JsonPrimitive(HASH.uppercase())))),
        )
        assertEquals(
            "NEXUS_HISTORY_INVALID_BLOCK",
            failureCode(JsonObject(item + ("block" to JsonPrimitive(0)))),
        )
        assertEquals(
            "NEXUS_HISTORY_INVALID_AUTHORITY",
            failureCode(JsonObject(item + ("authority" to JsonPrimitive("invalid")))),
        )

        val box = item.getValue("box") as JsonObject
        val projection = box.getValue("json") as JsonObject
        val wrongProjection = JsonObject(projection + ("kind" to JsonPrimitive("Mint")))
        val wrongBox = JsonObject(box + ("json" to wrongProjection))
        assertEquals(
            "NEXUS_HISTORY_IDENTITY_MISMATCH",
            failureCode(JsonObject(item + ("box" to wrongBox))),
        )
    }

    private fun submitAndWaitResult(
        receiptHash: String = HASH,
        headerHash: String = HASH,
        terminalKind: String = "Applied",
    ): JsonObject {
        val submitBody = json.parseToJsonElement(
            """
            {
              "payload": {
                "tx_hash": "$receiptHash",
                "submitted_at_ms": 1775251200000,
                "submitted_at_height": 42,
                "signer": "ed0120example"
              },
              "signature": "ed0120example"
            }
            """.trimIndent()
        )
        val finalBody = json.parseToJsonElement(
            """
            {
              "hash": "$HASH",
              "status": {"kind": "$terminalKind", "block_height": 43},
              "scope": "global",
              "resolved_from": "state"
            }
            """.trimIndent()
        )
        return JsonObject(
            mapOf(
                "isError" to JsonPrimitive(false),
                "structuredContent" to JsonObject(
                    mapOf(
                        "status" to JsonPrimitive(200),
                        "hash" to JsonPrimitive(HASH),
                        "terminal_kind" to JsonPrimitive(terminalKind),
                        "terminal_statuses" to JsonArray(listOf(JsonPrimitive("Applied"))),
                        "attempts" to JsonPrimitive(2),
                        "elapsed_ms" to JsonPrimitive(500),
                        "submit" to routedStructured(submitBody, headerHash),
                        "final" to routedStructured(finalBody),
                    )
                ),
            )
        )
    }

    private fun routedStructured(
        body: kotlinx.serialization.json.JsonElement,
        transactionHash: String? = null,
    ): JsonObject {
        val headers = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "content-type" to JsonPrimitive("application/json")
        )
        transactionHash?.let {
            headers["x-iroha-transaction-hash"] = JsonPrimitive(it)
        }
        return JsonObject(
            mapOf(
                "status" to JsonPrimitive(200),
                "headers" to JsonObject(headers),
                "content_type" to JsonPrimitive("application/json"),
                "body" to body,
            )
        )
    }

    private fun routedHistory(amount: String): JsonObject {
        val body = json.parseToJsonElement(
            """
            {
              "pagination": {"page": 1, "per_page": 100, "total_pages": 1, "total_items": 1},
              "items": [
                {
                  "authority": "$sender",
                  "created_at": "2026-04-04T00:00:00Z",
                  "kind": "Transfer",
                  "box": {
                    "encoded": "0x01",
                    "json": {
                      "kind": "Transfer",
                      "payload": {
                        "variant": "Asset",
                        "value": {
                          "source": "${TairaTestnetContract.XOR_ASSET_DEFINITION_ID}#$sender",
                          "destination": "$receiver",
                          "object": "$amount"
                        }
                      },
                      "wire_id": "iroha_data_model::isi::TransferBox",
                      "encoded": "01"
                    }
                  },
                  "transaction_hash": "$HASH",
                  "transaction_status": "Committed",
                  "block": 42,
                  "index": 0
                }
              ]
            }
            """.trimIndent()
        )
        return JsonObject(
            mapOf(
                "status" to JsonPrimitive(200),
                "headers" to JsonObject(emptyMap()),
                "content_type" to JsonPrimitive("application/json"),
                "body" to body,
            )
        )
    }

    private fun JsonObject.string(key: String): String = (this[key] as JsonPrimitive).content

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
