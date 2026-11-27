package jp.co.soramitsu.common.hashi

import java.time.Instant
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * LIVE NETWORK TEST against SORA and Ethereum mainnet, read-only: no key, nothing signed or
 * sent (storage reads, RPC reads, `eth_call` and `eth_estimateGas` only). Off by default; run with
 *
 *     RUN_LIVE_HASHI_TESTS=true ./gradlew :common:testProductionDebugUnitTest \
 *       --tests '*HashiLiveMainnetTest*' --rerun
 *
 * It uses the wallet's own bounded HTTPS client and bridge clients against the reviewed SORA node
 * (https://mof2.sora.org) and the public Ethereum endpoints the app uses.
 */
class HashiLiveMainnetTest {

    private val bounded = BoundedHashiRpcTransport(BoundedHttpTextClient())

    /**
     * Every request here is a read, so a transport failure (a deadline on a loaded build machine,
     * a dropped connection) is asked again, up to three times. An answer is never asked again:
     * a node error or an unexpected value fails the test as it is.
     */
    private val transport = HashiRpcTransport { endpoint, body ->
        var failure: BoundedHttpTextException? = null
        repeat(TRANSPORT_ATTEMPTS) {
            try {
                return@HashiRpcTransport bounded.post(endpoint, body)
            } catch (error: BoundedHttpTextException) {
                failure = error
            }
        }
        throw checkNotNull(failure)
    }
    private val sora = HashiSoraRpcClient(transport)
    private val ethereum = EthereumRpcClient(transport)
    private val vectors = Json.parseToJsonElement(checkNotNull(javaClass.classLoader!!.getResource("hashi/eth.json")).readText()).jsonObject

    @Before
    fun requireOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_HASHI_TESTS") == "true")
    }

    private companion object {
        const val TRANSPORT_ATTEMPTS = 3
    }

    @Test
    fun `availability today - recovery complete, signer report missing, so new transfers are paused`() = runBlocking {
        val config = HashiSignerReportSource { BoundedHttpTextClient().getUtf8(HashiSignerReports.CONFIG_URL, 4 * 1024 * 1024) }
        val availability = sora.availability(config, Instant.now())
        println("LIVE availability spec=${availability.specVersion} finalized=${availability.finalizedBlock} blocks=${availability.blocks} signers=${availability.signerVerdict}")
        assertTrue(availability.specVersion!! >= 142)
        assertEquals(setOf(HashiBlock.SIGNERS_UNVERIFIED), availability.blocks)
        assertEquals(HashiSignerReports.Verdict.MISSING, availability.signerVerdict)
        assertFalse(availability.newTransfersAllowed)
    }

    @Test
    fun `recovery keys are complete by the 142 rule and the legacy latch is still set`() = runBlocking {
        val keys = (HashiRecoveryKeys.RECOVERY + HashiRecoveryKeys.LEGACY_BRIDGE_HALT + HashiRecoveryKeys.GUARDIAN_HALT)
        val values = sora.storage(keys.map(HashiRecoveryKeys::hex))
        keys.forEach { println("LIVE $it = ${values[HashiRecoveryKeys.hex(it)]}") }
        val verdict = HashiRecoveryCompletion.evaluate { key -> values[HashiRecoveryKeys.hex(key)]?.let(EthHex::decode) }
        assertEquals(HashiRecoveryCompletion.Verdict.COMPLETE, verdict)
        assertEquals("0x01", values[HashiRecoveryKeys.hex(HashiRecoveryKeys.LEGACY_BRIDGE_HALT)])
        assertEquals(null, values[HashiRecoveryKeys.hex(HashiRecoveryKeys.GUARDIAN_HALT)])
    }

    @Test
    fun `contract addresses on SORA are the pinned ones`() = runBlocking {
        val bridgeKey = HashiSoraStorage.bridgeContractAddressKey()
        val valKey = HashiSoraStorage.valMasterContractAddressKey
        val statusKey = HashiSoraStorage.bridgeStatusKey()
        val values = sora.storage(listOf(bridgeKey, valKey, statusKey))
        assertEquals(HashiBridgeContract.BRIDGE_ADDRESS, EthAddress.checksum(HashiSoraStorage.decodeAddress(values.getValue(bridgeKey)!!)))
        assertEquals(HashiBridgeContract.VAL_MASTER_ADDRESS, EthAddress.checksum(HashiSoraStorage.decodeAddress(values.getValue(valKey)!!)))
        assertEquals("0x00", values[statusKey])
    }

    @Test
    fun `registered assets parse - 197 rows, 174 Sidechain, 22 Thischain, 1 SidechainOwned`() = runBlocking {
        val assets = sora.registeredAssets()
        val counts = assets.groupingBy { it.kind }.eachCount()
        println("LIVE registered assets ${assets.size} $counts")
        assertEquals(197, assets.size)
        assertEquals(174, counts[HashiAssetKind.SIDECHAIN])
        assertEquals(22, counts[HashiAssetKind.THISCHAIN])
        assertEquals(1, counts[HashiAssetKind.SIDECHAIN_OWNED])
        val ether = assets.single { it.assetId == HashiBridgeContract.ETH_ASSET_ID }
        assertTrue(ether.ethToken!!.all { it == 0.toByte() })
    }

    @Test
    fun `sidechain tokens of XOR and PSWAP and their decimals`() = runBlocking {
        val xor = checkNotNull(ethereum.sidechainToken(EthHex.decode(HashiBridgeContract.XOR_ASSET_ID)))
        assertEquals("0x1D03EbBd6bE00B805702dDe42313dcF44aC1a6Eb", EthAddress.checksum(xor))
        val pswap = checkNotNull(ethereum.sidechainToken(EthHex.decode("0x0200050000000000000000000000000000000000000000000000000000000000")))
        assertEquals("0x519C1001D550C0a1DaE7d1fC220f7d14c2A521BB", EthAddress.checksum(pswap))
        assertEquals(18, ethereum.erc20Decimals(xor))
        assertEquals(18, ethereum.erc20Decimals(pswap))
        // The Thischain ERC-20 maps back to its SORA asset (the incoming pre-check).
        assertEquals(HashiBridgeContract.XOR_ASSET_ID, EthHex.encode(ethereum.sidechainAssetOfToken(xor)))
        assertTrue(ethereum.isAcceptedEthToken(EthHex.decode("0xdac17f958d2ee523a2206206994597c13d831ec7")))
    }

    @Test
    fun `both restored approved requests are already used and their claims revert with txHash already used`() = runBlocking {
        vectors.getValue("approvedRequestToClaim").jsonArray.forEach { element ->
            val case = element.jsonObject
            val requestHash = EthHex.decode(case.getValue("requestHash").jsonPrimitive.content)
            val request = checkNotNull(sora.approvedRequest(requestHash))
            val asset = HashiBridgeAsset(
                HashiAssetKind.THISCHAIN,
                EthHex.encode((request.currency as HashiCurrency.AssetId).id),
                18,
                ByteArray(20) { 1 },
                18,
            )
            val claim = HashiClaimPlanner.plan(asset, request, request.to, request.amount)
            assertEquals(case.getValue("calldata").jsonPrimitive.content, EthHex.encode(claim.data))
            assertTrue(ethereum.isUsed(claim.contract, claim.txHash))
            val revert = runCatching { ethereum.estimateGas(request.to, claim.contract, claim.data) }.exceptionOrNull()
            println("LIVE claim ${case.getValue("requestHash").jsonPrimitive.content}: $revert ${(revert as? EthCallRevertedException)?.reason}")
            assertTrue(revert is EthCallRevertedException)
            assertEquals("txHash already used", (revert as EthCallRevertedException).reason)
        }
    }

    @Test
    fun `locked liquidity and Ethereum fees read`() = runBlocking {
        val xor = sora.lockedAssets(EthHex.decode(HashiBridgeContract.XOR_ASSET_ID))
        val baseFee = ethereum.latestBaseFee()
        val priority = ethereum.maxPriorityFeePerGas()
        val fees = HashiEthereumPolicy.fees(baseFee, priority, HashiBridgeContract.GAS_RECEIVE_BY_SIDECHAIN_ASSET_ID)
        println("LIVE lockedAssets(XOR)=$xor baseFee=$baseFee priority=$priority claimMaxCost=${fees.maximumCost}")
        assertTrue(xor.signum() > 0)
        assertTrue(fees.maxPriorityFeePerGas <= HashiBridgeContract.MAX_PRIORITY_FEE_PER_GAS)
        assertEquals(java.math.BigInteger.TEN.pow(38), sora.denominator())
    }

    @Test
    fun `the dry run goes through state_call because system_dryRun is refused as unsafe`() = runBlocking {
        val dryRun = runCatching { sora.request("system_dryRun", buildJsonArray { add("0x00") }) }.exceptionOrNull()
        println("LIVE system_dryRun: ${dryRun?.message}")
        assertTrue(dryRun is HashiRpcErrorException && dryRun.message.contains("unsafe", ignoreCase = true))
        val applied = runCatching {
            sora.request("state_call", buildJsonArray { add("BlockBuilder_apply_extrinsic"); add("0x00") })
        }.exceptionOrNull()
        // Garbage input traps in the runtime: the method itself is open to the app.
        println("LIVE state_call apply_extrinsic: ${applied?.message?.take(120)}")
        assertTrue(applied is HashiRpcErrorException && !applied.message.contains("unsafe", ignoreCase = true))
    }
}
