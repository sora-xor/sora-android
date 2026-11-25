package jp.co.soramitsu.common.hashi

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.ethereum.EthereumKeyDerivation
import jp.co.soramitsu.common.ethereum.Rlp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dust, conversion, claim selection, halt and codec rules of the bridge. */
class HashiRulesTest {

    private fun hex(text: String) = EthHex.decode(text)

    private val usdt = HashiBridgeAsset(
        HashiAssetKind.SIDECHAIN,
        "0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4",
        18,
        hex("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        6,
    )
    private val ether = HashiBridgeAsset(HashiAssetKind.SIDECHAIN, HashiBridgeContract.ETH_ASSET_ID, 18, ByteArray(20), 18)
    private val xor = HashiBridgeAsset(
        HashiAssetKind.THISCHAIN, HashiBridgeContract.XOR_ASSET_ID, 18, hex("0x1D03EbBd6bE00B805702dDe42313dcF44aC1a6Eb"), 18,
    )
    private val tbcd = HashiBridgeAsset(
        HashiAssetKind.THISCHAIN, HashiBridgeContract.TBCD_ASSET_ID, 18, hex("0xe2f3D42443605Fc4ad5bcE82F0e9BFffBCffA6Ca"), 18,
    )
    private val valAsset = HashiBridgeAsset(
        HashiAssetKind.SIDECHAIN_OWNED, HashiBridgeContract.VAL_ASSET_ID, 18, HashiBridgeContract.valToken, 18,
    )
    private val highDecimals = HashiBridgeAsset(HashiAssetKind.SIDECHAIN, "0x" + "01".repeat(32), 18, ByteArray(20) { 9 }, 24)

    // --- Dust and conversions ------------------------------------------------------------------

    @Test
    fun `outgoing Sidechain amounts are truncated to the token decimals (NonZeroDust)`() {
        assertEquals(BigInteger.TEN.pow(12), HashiAmounts.outgoingStep(usdt))
        assertEquals(6, HashiAmounts.outgoingScale(usdt))
        val amount = BigInteger("1234567891234567891") // 1.234567891234567891 USDT on SORA
        assertTrue(HashiAmounts.hasOutgoingDust(amount, usdt))
        assertFalse(HashiAmounts.hasOutgoingDust(BigInteger("1234567000000000000"), usdt))
        // Typed amounts with more decimals than the token has are refused, never truncated.
        assertNull(HashiAmounts.outgoingUnits(BigDecimal("1.2345671"), usdt))
        assertEquals(BigInteger("1234567000000000000"), HashiAmounts.outgoingUnits(BigDecimal("1.234567"), usdt))
        assertEquals(BigInteger("1234567000000000000"), HashiAmounts.outgoingUnits(BigDecimal("1.234567000"), usdt))
        assertNull(HashiAmounts.outgoingUnits(BigDecimal.ZERO, usdt))
        assertEquals(BigInteger("1"), HashiAmounts.outgoingUnits(BigDecimal("0.000000000000000001"), xor))
        assertNull(HashiAmounts.outgoingUnits(BigDecimal("0.0000000000000000001"), xor))
        assertEquals(BigInteger("1234567"), HashiAmounts.expectedEthAmount(BigInteger("1234567000000000000"), usdt, BigInteger.ONE))
        // 18 → 18 and owned assets have no dust rule.
        assertEquals(BigInteger.ONE, HashiAmounts.outgoingStep(ether))
        assertEquals(BigInteger.ONE, HashiAmounts.outgoingStep(xor))
        assertEquals(BigInteger.ONE, HashiAmounts.outgoingStep(valAsset))
        assertEquals(18, HashiAmounts.outgoingScale(xor))
        // More Ethereum decimals than SORA precision: multiplied, nothing dropped.
        assertEquals(BigInteger.ONE, HashiAmounts.outgoingStep(highDecimals))
        assertEquals(BigInteger.TEN.pow(6), HashiAmounts.expectedEthAmount(BigInteger.ONE, highDecimals, BigInteger.ONE))
    }

    @Test
    fun `owned assets multiply by the denomination factor and divide it back on deposit`() {
        val factor = BigInteger.TEN.pow(38)
        assertEquals(factor.multiply(BigInteger.valueOf(5)), HashiAmounts.expectedEthAmount(BigInteger.valueOf(5), tbcd, factor))
        // A deposit that is not a whole multiple of the factor would be cut: never offered.
        assertNull(HashiAmounts.incomingSoraAmount(BigInteger.TEN.pow(18), tbcd, factor))
        assertEquals(BigInteger.valueOf(3), HashiAmounts.incomingSoraAmount(factor.multiply(BigInteger.valueOf(3)), tbcd, factor))
        assertNull(HashiAmounts.incomingUnits(BigDecimal("1"), tbcd, factor))
    }

    @Test
    fun `incoming amounts convert to SORA precision without losing anything`() {
        assertEquals(BigInteger("1500000000000000000"), HashiAmounts.incomingSoraAmount(BigInteger("1500000"), usdt, BigInteger.ONE))
        assertEquals(BigInteger.ONE, HashiAmounts.incomingStep(usdt, BigInteger.ONE))
        assertEquals(BigInteger.TEN.pow(6), HashiAmounts.incomingStep(highDecimals, BigInteger.ONE))
        assertNull(HashiAmounts.incomingSoraAmount(BigInteger("1000001"), highDecimals, BigInteger.ONE))
        assertEquals(BigInteger.ONE, HashiAmounts.incomingSoraAmount(BigInteger("1000000"), highDecimals, BigInteger.ONE))
        assertNull(HashiAmounts.incomingUnits(BigDecimal("0.000000000000000001999999"), highDecimals, BigInteger.ONE))
        assertEquals(BigInteger("1000000"), HashiAmounts.incomingUnits(BigDecimal("0.000000000000000001"), highDecimals, BigInteger.ONE))
        assertNull(HashiAmounts.incomingUnits(BigDecimal("1.0000001"), usdt, BigInteger.ONE))
        assertEquals(BigInteger("1500000"), HashiAmounts.incomingUnits(BigDecimal("1.5"), usdt, BigInteger.ONE))
        assertNull(HashiAmounts.incomingSoraAmount(BigInteger.ZERO, usdt, BigInteger.ONE))
        assertEquals(BigInteger("7"), HashiAmounts.incomingSoraAmount(BigInteger("7"), xor, BigInteger.ONE))
    }

    @Test
    fun `units convert back to decimals and precision conversion keeps the remainder`() {
        assertEquals(BigDecimal("1.234567"), HashiAmounts.fromUnits(BigInteger("1234567"), 6))
        assertEquals(3, HashiAmounts.decimalsOf(BigDecimal("1.23400")))
        assertEquals(0, HashiAmounts.decimalsOf(BigDecimal("100")))
        assertEquals(BigInteger.ZERO to BigInteger("5"), HashiAmounts.convertPrecision(18, 17, BigInteger("5")))
    }

    // --- Claim selection -----------------------------------------------------------------------

    private val recipient = ByteArray(20) { 0x42 }
    private val sig = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 1))

    private val approved = BigInteger("1234567")

    private fun request(currency: HashiCurrency, to: ByteArray = recipient) = HashiApprovedRequest(
        currency = currency, amount = approved, to = to, from = ByteArray(20) { 3 },
        txHash = ByteArray(32) { 4 }, signatures = sig,
    )

    @Test
    fun `token address of a Sidechain asset claims on the bridge by token`() {
        val claim = HashiClaimPlanner.plan(usdt, request(HashiCurrency.TokenAddress(usdt.ethToken)), recipient, approved)
        assertEquals(HashiClaimCall.Method.RECEIVE_BY_ETHEREUM_ASSET_ADDRESS, claim.method)
        assertArrayEquals(HashiBridgeContract.bridge, claim.contract)
        assertArrayEquals(EthAbi.selector("receiveByEthereumAssetAddress(address,uint256,address,address,bytes32,uint8[],bytes32[],bytes32[])"), claim.data.copyOf(4))
        // v = recovery id + 27 = 28 in the encoded array.
        assertTrue(EthHex.encode(claim.data).contains("000000000000000000000000000000000000000000000000000000000000001c"))
    }

    @Test
    fun `VAL claims mint on the VAL master with from last`() {
        val claim = HashiClaimPlanner.plan(valAsset, request(HashiCurrency.TokenAddress(HashiBridgeContract.valToken)), recipient, approved)
        assertEquals(HashiClaimCall.Method.MINT_TOKENS_BY_PEERS, claim.method)
        assertArrayEquals(HashiBridgeContract.valMaster, claim.contract)
        assertArrayEquals(
            EthAbi.selector("mintTokensByPeers(address,uint256,address,bytes32,uint8[],bytes32[],bytes32[],address)"),
            claim.data.copyOf(4),
        )
        assertFalse(claim.contract.contentEquals(HashiBridgeContract.legacyXorMaster))
    }

    @Test
    fun `asset id of a Thischain asset claims on the bridge by asset id`() {
        val claim = HashiClaimPlanner.plan(xor, request(HashiCurrency.AssetId(hex(xor.assetId))), recipient, approved)
        assertEquals(HashiClaimCall.Method.RECEIVE_BY_SIDECHAIN_ASSET_ID, claim.method)
        assertArrayEquals(HashiBridgeContract.bridge, claim.contract)
    }

    @Test
    fun `every other combination is refused`() {
        listOf(
            { HashiClaimPlanner.plan(xor, request(HashiCurrency.TokenAddress(xor.ethToken)), recipient, approved) },
            { HashiClaimPlanner.plan(usdt, request(HashiCurrency.AssetId(hex(usdt.assetId))), recipient, approved) },
            { HashiClaimPlanner.plan(usdt, request(HashiCurrency.TokenAddress(ByteArray(20) { 5 })), recipient, approved) },
            { HashiClaimPlanner.plan(xor, request(HashiCurrency.AssetId(hex(HashiBridgeContract.VAL_ASSET_ID))), recipient, approved) },
            { HashiClaimPlanner.plan(valAsset, request(HashiCurrency.TokenAddress(ByteArray(20) { 5 })), recipient, approved) },
            { HashiClaimPlanner.plan(usdt, request(HashiCurrency.TokenAddress(usdt.ethToken), to = ByteArray(20)), recipient, approved) },
            { HashiClaimPlanner.plan(usdt, request(HashiCurrency.TokenAddress(usdt.ethToken), to = ByteArray(20)), ByteArray(20), approved) },
            { HashiClaimPlanner.plan(usdt, request(HashiCurrency.TokenAddress(usdt.ethToken)), recipient, approved.add(BigInteger.ONE)) },
        ).forEach { attempt ->
            assertTrue(runCatching { attempt() }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `fallback claim gas follows the web app`() {
        assertEquals(BigInteger.valueOf(211_000), HashiClaimPlanner.fallbackGas(valAsset))
        assertEquals(BigInteger.valueOf(184_000), HashiClaimPlanner.fallbackGas(xor))
        assertEquals(BigInteger.valueOf(155_000), HashiClaimPlanner.fallbackGas(ether))
        assertEquals(BigInteger.valueOf(181_000), HashiClaimPlanner.fallbackGas(usdt))
        assertTrue(ether.isNativeEther)
        assertFalse(usdt.isNativeEther)
    }

    @Test
    fun `dispatch errors that mean paused are recognised`() {
        assertTrue(HashiSoraErrors.meansPaused("EthBridge.RecoveryHalted"))
        assertFalse(HashiSoraErrors.meansPaused("EthBridge.NonZeroDust"))
        assertFalse(HashiSoraErrors.meansPaused(null))
    }

    // --- Codecs -----------------------------------------------------------------------------------

    @Test
    fun `RLP encodes the yellow paper examples`() {
        assertEquals("0x83646f67", EthHex.encode(Rlp.encode(Rlp.bytes("dog".toByteArray()))))
        assertEquals(
            "0xc88363617483646f67",
            EthHex.encode(Rlp.encode(Rlp.list(Rlp.bytes("cat".toByteArray()), Rlp.bytes("dog".toByteArray())))),
        )
        assertEquals("0x80", EthHex.encode(Rlp.encode(Rlp.scalar(0))))
        assertEquals("0x0f", EthHex.encode(Rlp.encode(Rlp.scalar(15))))
        assertEquals("0x820400", EthHex.encode(Rlp.encode(Rlp.scalar(1024))))
        assertEquals("0xc0", EthHex.encode(Rlp.encode(Rlp.list(emptyList()))))
        val lorem = "Lorem ipsum dolor sit amet, consectetur adipisicing elit".toByteArray()
        assertEquals("0xb838" + EthHex.encode(lorem, withPrefix = false), EthHex.encode(Rlp.encode(Rlp.bytes(lorem))))
    }

    @Test
    fun `revert reasons are read from Error(string) data`() {
        val data = EthAbi.selector("Error(string)") +
            EthAbi.uintWord(BigInteger.valueOf(32)) + EthAbi.uintWord(BigInteger.valueOf(19)) +
            "txHash already used".toByteArray().copyOf(32)
        assertEquals("txHash already used", EthAbi.decodeRevertReason(data))
        assertNull(EthAbi.decodeRevertReason(ByteArray(4)))
        assertNull(EthAbi.decodeRevertReason(EthAbi.selector("Error(string)") + ByteArray(31)))
    }

    @Test
    fun `quantities and fixed values are strict`() {
        assertEquals(BigInteger.ZERO, EthHex.parseQuantity("0x0"))
        assertEquals(BigInteger.valueOf(255), EthHex.parseQuantity("0xff"))
        assertEquals("0x0", EthHex.quantity(BigInteger.ZERO))
        assertTrue(runCatching { EthHex.parseQuantity("12") }.isFailure)
        assertTrue(runCatching { EthHex.decode("0x123") }.isFailure)
        assertTrue(runCatching { EthHex.decode("0xzz") }.isFailure)
        assertTrue(runCatching { EthHex.toFixed(BigInteger.ONE.shiftLeft(256), 32) }.isFailure)
        assertTrue(runCatching { EthAbi.decodeBool(EthAbi.uintWord(BigInteger.TWO)) }.isFailure)
        assertTrue(runCatching { EthAbi.decodeAddress(ByteArray(32) { 1 }) }.isFailure)
    }

    @Test
    fun `BIP-32 paths are parsed strictly and an invalid phrase is refused`() {
        assertEquals(
            listOf(0x8000002cL, 0x8000003cL, 0x80000000L, 0L, 0L),
            EthereumKeyDerivation.parsePath("m/44'/60'/0'/0/0"),
        )
        assertTrue(runCatching { EthereumKeyDerivation.parsePath("44'/60'") }.isFailure)
        assertTrue(runCatching { EthereumKeyDerivation.parsePath("m/2147483648") }.isFailure)
        assertTrue(runCatching { EthereumKeyDerivation.derive("abandon abandon") }.isFailure)
        // Separators and spacing of the stored text do not change the account.
        val canonical = EthereumKeyDerivation.derive("bottom drive obey lake curtain smoke basket hold race lonely fit walk")
        val spaced = EthereumKeyDerivation.derive("  bottom drive obey lake curtain smoke\nbasket hold  race lonely fit walk ")
        assertArrayEquals(canonical.address, spaced.address)
        canonical.destroy()
        spaced.destroy()
    }
}
