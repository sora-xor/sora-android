package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PolkamarktRuntimeCallFactoryTest {

    @Test
    fun `runtime calls match pinned web argument and SCALE vectors`() {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val fixture = sequenceOf(
            workingDirectory.resolve(
                "feature_polkaswap_impl/src/test/resources/polkamarkt_web_contract.json"
            ),
            workingDirectory.resolve(
                "../feature_polkaswap_impl/src/test/resources/polkamarkt_web_contract.json"
            ).normalize(),
        ).firstOrNull { Files.isRegularFile(it) }
        val vectors = Files.newBufferedReader(checkNotNull(fixture)).use {
            JsonParser.parseReader(it).asJsonObject["canonicalVectors"]
                .asJsonObject["runtimeCalls"].asJsonArray
        }

        vectors.forEach { element ->
            val vector = element.asJsonObject
            val arguments = vector["arguments"].asJsonObject
            val call = callFromVector(vector["call"].asString, arguments)

            assertEquals(
                vector["scaleArgumentsHex"].asString,
                scaleArguments(call).toHex(),
            )
        }
    }

    @Test
    fun `buy call matches the reviewed runtime contract exactly`() {
        val call = PolkamarktRuntimeCallFactory.buy(
            marketId = 7,
            outcome = "Yes",
            collateralIn = BigInteger.valueOf(1_000),
            minimumSharesOut = BigInteger.valueOf(1_900),
        )

        assertHeader(call, method = "buy")
        assertEquals(
            setOf("market_id", "outcome", "collateral_in", "min_shares_out"),
            call.arguments.keys,
        )
        assertEquals(BigInteger.valueOf(7), call.arguments["market_id"])
        assertOutcome(call, "Yes")
        assertEquals(BigInteger.valueOf(1_000), call.arguments["collateral_in"])
        assertEquals(BigInteger.valueOf(1_900), call.arguments["min_shares_out"])
    }

    @Test
    fun `sell call matches the reviewed runtime contract exactly`() {
        val call = PolkamarktRuntimeCallFactory.sell(
            marketId = PolkamarktMarketId.MAX_VALUE,
            outcome = "No",
            sharesIn = BigInteger.valueOf(900),
            minimumCollateralOut = BigInteger.valueOf(400),
        )

        assertHeader(call, method = "sell")
        assertEquals(
            setOf("market_id", "outcome", "shares_in", "min_collateral_out"),
            call.arguments.keys,
        )
        assertEquals(
            BigInteger.valueOf(PolkamarktMarketId.MAX_VALUE),
            call.arguments["market_id"],
        )
        assertOutcome(call, "No")
        assertEquals(BigInteger.valueOf(900), call.arguments["shares_in"])
        assertEquals(BigInteger.valueOf(400), call.arguments["min_collateral_out"])
    }

    @Test
    fun `single trader claim uses claim market and a SCALE u32 id`() {
        val call = PolkamarktRuntimeCallFactory.claimTraderPayout(12)

        assertHeader(call, method = "claim_market")
        assertEquals(mapOf("market_id" to BigInteger.valueOf(12)), call.arguments)
    }

    @Test
    fun `batch trader claim preserves ids and uses claim markets`() {
        val call = PolkamarktRuntimeCallFactory.claimTraderPayouts(
            listOf(1, 9, PolkamarktMarketId.MAX_VALUE)
        )

        assertHeader(call, method = "claim_markets")
        assertEquals(
            mapOf(
                "market_ids" to listOf(
                    BigInteger.ONE,
                    BigInteger.valueOf(9),
                    BigInteger.valueOf(PolkamarktMarketId.MAX_VALUE),
                )
            ),
            call.arguments,
        )
    }

    @Test
    fun `creator fee claim uses the dedicated runtime call`() {
        val call = PolkamarktRuntimeCallFactory.claimCreatorFees(22)

        assertHeader(call, method = "claim_creator_fees")
        assertEquals(mapOf("market_id" to BigInteger.valueOf(22)), call.arguments)
    }

    @Test
    fun `trade calls reject an outcome outside the runtime enum`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktRuntimeCallFactory.buy(
                marketId = 7,
                outcome = "Maybe",
                collateralIn = BigInteger.ONE,
                minimumSharesOut = BigInteger.ONE,
            )
        }

        assertEquals("POLKAMARKT_INVALID_OUTCOME", error.message)
    }

    @Test
    fun `claim calls reject an id outside SCALE u32`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktRuntimeCallFactory.claimCreatorFees(
                PolkamarktMarketId.MAX_VALUE + 1
            )
        }

        assertEquals("POLKAMARKT_INVALID_MARKET_ID", error.message)
    }

    private fun assertHeader(call: PolkamarktRuntimeCall, method: String) {
        assertEquals("Polkamarkt", call.pallet)
        assertEquals(method, call.method)
    }

    private fun assertOutcome(call: PolkamarktRuntimeCall, expected: String) {
        val outcome = call.arguments["outcome"] as DictEnum.Entry<*>
        assertEquals(expected, outcome.name)
        assertNull(outcome.value)
    }

    private fun callFromVector(
        call: String,
        arguments: JsonObject,
    ): PolkamarktRuntimeCall = when (call) {
        "buy" -> PolkamarktRuntimeCallFactory.buy(
            marketId = arguments["market_id"].asLong,
            outcome = arguments["outcome"].asString,
            collateralIn = arguments["collateral_in"].asString.toBigInteger(),
            minimumSharesOut = arguments["min_shares_out"].asString.toBigInteger(),
        )

        "sell" -> PolkamarktRuntimeCallFactory.sell(
            marketId = arguments["market_id"].asLong,
            outcome = arguments["outcome"].asString,
            sharesIn = arguments["shares_in"].asString.toBigInteger(),
            minimumCollateralOut =
                arguments["min_collateral_out"].asString.toBigInteger(),
        )

        "claim_market" -> PolkamarktRuntimeCallFactory.claimTraderPayout(
            arguments["market_id"].asLong
        )

        "claim_markets" -> PolkamarktRuntimeCallFactory.claimTraderPayouts(
            arguments["market_ids"].asJsonArray.map { it.asLong }
        )

        "claim_creator_fees" -> PolkamarktRuntimeCallFactory.claimCreatorFees(
            arguments["market_id"].asLong
        )

        else -> error("Unexpected fixture call: $call")
    }

    private fun scaleArguments(call: PolkamarktRuntimeCall): ByteArray = when (call.method) {
        "buy" ->
            encodeU32(call.arguments.getValue("market_id") as BigInteger) +
                encodeOutcome(call) +
                encodeU128(call.arguments.getValue("collateral_in") as BigInteger) +
                encodeU128(call.arguments.getValue("min_shares_out") as BigInteger)

        "sell" ->
            encodeU32(call.arguments.getValue("market_id") as BigInteger) +
                encodeOutcome(call) +
                encodeU128(call.arguments.getValue("shares_in") as BigInteger) +
                encodeU128(call.arguments.getValue("min_collateral_out") as BigInteger)

        "claim_market", "claim_creator_fees" ->
            encodeU32(call.arguments.getValue("market_id") as BigInteger)

        "claim_markets" -> {
            @Suppress("UNCHECKED_CAST")
            val marketIds = call.arguments.getValue("market_ids") as List<BigInteger>
            require(marketIds.size < 64)
            byteArrayOf((marketIds.size shl 2).toByte()) +
                marketIds.fold(ByteArray(0)) { bytes, value ->
                    bytes + encodeU32(value)
                }
        }

        else -> error("Unexpected runtime call: ${call.method}")
    }

    private fun encodeOutcome(call: PolkamarktRuntimeCall): ByteArray {
        val outcome = call.arguments.getValue("outcome") as DictEnum.Entry<*>
        return byteArrayOf(
            when (outcome.name) {
                "Yes" -> 0.toByte()
                "No" -> 1.toByte()
                else -> error("Unexpected outcome: ${outcome.name}")
            }
        )
    }

    private fun encodeU32(value: BigInteger): ByteArray {
        require(value.signum() >= 0 && value <= BigInteger("4294967295"))
        val raw = value.toLong()
        return ByteArray(4) { index ->
            (raw shr (index * 8)).toByte()
        }
    }

    private fun encodeU128(value: BigInteger): ByteArray {
        require(value.signum() >= 0 && value.bitLength() <= 128)
        val bigEndian = value.toByteArray().let { bytes ->
            if (bytes.size == 17 && bytes[0] == 0.toByte()) {
                bytes.copyOfRange(1, bytes.size)
            } else {
                bytes
            }
        }
        return ByteArray(16) { index ->
            val sourceIndex = bigEndian.lastIndex - index
            if (sourceIndex >= 0) bigEndian[sourceIndex] else 0
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
