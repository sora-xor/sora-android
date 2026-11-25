package jp.co.soramitsu.common.hashi

import java.io.IOException
import java.math.BigInteger
import java.time.Instant
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Availability of new transfers (spec §8 A) against the vectors' `recoveryCompletion`,
 * `operatorSignerReport` and `bridgeProxy` sections, and the dry-run decoding (§8 B).
 */
class HashiAvailabilityTest {

    private val sora: JsonObject =
        Json.parseToJsonElement(checkNotNull(javaClass.classLoader!!.getResource("hashi/sora.json")).readText()).jsonObject

    private fun valuesOf(case: JsonObject): Map<String, String?> =
        case.getValue("values").jsonObject.mapValues { (_, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content }

    @Test
    fun `recovery completion matches every vector case`() {
        val cases = sora.getValue("recoveryCompletion").jsonObject.getValue("cases").jsonArray
        assertEquals(9, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val values = valuesOf(case)
            val verdict = HashiRecoveryCompletion.evaluate { key ->
                check(key in values) { "vector has no value for $key" }
                values[key]?.let(EthHex::decode)
            }
            val name = case.getValue("name").jsonPrimitive.content
            when (val expect = case.getValue("expect").jsonPrimitive.content) {
                "complete" -> assertEquals(name, HashiRecoveryCompletion.Verdict.COMPLETE, verdict)
                "none (admitted)" -> assertEquals(name, HashiRecoveryCompletion.Verdict.NONE, verdict)
                "error (blocked)" -> assertEquals(name, HashiRecoveryCompletion.Verdict.INCOMPLETE, verdict)
                else -> error("unexpected $expect")
            }
            assertEquals(name, case.getValue("expect").jsonPrimitive.content != "error (blocked)", verdict.admitted)
        }
    }

    @Test
    fun `the operator signer report decides availability`() {
        val section = sora.getValue("operatorSignerReport").jsonObject
        assertEquals(HashiSignerReports.CONFIG_URL, section.getValue("configUrl").jsonPrimitive.content)
        assertEquals(HashiSignerReports.CONFIG_KEY, section.getValue("key").jsonPrimitive.content)
        assertEquals(HashiSignerReports.MAXIMUM_AGE_SECONDS, section.getValue("maxAgeSeconds").jsonPrimitive.long)
        val seen = section.getValue("lastOperatorReportSeen").jsonObject
        val reportJson = buildJsonObject { put(HashiSignerReports.CONFIG_KEY, seen.getValue("value")) }.toString()
        val report = checkNotNull(HashiSignerReports.parse(reportJson))
        assertEquals(1, report.activeSigners)
        assertEquals(3, report.registeredPeers)
        assertEquals(3, report.requiredSignatures)
        val atReport = report.reportedAt.plusSeconds(600)
        assertEquals("down", seen.getValue("verdict").jsonPrimitive.content)
        assertEquals(HashiSignerReports.Verdict.DOWN, HashiSignerReports.judge(report, atReport, peers = 3, pendingPeer = false))
        // Today: the key is missing from the config.
        assertFalse(section.getValue("presentAtGeneration").jsonPrimitive.content.toBoolean())
        assertNull(HashiSignerReports.parse("{\"currencies\":[]}"))
        assertEquals(HashiSignerReports.Verdict.MISSING, HashiSignerReports.judge(null, atReport, 3, false))

        val up = HashiSignerReport(report.reportedAt, 3, 3, 3)
        assertEquals(HashiSignerReports.Verdict.UP, HashiSignerReports.judge(up, atReport, 3, false))
        assertEquals(HashiSignerReports.Verdict.STALE, HashiSignerReports.judge(up, report.reportedAt.plusSeconds(3_601), 3, false))
        assertEquals(HashiSignerReports.Verdict.STALE, HashiSignerReports.judge(up, report.reportedAt.minusSeconds(1), 3, false))
        assertEquals(HashiSignerReports.Verdict.MISMATCH, HashiSignerReports.judge(up, atReport, 4, false))
        assertEquals(HashiSignerReports.Verdict.MISMATCH, HashiSignerReports.judge(up, atReport, 3, pendingPeer = true))
        assertNull(HashiSignerReports.parse("{\"hashi_bridge_status\":{\"reportedAt\":\"yesterday\",\"activeSigners\":3,\"registeredPeers\":3,\"requiredSignatures\":3}}"))
        assertNull(HashiSignerReports.parse("{\"hashi_bridge_status\":{\"reportedAt\":\"2026-11-20T09:45:00Z\",\"activeSigners\":\"3\",\"registeredPeers\":3,\"requiredSignatures\":3}}"))
    }

    @Test
    fun `required signatures follow the pallet's threshold`() {
        assertTrue(sora.getValue("operatorSignerReport").jsonObject.getValue("requiredSignaturesRule").jsonPrimitive.content.contains("=> 3"))
        assertEquals(3, HashiSignerReports.requiredSignatures(3, false))
        assertEquals(4, HashiSignerReports.requiredSignatures(3, true))
        assertEquals(3, HashiSignerReports.requiredSignatures(4, false))
        assertEquals(5, HashiSignerReports.requiredSignatures(7, false))
        assertEquals(1, HashiSignerReports.requiredSignatures(1, false))
    }

    @Test
    fun `lockedAssets key of XOR reads the live value`() {
        val proxy = sora.getValue("bridgeProxy").jsonObject
        assertEquals("({EVMLegacy: 0}, assetId)", proxy.getValue("lockedAssetsKeyArgs").jsonPrimitive.content)
        // Key checked against mainnet on 2026-11-24: it returned exactly the vector's XOR value.
        assertEquals(
            "0x36a5ab3d4eb3724ea97d04d19e77d1ff198e197075bb69012727ed8832aaab18148ea4e4c47f737f97b4a6d3b61726f9" +
                "02000000009d7224862f5243be3cf3be5853f2c810" + "02" + "00".repeat(31),
            HashiSoraStorage.lockedAssetsKey(EthHex.decode(HashiBridgeContract.XOR_ASSET_ID)),
        )
        assertEquals(
            BigInteger(proxy.getValue("liveAtGeneration").jsonObject.getValue("XOR").jsonPrimitive.content),
            HashiSoraStorage.decodeU128("0xbe0f9b0b2d0fa1479600000000000000"),
        )
        assertEquals(listOf("XOR", "VAL", "DAI", "ETH"), proxy.getValue("limitedAssets").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `queue, peers and pending peer keys and values decode`() {
        assertEquals(
            "0x8e8f4d4777099074419cbb4597dac1d34f1c48c3cf3da900a7dac823371f7a9fb4def25cfda6ef3a00000000",
            HashiSoraStorage.requestsQueueKey(),
        )
        assertEquals(
            "0x8e8f4d4777099074419cbb4597dac1d32db2d969c1d2247f7efebb578ef18d2bb4def25cfda6ef3a00000000",
            HashiSoraStorage.peersKey(),
        )
        assertEquals(emptyList<ByteArray>(), HashiSoraStorage.decodeFixedVector("0x00", 32))
        val peers = HashiSoraStorage.decodeFixedVector("0x0c" + "11".repeat(32) + "22".repeat(32) + "33".repeat(32), 32)
        assertEquals(3, peers.size)
        assertTrue(runCatching { HashiSoraStorage.decodeFixedVector("0x0c" + "11".repeat(32), 32) }.isFailure)
        assertEquals(28_499_182L, HashiSoraStorage.decodeU32("0xeedcb201"))
    }

    // --- The whole availability read ---------------------------------------------------------

    private val live = valuesOf(sora.getValue("recoveryCompletion").jsonObject.getValue("cases").jsonArray[0].jsonObject)
    private val finalized = 28_499_182L
    private val finalizedHash = "0x" + "ab".repeat(32)

    private fun chainValues(overrides: Map<String, String?> = emptyMap()): Map<String, String?> =
        live.mapKeys { (key, _) -> HashiRecoveryKeys.hex(key) } +
            mapOf(
                HashiSoraStorage.bridgeStatusKey() to "0x00",
                HashiSoraStorage.bridgeContractAddressKey() to "0x313416870a4da6f12505a550b67bb73c8e21d5d3",
                HashiSoraStorage.valMasterContractAddressKey to "0xd1eeb2f30016fffd746233ee12c486e7ca8efef1",
                HashiSoraStorage.requestsQueueKey() to "0x00",
                HashiSoraStorage.peersKey() to "0x0c" + "11".repeat(32) + "22".repeat(32) + "33".repeat(32),
            ) + overrides

    private fun client(values: Map<String, String?>, spec: Int = 142) = HashiSoraRpcClient(
        { _, body ->
            val request = Json.parseToJsonElement(body).jsonObject
            val id = request.getValue("id").jsonPrimitive.long
            val params = request.getValue("params").jsonArray
            val result = when (request.getValue("method").jsonPrimitive.content) {
                "state_getRuntimeVersion" -> buildJsonObject { put("specVersion", spec) }
                "chain_getFinalizedHead" -> JsonPrimitive(finalizedHash)
                "chain_getHeader" -> buildJsonObject { put("number", "0x" + finalized.toString(16)) }
                "state_queryStorageAt" -> {
                    assertEquals(finalizedHash, params[1].jsonPrimitive.content)
                    val keys = params[0].jsonArray.map { it.jsonPrimitive.content }
                    buildJsonArray {
                        add(buildJsonObject {
                            put("block", finalizedHash)
                            put("changes", JsonArray(keys.filter { values[it] != null }.map {
                                JsonArray(listOf(JsonPrimitive(it), JsonPrimitive(values.getValue(it))))
                            }))
                        })
                    }
                }
                else -> throw IOException("unexpected")
            }
            buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }.toString()
        },
        "https://sora.example",
    )

    private val now: Instant = Instant.parse("2026-11-24T13:00:00Z")

    private fun reportJson(active: Int, reportedAt: Instant = now.minusSeconds(60)) =
        "{\"hashi_bridge_status\":{\"reportedAt\":\"$reportedAt\",\"activeSigners\":$active,\"registeredPeers\":3,\"requiredSignatures\":3}}"

    @Test
    fun `today's mainnet is paused only because the signer report is missing`() = runBlocking {
        val availability = client(chainValues()).availability({ "{\"currencies\":[]}" }, now)
        assertEquals(setOf(HashiBlock.SIGNERS_UNVERIFIED), availability.blocks)
        assertFalse(availability.newTransfersAllowed)
        assertEquals(142, availability.specVersion)
        assertEquals(HashiSignerReports.Verdict.MISSING, availability.signerVerdict)
    }

    @Test
    fun `everything verified opens new transfers, and the operators' down report closes them`() = runBlocking {
        assertTrue(client(chainValues()).availability({ reportJson(3) }, now).newTransfersAllowed)
        assertEquals(setOf(HashiBlock.SIGNERS_DOWN), client(chainValues()).availability({ reportJson(1) }, now).blocks)
        assertEquals(
            setOf(HashiBlock.SIGNERS_UNVERIFIED),
            client(chainValues()).availability({ reportJson(3, now.minusSeconds(7_200)) }, now).blocks,
        )
    }

    @Test
    fun `each chain check blocks on its own`() = runBlocking {
        val up = HashiSignerReportSource { reportJson(3) }
        assertEquals(
            setOf(HashiBlock.GUARDIAN_HALT),
            client(chainValues(mapOf(HashiRecoveryKeys.hex(HashiRecoveryKeys.GUARDIAN_HALT) to "0x0100000002000000")))
                .availability(up, now).blocks,
        )
        assertEquals(
            setOf(HashiBlock.NOT_INITIALIZED),
            client(chainValues(mapOf(HashiSoraStorage.bridgeStatusKey() to "0x01"))).availability(up, now).blocks,
        )
        assertEquals(
            setOf(HashiBlock.CONTRACT_MISMATCH),
            client(chainValues(mapOf(HashiSoraStorage.valMasterContractAddressKey to "0x" + "00".repeat(20))))
                .availability(up, now).blocks,
        )
        val unfinished = HashiRecoveryKeys.hex(HashiRecoveryKeys.STATUS) to
            "0x010000001f4e11427a9c0a61a4868af40c9e0bdf263bb44a9ee59886a537c52164f3cdbb03d7030000000000000000000000"
        assertEquals(setOf(HashiBlock.RECOVERY_NOT_COMPLETE), client(chainValues(mapOf(unfinished))).availability(up, now).blocks)
        // Before runtime 142 the legacy latch (set today) is the admission rule.
        assertEquals(setOf(HashiBlock.RECOVERY_NOT_COMPLETE), client(chainValues(), spec = 141).availability(up, now).blocks)
    }

    @Test
    fun `a queued request older than 300 blocks means the signers are not processing`() = runBlocking {
        val up = HashiSignerReportSource { reportJson(3) }
        val stuck = ByteArray(32) { 5 }
        val fresh = ByteArray(32) { 6 }
        fun height(value: Long) = EthHex.encode(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array())
        val queue = HashiSoraStorage.requestsQueueKey() to "0x08" + EthHex.encode(fresh, false) + EthHex.encode(stuck, false)
        val ok = client(
            chainValues(mapOf(queue, HashiSoraStorage.requestSubmissionHeightKey(fresh) to height(finalized - 10),
                HashiSoraStorage.requestSubmissionHeightKey(stuck) to height(finalized - 300)))
        ).availability(up, now)
        assertTrue(ok.newTransfersAllowed)
        val stalled = client(
            chainValues(mapOf(queue, HashiSoraStorage.requestSubmissionHeightKey(fresh) to height(finalized - 10),
                HashiSoraStorage.requestSubmissionHeightKey(stuck) to height(finalized - 301)))
        ).availability(up, now)
        assertEquals(setOf(HashiBlock.STALLED_REQUESTS), stalled.blocks)
        // No recorded height counts as stalled.
        assertEquals(setOf(HashiBlock.STALLED_REQUESTS), client(chainValues(mapOf(queue))).availability(up, now).blocks)
    }

    @Test
    fun `a signer config that cannot be read blocks`() = runBlocking {
        val availability = client(chainValues()).availability({ throw IOException("offline") }, now)
        assertEquals(setOf(HashiBlock.READ_FAILED), availability.blocks)
    }

    // --- Dry run --------------------------------------------------------------------------------

    @Test
    fun `dry-run results decode to ok, a named module error or an invalid transaction`() {
        assertTrue(HashiDryRun.decode("0x0000").ok)
        val halted = HashiDryRun.decode("0x0001031f60000000") as HashiDryRunResult.DispatchFailed
        assertEquals("EthBridge.RecoveryHalted", halted.error)
        assertEquals(31, halted.module)
        val proxy = HashiDryRun.decode("0x0001036502000000") { module, error -> if (module == 101 && error == 2) "BridgeProxy.NotEnoughLockedLiquidity" else null }
        assertEquals("BridgeProxy.NotEnoughLockedLiquidity", (proxy as HashiDryRunResult.DispatchFailed).error)
        assertEquals("Module(99, 1)", (HashiDryRun.decode("0x0001036301000000") as HashiDryRunResult.DispatchFailed).error)
        assertEquals("BadOrigin", (HashiDryRun.decode("0x000102") as HashiDryRunResult.DispatchFailed).error)
        assertEquals("Invalid.Payment", (HashiDryRun.decode("0x010001") as HashiDryRunResult.Invalid).error)
        assertEquals("Unknown.CannotLookup", (HashiDryRun.decode("0x010100") as HashiDryRunResult.Invalid).error)
        assertTrue(runCatching { HashiDryRun.decode("0x02") }.isFailure)
        assertTrue(runCatching { HashiDryRun.decode("0x000099") }.isFailure)
    }

    @Test
    fun `the dry run goes through state_call BlockBuilder_apply_extrinsic`() = runBlocking {
        var seen: JsonArray? = null
        val result = HashiSoraRpcClient(
            { _, body ->
                val request = Json.parseToJsonElement(body).jsonObject
                seen = request.getValue("params").jsonArray
                assertEquals("state_call", request.getValue("method").jsonPrimitive.content)
                "{\"jsonrpc\":\"2.0\",\"id\":${request.getValue("id").jsonPrimitive.long},\"result\":\"0x0000\"}"
            },
            "https://sora.example",
        ).dryRun("0x1234")
        assertTrue(result.ok)
        assertEquals(listOf("BlockBuilder_apply_extrinsic", "0x1234"), seen!!.map { it.jsonPrimitive.content })
    }
}
