package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
