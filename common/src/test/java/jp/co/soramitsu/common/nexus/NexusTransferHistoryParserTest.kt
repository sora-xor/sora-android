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
    fun `submit and wait accepts only matching final Applied result`() {
        val receipt = TairaMcpContract.validateSubmitAndWaitResult(
            json = json,
            result = submitAndWaitResult(),
            expectedHash = HASH,
        )

        assertEquals(HASH, receipt.payload.transactionHash)
        assertEquals(HASH, receipt.payload.entrypointHash)
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
                "entrypoint_hash": "$receiptHash",
                "signed_transaction_hash": "$receiptHash",
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
            headers["x-iroha-entrypoint-hash"] = JsonPrimitive(it)
            headers["x-iroha-signed-transaction-hash"] = JsonPrimitive(it)
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

    private fun parseHistory(report: JsonObject, account: String = sender) = NexusTransferHistoryParser.page(
        report, NexusNetworks.taira, account, TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
    )

    private fun mutateItem(change: (JsonObject) -> JsonObject): JsonObject {
        val report = routedHistory("1")
        val body = report.getValue("body") as JsonObject
        val item = (body.getValue("items") as JsonArray).single() as JsonObject
        return JsonObject(report + ("body" to JsonObject(body + ("items" to JsonArray(listOf(change(item)))))))
    }

    private fun mutatePayload(change: (JsonObject) -> JsonObject): JsonObject = mutateItem { item ->
        val box = item.getValue("box") as JsonObject
        val projection = box.getValue("json") as JsonObject
        val payload = projection.getValue("payload") as JsonObject
        JsonObject(item + ("box" to JsonObject(box + ("json" to JsonObject(projection + ("payload" to change(payload)))))))
    }

    private fun mutateTransfer(change: (JsonObject) -> JsonObject): JsonObject = mutatePayload { payload ->
        JsonObject(payload + ("value" to change(payload.getValue("value") as JsonObject)))
    }

    private fun assertRejected(code: String, report: JsonObject) {
        assertEquals(code, assertThrows(NexusToriiException::class.java) { parseHistory(report) }.safeCode)
    }

    @Test fun `history page rejects missing containers and nonobject instructions`() {
        val valid = routedHistory("1")
        val body = valid.getValue("body") as JsonObject
        listOf("body", "items", "pagination").forEach { field ->
            val report = if (field == "body") JsonObject(valid - field)
                else JsonObject(valid + ("body" to JsonObject(body - field)))
            assertRejected("NEXUS_HISTORY_INVALID_RESPONSE", report)
        }
        assertRejected("NEXUS_HISTORY_INVALID_ITEM", JsonObject(valid + ("body" to JsonObject(body + ("items" to JsonArray(listOf(JsonPrimitive("bad"))))))))
    }

    @Test fun `each pagination field must be an integer before a page can be admitted`() {
        val valid = routedHistory("1")
        val body = valid.getValue("body") as JsonObject
        val pagination = body.getValue("pagination") as JsonObject
        for (field in listOf("page", "per_page", "total_pages", "total_items")) {
            assertRejected("NEXUS_HISTORY_PAGINATION_INVALID", JsonObject(valid + ("body" to JsonObject(body + ("pagination" to JsonObject(pagination - field))))))
        }
    }

    @Test fun `instruction identity requires explicit status hash index and timestamp`() {
        mapOf(
            "transaction_status" to "NEXUS_HISTORY_STATUS_MISSING",
            "transaction_hash" to "NEXUS_HISTORY_INVALID_HASH",
            "index" to "NEXUS_HISTORY_INVALID_INDEX",
            "created_at" to "NEXUS_HISTORY_INVALID_TIMESTAMP",
            "box" to "NEXUS_HISTORY_INVALID_PAYLOAD",
        ).forEach { (field, code) -> assertRejected(code, mutateItem { JsonObject(it - field) }) }
        assertRejected("NEXUS_HISTORY_IDENTITY_MISMATCH", mutateItem { JsonObject(it + ("kind" to JsonPrimitive("Mint"))) })
        for (timestamp in listOf("bad", "0", "9".repeat(65), "9223372036854775808")) {
            assertRejected("NEXUS_HISTORY_INVALID_TIMESTAMP", mutateItem { JsonObject(it + ("created_at" to JsonPrimitive(timestamp))) })
        }
    }

    @Test fun `second and millisecond timestamps resolve to the same instant`() {
        val seconds = parseHistory(mutateItem { JsonObject(it + ("created_at" to JsonPrimitive("1775260800"))) })
        val millis = parseHistory(mutateItem { JsonObject(it + ("created_at" to JsonPrimitive("1775260800000"))) })
        assertEquals(1775260800000L, seconds.items.single().timestampMillis)
        assertEquals(seconds.items, millis.items)
    }

    @Test fun `history transfer requires destination amount and consistent explicit source identity`() {
        assertRejected("NEXUS_HISTORY_DESTINATION_MISSING", mutateTransfer { JsonObject(it - "destination") })
        assertRejected("NEXUS_HISTORY_INVALID_AMOUNT", mutateTransfer { JsonObject(it - "object") })
        assertRejected("NEXUS_HISTORY_SOURCE_MISMATCH", mutateTransfer { JsonObject(it + ("source_account" to JsonPrimitive(receiver))) })
        assertRejected("NEXUS_HISTORY_INVALID_SOURCE_ASSET", mutateTransfer { JsonObject(it + ("source" to JsonPrimitive(TairaTestnetContract.XOR_ASSET_DEFINITION_ID + "#"))) })
        assertRejected("NEXUS_HISTORY_INVALID_DESTINATION", mutateTransfer { JsonObject(it + ("destination" to JsonPrimitive("invalid"))) })
        assertRejected("NEXUS_HISTORY_IDENTITY_MISMATCH", mutateTransfer { JsonObject(it + ("source" to JsonPrimitive("unrelated-asset"))) })
        val incoming = mutateTransfer { JsonObject((it - "source") + ("source_account" to JsonPrimitive(sender))) }
        assertEquals(sender, parseHistory(incoming, receiver).items.single().sender)
        assertRejected("NEXUS_HISTORY_INVALID_SOURCE", mutateTransfer { JsonObject((it - "source") + ("destination" to JsonPrimitive(sender))) })
    }

    @Test fun `scaled mantissa and nested quantity forms retain exact decimal amounts`() {
        for (quantity in listOf(
            JsonObject(mapOf("scale" to JsonPrimitive("9"), "mantissa" to JsonPrimitive("1234567890"))),
            JsonObject(mapOf("amount" to JsonPrimitive("1.234567890"))),
            JsonObject(mapOf("value" to JsonObject(mapOf("value" to JsonPrimitive("1.234567890"))))),
        )) {
            assertEquals("1.23456789", parseHistory(mutateTransfer { JsonObject(it + ("object" to quantity)) }).items.single().amount)
        }
        for (quantity in listOf(
            JsonArray(emptyList()),
            JsonObject(mapOf("scale" to JsonPrimitive("-1"), "value" to JsonPrimitive("1"))),
            JsonObject(mapOf("scale" to JsonPrimitive("9"), "value" to JsonPrimitive("-1"))),
            JsonObject(mapOf("scale" to JsonPrimitive("9"), "value" to JsonPrimitive("9".repeat(5000)))),
        )) assertRejected("NEXUS_HISTORY_INVALID_AMOUNT", mutateTransfer { JsonObject(it + ("object" to quantity)) })
    }

    @Test fun `batch transfer accepts supported entry containers and filters different asset definitions`() {
        val definition = TairaTestnetContract.XOR_ASSET_DEFINITION_ID
        val entry = JsonObject(mapOf("asset_definition" to JsonPrimitive(definition), "source_account" to JsonPrimitive(sender), "destination" to JsonPrimitive(receiver), "amount" to JsonPrimitive("2.5")))
        for (key in listOf("entries", "transfers", "items")) {
            val batch = mutatePayload { JsonObject(mapOf("variant" to JsonPrimitive("AssetBatch"), "value" to JsonObject(mapOf(key to JsonArray(listOf(JsonObject(entry + ("asset_definition" to JsonPrimitive("other"))), entry)))))) }
            assertEquals(listOf("2.5"), parseHistory(batch).items.map { it.amount })
        }
        val missingDefinition = JsonArray(listOf(JsonObject(entry - "asset_definition")))
        val invalidBatch = mutatePayload {
            JsonObject(mapOf("variant" to JsonPrimitive("AssetBatch"), "value" to JsonObject(mapOf("entries" to missingDefinition))))
        }
        assertRejected("NEXUS_HISTORY_INVALID_BATCH_ASSET", invalidBatch)
    }

    @Test fun `malformed batch payloads and unknown variants are rejected`() {
        for (value in listOf(JsonPrimitive("bad"), JsonObject(emptyMap()), JsonObject(mapOf("entries" to JsonArray(listOf(JsonPrimitive("bad"))))))) {
            assertRejected("NEXUS_HISTORY_INVALID_PAYLOAD", mutatePayload { JsonObject(mapOf("variant" to JsonPrimitive("AssetBatch"), "value" to value)) })
        }
        assertRejected("NEXUS_HISTORY_IDENTITY_MISMATCH", mutatePayload { JsonObject(it + ("variant" to JsonPrimitive("Unexpected"))) })
    }

}
