package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.*
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xcrypto.util.fromHex
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger

/** Checks the app-to-runtime boundary without a signer or network connection. */
class ExtrinsicArgumentsContractTest {
    private val builder = mockk<ExtrinsicBuilder>()
    private val pallet = slot<String>()
    private val method = slot<String>()
    private val arguments = slot<Map<String, Any?>>()
    private val xor = "0x" + "02" + "00".repeat(31)
    private val valId = "0x" + "04" + "00".repeat(31)
    private val reward = "0x" + "05" + "00".repeat(31)
    private val alicePublicKey = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    private val address = alicePublicKey.fromHex().toAddress(42.toShort())
    private val amount = BigInteger("123456789012345678901234567890")
    private val limit = BigInteger("9876543210123456789")
    @Before fun setup() {
        every { builder.call(capture(pallet), capture(method), capture(arguments)) } returns builder
    }
    private fun header(expectedPallet: String, expectedMethod: String) {
        assertEquals(expectedPallet, pallet.captured); assertEquals(expectedMethod, method.captured)
        verify(exactly = 1) { builder.call(any<String>(), any<String>(), any()) }
    }
    private fun asset(name: String, expectedFirstByte: Int) {
        val code = (arguments.captured.getValue(name) as Struct.Instance).get<List<BigInteger>>("code")!!
        assertEquals(32, code.size); assertEquals(expectedFirstByte.toBigInteger(), code.first())
        assertTrue(code.drop(1).all { it == BigInteger.ZERO })
    }
    private fun recipient(name: String) {
        val bytes = arguments.captured.getValue(name) as ByteArray
        assertEquals(alicePublicKey, bytes.joinToString("") { "%02x".format(it) })
    }
    @Test fun `transfer maps the exact asset recipient and full precision amount`() {
        assertSame(builder, builder.transfer(xor, address, amount)); header("Assets", "transfer")
        assertEquals(setOf("asset_id", "to", "amount"), arguments.captured.keys)
        asset("asset_id", 2); recipient("to"); assertEquals(amount, arguments.captured["amount"])
    }
    @Test fun `invalid transfer address never reaches runtime call builder`() {
        assertThrows(Exception::class.java) { builder.transfer(xor, "invalid", amount) }
        verify(exactly = 0) { builder.call(any<String>(), any<String>(), any()) }
    }
    @Test fun `set referrer encodes the account rather than its display address`() {
        builder.setReferrer(address); header("Referrals", "set_referrer"); recipient("referrer")
        assertEquals(setOf("referrer"), arguments.captured.keys)
    }
    @Test fun `bond uses reserve with integer balance`() {
        builder.referralBond(amount); header("Referrals", "reserve"); assertEquals(mapOf("balance" to amount), arguments.captured)
    }
    @Test fun `unbond uses unreserve with integer balance`() {
        builder.referralUnbond(amount); header("Referrals", "unreserve"); assertEquals(mapOf("balance" to amount), arguments.captured)
    }
    @Test fun `migration preserves UTF8 identity key and signature bytes`() {
        builder.migrate("アリス@sora", "public-key", "signature"); header("IrohaMigration", "migrate")
        assertArrayEquals("アリス@sora".toByteArray(Charsets.UTF_8), arguments.captured["iroha_address"] as ByteArray)
        assertArrayEquals("public-key".toByteArray(), arguments.captured["iroha_public_key"] as ByteArray)
        assertArrayEquals("signature".toByteArray(), arguments.captured["iroha_signature"] as ByteArray)
    }
    @Test fun `desired input swap encodes output floor and selected liquidity sources`() {
        builder.swap(0, xor, valId, amount, limit, "AllowSelected", listOf("XYKPool", "XSTPool"), WithDesired.INPUT)
        header("LiquidityProxy", "swap"); asset("input_asset_id", 2); asset("output_asset_id", 4)
        assertEquals(BigInteger.ZERO, arguments.captured["dex_id"])
        val swap = arguments.captured["swap_amount"] as DictEnum.Entry<*>
        assertEquals("WithDesiredInput", swap.name)
        val payload = swap.value as Struct.Instance
        assertEquals(amount, payload.get<BigInteger>("desired_amount_in")); assertEquals(limit, payload.get<BigInteger>("min_amount_out"))
        val sources = arguments.captured["selected_source_types"] as List<*>
        assertEquals(listOf("XYKPool", "XSTPool"), sources.map { (it as DictEnum.Entry<*>).name })
        assertEquals("AllowSelected", (arguments.captured["filter_mode"] as DictEnum.Entry<*>).name)
    }
    @Test fun `desired output swap encodes maximum input without reversing assets`() {
        builder.swap(1, xor, valId, amount, limit, "Disabled", emptyList(), WithDesired.OUTPUT)
        header("LiquidityProxy", "swap"); asset("input_asset_id", 2); asset("output_asset_id", 4)
        assertEquals(BigInteger.ONE, arguments.captured["dex_id"])
        val swap = arguments.captured["swap_amount"] as DictEnum.Entry<*>
        assertEquals("WithDesiredOutput", swap.name)
        val payload = swap.value as Struct.Instance
        assertEquals(amount, payload.get<BigInteger>("desired_amount_out")); assertEquals(limit, payload.get<BigInteger>("max_amount_in"))
        assertEquals(emptyList<Any>(), arguments.captured["selected_source_types"])
    }
    @Test fun `liquidity removal keeps marker and each asset minimum distinct`() {
        builder.removeLiquidity(0, xor, valId, amount, limit, BigInteger.TEN); header("PoolXYK", "withdraw_liquidity")
        asset("output_asset_a", 2); asset("output_asset_b", 4)
        assertEquals(amount, arguments.captured["marker_asset_desired"]); assertEquals(limit, arguments.captured["output_a_min"])
        assertEquals(BigInteger.TEN, arguments.captured["output_b_min"])
    }
    @Test fun `new pair registration preserves dex and token order`() {
        builder.register(1, xor, valId); header("TradingPair", "register")
        assertEquals(BigInteger.ONE, arguments.captured["dex_id"]); asset("base_asset_id", 2); asset("target_asset_id", 4)
    }
    @Test fun `pool initialization uses the pool pallet and exact pair`() {
        builder.initializePool(0, xor, valId); header("PoolXYK", "initialize_pool")
        asset("asset_a", 2); asset("asset_b", 4); assertEquals(BigInteger.ZERO, arguments.captured["dex_id"])
    }
    @Test fun `liquidity supply keeps each desired amount and each minimum distinct`() {
        builder.depositLiquidity(0, xor, valId, amount, limit, BigInteger.TEN, BigInteger.ONE); header("PoolXYK", "deposit_liquidity")
        asset("input_asset_a", 2); asset("input_asset_b", 4)
        assertEquals(amount, arguments.captured["input_a_desired"]); assertEquals(limit, arguments.captured["input_b_desired"])
        assertEquals(BigInteger.TEN, arguments.captured["input_a_min"]); assertEquals(BigInteger.ONE, arguments.captured["input_b_min"])
    }
    @Test fun `faucet request uses target account and faucet pallet`() {
        builder.faucetTransfer(valId, address, amount); header("Faucet", "transfer")
        asset("asset_id", 4); recipient("target"); assertEquals(amount, arguments.captured["amount"])
    }
    @Test fun `farm deposit defaults to farm mode and retains pooled units`() {
        builder.depositDemeter(xor, valId, reward, amount = amount); header("DemeterFarmingPlatform", "deposit")
        asset("base_asset", 2); asset("pool_asset", 4); asset("reward_asset", 5)
        assertEquals(true, arguments.captured["is_farm"]); assertEquals(amount, arguments.captured["pooled_tokens"])
    }
    @Test fun `staking withdrawal retains explicit non farm mode`() {
        builder.withdrawDemeter(xor, valId, reward, false, amount); header("DemeterFarmingPlatform", "withdraw")
        asset("base_asset", 2); asset("pool_asset", 4); asset("reward_asset", 5)
        assertEquals(false, arguments.captured["is_farm"]); assertEquals(amount, arguments.captured["pooled_tokens"])
    }
    @Test fun `reward claim carries no fabricated amount`() {
        builder.claimDemeter(xor, valId, reward, false); header("DemeterFarmingPlatform", "get_rewards")
        asset("base_asset", 2); asset("pool_asset", 4); asset("reward_asset", 5)
        assertEquals(false, arguments.captured["is_farm"])
        assertEquals(setOf("base_asset", "pool_asset", "reward_asset", "is_farm"), arguments.captured.keys)
    }
    @Test fun `market buy forwards the reviewed outcome and exact output floor`() {
        builder.polkamarktBuy(7, "Yes", amount, limit); header("Polkamarkt", "buy")
        assertEquals(7.toBigInteger(), arguments.captured["market_id"]); assertEquals("Yes", (arguments.captured["outcome"] as DictEnum.Entry<*>).name)
        assertEquals(amount, arguments.captured["collateral_in"]); assertEquals(limit, arguments.captured["min_shares_out"])
    }
    @Test fun `market sell forwards exact shares and collateral floor`() {
        builder.polkamarktSell(7, "No", amount, limit); header("Polkamarkt", "sell")
        assertEquals("No", (arguments.captured["outcome"] as DictEnum.Entry<*>).name)
        assertEquals(amount, arguments.captured["shares_in"]); assertEquals(limit, arguments.captured["min_collateral_out"])
    }
    @Test fun `single trader claim is distinct from creator fees`() {
        builder.polkamarktClaimTraderPayout(7); header("Polkamarkt", "claim_market")
        assertEquals(mapOf("market_id" to 7.toBigInteger()), arguments.captured)
    }
    @Test fun `batch trader claim preserves review order`() {
        builder.polkamarktClaimTraderPayouts(listOf(7, 2)); header("Polkamarkt", "claim_markets")
        assertEquals(mapOf("market_ids" to listOf(7.toBigInteger(), 2.toBigInteger())), arguments.captured)
    }
    @Test fun `creator fee claim uses the dedicated runtime method`() {
        builder.polkamarktClaimCreatorFees(7); header("Polkamarkt", "claim_creator_fees")
        assertEquals(mapOf("market_id" to 7.toBigInteger()), arguments.captured)
    }
}
