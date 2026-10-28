package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger

class SubstratePoolReadTest {
    private val socket = mockk<SocketService>()
    private val runtime = mockk<RuntimeManager>()
    private val subject = SubstrateApiImpl(socket, runtime)
    private val requests = mutableListOf<RuntimeRequest>()
    private var reply: (RuntimeRequest) -> Any? = { error("Unexpected RPC ${it.method}") }
    private val xor = "0x02" + "00".repeat(31)
    private val valId = "0x04" + "00".repeat(31)
    private val reward = "0x05" + "00".repeat(31)
    private val reserve = "11".repeat(32).fromHex()
    private val owner = "22".repeat(32).fromHex().toAddress(42.toShort())
    private val first = BigInteger("123456789012345678901234567890")
    private val second = BigInteger("98765432109876543210")
    @Before fun setup() {
        every { socket.jsonMapper } returns Gson()
        coEvery { runtime.getRuntimeSnapshot() } returns TestRuntimeSnapshots.mainnet133
        every { runtime.toSoraAddress(any()) } answers { firstArg<ByteArray>().toAddress(69.toShort()) }
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests.add(request)
            val callback = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            try { callback.onNext(RpcResponse("2.0", reply(request), request.id, null)) }
            catch (error: Throwable) { callback.onError(error) }
            mockk(relaxed = true)
        }
    }
    private fun u128(value: BigInteger) = (0 until 16).joinToString("") { value.shiftRight(it * 8).and(BigInteger.valueOf(255)).toString(16).padStart(2, '0') }
    private fun replies(vararg values: Any?) { var index = 0; reply = { check(index < values.size) { "Unexpected request ${it.method}" }; values[index++] } }
    @Test fun `pool reserves decode both full precision integers`() = runTest {
        replies("0x" + u128(first) + u128(second))
        assertEquals(first to second, subject.getPoolReserves(xor, valId))
        assertEquals("state_getStorage", requests.single().method)
    }
    @Test fun `missing pool reserve is absent rather than invented zero`() = runTest {
        replies(null); assertNull(subject.getPoolReserves(xor, valId))
    }
    @Test fun `malformed pool reserves cannot be used as valid balances`() = runTest {
        replies("0x01")
        try { subject.getPoolReserves(xor, valId); fail("Incomplete SCALE reserve") } catch (_: Exception) { }
    }
    @Test fun `reserve account is first account in pool properties`() = runTest {
        replies("0x" + "11".repeat(32) + "33".repeat(32))
        assertArrayEquals(reserve, subject.getPoolReserveAccount(xor, valId.fromHex()))
    }
    @Test fun `pool without properties has no reserve account`() = runTest {
        replies(null); assertNull(subject.getPoolReserveAccount(xor, valId.fromHex()))
    }
    @Test fun `pool issuance retains integer precision`() = runTest {
        replies("0x" + u128(first)); assertEquals(first, subject.getPoolTotalIssuances(reserve))
    }
    @Test fun `pool without issuance remains absent`() = runTest {
        replies(null); assertNull(subject.getPoolTotalIssuances(reserve))
    }
    @Test fun `user pool joins reserves issuance and providers without changing token order`() = runTest {
        replies("0x" + u128(first) + u128(second), "0x" + "11".repeat(32) + "33".repeat(32), "0x" + u128(BigInteger.valueOf(100)), "0x" + u128(BigInteger.TEN))
        val pool = subject.getUserPoolsData(owner, xor, listOf(valId.fromHex())).single()
        assertEquals(xor, pool.baseAssetId); assertEquals(valId, pool.assetId)
        assertEquals(first, pool.reservesFirst); assertEquals(second, pool.reservesSecond)
        assertEquals(BigInteger.valueOf(100), pool.totalIssuance); assertEquals(BigInteger.TEN, pool.poolProvidersBalance)
        assertEquals(reserve.toAddress(69.toShort()), pool.reservesAccount)
    }
    @Test fun `missing provider account means zero ownership of an existing pool`() = runTest {
        replies("0x" + u128(first) + u128(second), "0x" + "11".repeat(32) + "33".repeat(32), "0x" + u128(BigInteger.valueOf(100)), null)
        assertEquals(BigInteger.ZERO, subject.getUserPoolsData(owner, xor, listOf(valId.fromHex())).single().poolProvidersBalance)
    }
    @Test fun `pool without properties is omitted without issuance lookup`() = runTest {
        replies("0x" + u128(first) + u128(second), null)
        assertTrue(subject.getUserPoolsData(owner, xor, listOf(valId.fromHex())).isEmpty()); assertEquals(2, requests.size)
    }
    @Test fun `pool without issuance is omitted without provider lookup`() = runTest {
        replies("0x" + u128(first) + u128(second), "0x" + "11".repeat(32) + "33".repeat(32), null)
        assertTrue(subject.getUserPoolsData(owner, xor, listOf(valId.fromHex())).isEmpty()); assertEquals(3, requests.size)
    }
    @Test fun `empty requested token list makes no RPC calls`() = runTest {
        assertTrue(subject.getUserPoolsData(owner, xor, emptyList()).isEmpty()); assertTrue(requests.isEmpty())
    }
    @Test fun `account pool tokens decode their exact asset bytes and base identity`() = runTest {
        val key = "0x" + "aa".repeat(48) + xor.removePrefix("0x")
        replies(listOf(key), "0x08" + valId.removePrefix("0x") + reward.removePrefix("0x"))
        val pair = subject.getUserPoolsTokenIds(owner).single()
        assertEquals(xor, pair.first); assertEquals(2, pair.second.size)
        assertArrayEquals(valId.fromHex(), pair.second[0]); assertArrayEquals(reward.fromHex(), pair.second[1])
    }
    @Test fun `account with no pool keys does not query storage values`() = runTest {
        replies(emptyList<String>()); assertTrue(subject.getUserPoolsTokenIds(owner).isEmpty())
        assertEquals("state_getKeys", requests.single().method)
    }
    @Test fun `available path sends dex and ordered assets`() = runTest {
        replies(true); assertTrue(subject.isSwapAvailable(xor, valId, 1))
        assertEquals("liquidityProxy_isPathAvailable", requests.single().method); assertEquals(listOf(1, xor, valId), requests.single().params)
    }
    @Test fun `enabled liquidity sources retain runtime ordering`() = runTest {
        replies(listOf("XYKPool", "XSTPool")); assertEquals(listOf("XYKPool", "XSTPool"), subject.fetchAvailableSources(xor, valId, 1))
        assertEquals("liquidityProxy_listEnabledSourcesForPath", requests.single().method); assertEquals(listOf(1, xor, valId), requests.single().params)
    }
    @Test fun `pair availability keeps false and sends the requested dex`() = runTest {
        replies(false); assertFalse(subject.isPairEnabled(xor, valId, 1))
        assertEquals("tradingPair_isPairEnabled", requests.single().method); assertEquals(listOf(1, xor, valId), requests.single().params)
    }
    @Test fun `cancelled pool read is not reported as an empty wallet`() = runTest {
        reply = { throw CancellationException("wallet changed") }
        try { subject.getUserPoolsTokenIds(owner); fail("Cancellation") } catch (_: CancellationException) { }
    }
}
