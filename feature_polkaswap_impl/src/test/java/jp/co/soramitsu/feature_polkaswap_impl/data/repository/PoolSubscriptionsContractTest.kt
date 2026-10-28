package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.PoolDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.sora.substrate.runtime.*
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.xsubstrate.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PoolSubscriptionsContractTest {
    private val db = mockk<AppDatabase>()
    private val pools = mockk<PoolDao>()
    private val assets = mockk<AssetDao>()
    private val api = mockk<SubstrateApi>()
    private val calls = mockk<SubstrateCalls>()
    private val explorer = mockk<BlockExplorerManager>()
    private val runtime = mockk<RuntimeManager>()
    private val socket = mockk<SocketService>()
    private val subject = PolkaswapSubscriptionRepositoryImpl(db, api, SwapMarketMapper(), calls, explorer, runtime, socket)
    private val xor = TestTokens.xorToken.id
    private val valId = TestTokens.valToken.id
    private val reserve = ByteArray(32) { 17 }
    private val owner = ByteArray(32) { 34 }.toAddress(69.toShort())
    private val reserveAddress = reserve.toAddress(69.toShort())
    private val requests = mutableListOf<RuntimeRequest>()
    private var rpcReply: (RuntimeRequest) -> Any? = { error("Unexpected RPC ${it.method}") }
    private val storage = mutableMapOf<String, MutableStateFlow<String>>()
    private val basic = BasicPoolLocal(xor, valId, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, reserveAddress)
    private val base = BasePoolWithTokenLocal(PoolBaseTokenLocal(xor, 1), TokenLocal(xor, "XOR", "XOR", 18, true, "SORA", false))
    private fun u128(value: BigInteger) = (0 until 16).joinToString("") { value.shiftRight(it * 8).and(BigInteger.valueOf(255)).toString(16).padStart(2, '0') }
    private fun assertDecimal(expected: String, actual: BigDecimal) = assertEquals(expected, 0, BigDecimal(expected).compareTo(actual))
    private fun stateKey(storage: Storage, vararg args: Any) = snapshot.metadata.module(Pallete.POOL_XYK.palletName).storage(storage.storageName).storageKey(snapshot, *args)
    @Before fun setup() {
        every { db.poolDao() } returns pools; every { db.assetDao() } returns assets
        coEvery { runtime.getRuntimeSnapshot() } returns snapshot
        every { runtime.toSoraAddress(any()) } answers { firstArg<ByteArray>().toAddress(69.toShort()) }
        coEvery { pools.getBasicPool(any(), any()) } returns null
        coEvery { pools.getBasicPools() } returns emptyList()
        coEvery { pools.getPoolBaseTokens() } returns listOf(base)
        coEvery { pools.getPoolBaseToken(xor) } returns base.base
        coEvery { pools.deleteBasicPools(any()) } just Runs
        coEvery { pools.insertBasicPools(any()) } just Runs
        every { pools.subscribePool(valId, xor) } returns flowOf(emptyList())
        coEvery { assets.getWhitelistOfToken(any()) } returns "SORA"
        coEvery { assets.getPrecisionOfToken(xor) } returns 18
        coEvery { assets.getPrecisionOfToken(valId) } returns 6
        every { explorer.getTempApy(any()) } returns null
        every { calls.observeStorage(any()) } answers { storage.getOrPut(firstArg()) { MutableStateFlow("initial") } }
        every { socket.jsonMapper } returns Gson()
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests.add(request)
            val callback = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            try { callback.onNext(RpcResponse("2.0", rpcReply(request), request.id, null)) }
            catch (error: Throwable) { callback.onError(error) }
            mockk(relaxed = true)
        }
    }
    @Test fun `remote reserves keep both independent token precisions and percentage APY`() = runTest {
        coEvery { api.getPoolReserves(xor, valId) } returns (BigInteger("1234567890123456789") to BigInteger("2345678"))
        coEvery { pools.getBasicPool(xor, valId) } returns basic
        every { explorer.getTempApy(reserveAddress) } returns 0.123
        val result = subject.getRemotePoolReserves(xor, 18, valId, 6, true, false)
        assertDecimal("1.234567890123456789", result.firstReserves); assertDecimal("2.345678", result.secondReserves)
        assertDecimal("0", result.firstPooled); assertDecimal("0", result.secondPooled); assertEquals(12.3, result.sbApy!!, 0.00001)
    }
    @Test fun `present but disabled pair still reads its reserve quantities`() = runTest {
        coEvery { api.getPoolReserves(xor, valId) } returns (BigInteger.TEN to BigInteger.ONE)
        val result = subject.getRemotePoolReserves(xor, 0, valId, 0, false, true)
        assertDecimal("10", result.firstReserves); assertDecimal("1", result.secondReserves)
    }
    @Test fun `unknown pair never invents network reserve or APY reads`() = runTest {
        assertEquals(LiquidityData(), subject.getRemotePoolReserves(xor, 18, valId, 6, false, false))
        coVerify(exactly = 0) { api.getPoolReserves(any(), any()); pools.getBasicPool(any(), any()) }
    }
    @Test fun `missing reserve values become explicit zero with no invented APY`() = runTest {
        coEvery { api.getPoolReserves(xor, valId) } returns null
        val result = subject.getRemotePoolReserves(xor, 18, valId, 6, true, true)
        assertDecimal("0", result.firstReserves); assertDecimal("0", result.secondReserves); assertNull(result.sbApy)
    }
    @Test fun `swap quote uses exact decimal units source filter dex and route`() = runTest {
        rpcReply = { mapOf("amount" to "9876543210987654321", "route" to listOf(xor, valId)) }
        val quote = subject.getSwapQuote(xor, valId, BigDecimal("1.234567890123456789"), WithDesired.INPUT, listOf(Market.XYK), TestTokens.xorToken, 1)!!
        assertDecimal("9.876543210987654321", quote.amount)
        assertEquals(listOf(xor, valId), quote.route)
        assertEquals("liquidityProxy_quote", requests.single().method)
        assertEquals(listOf(1, xor, valId, "1234567890123456789", WithDesired.INPUT.backString, listOf(Market.XYK.backString), "AllowSelected"), requests.single().params)
    }
    @Test fun `smart quote uses disabled source filtering and desired output`() = runTest {
        rpcReply = { mapOf("amount" to "7") }
        val quote = subject.getSwapQuote(xor, valId, BigDecimal("0.000007"), WithDesired.OUTPUT, listOf(Market.SMART), TestTokens.xorToken.copy(precision = 6), 0)!!
        assertDecimal("0.000007", quote.amount); assertNull(quote.route)
        assertEquals(listOf(0, xor, valId, "7", WithDesired.OUTPUT.backString, emptyList<String>(), "Disabled"), requests.single().params)
    }
    @Test fun `absent swap quote is not replaced with zero output`() = runTest {
        rpcReply = { null }
        assertNull(subject.getSwapQuote(xor, valId, BigDecimal.ONE, WithDesired.INPUT, emptyList(), TestTokens.xorToken, 0))
    }
    @Test fun `swap quote cancellation cannot be converted into absent quote`() = runTest {
        rpcReply = { throw CancellationException("wallet changed") }
        try { subject.getSwapQuote(xor, valId, BigDecimal.ONE, WithDesired.INPUT, emptyList(), TestTokens.xorToken, 0); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
    @Test fun `enabled source list preserves recognized order and ignores unknown runtime source`() = runTest {
        coEvery { api.fetchAvailableSources(xor, valId, 1) } returns listOf("XYKPool", "unknown", "MulticollateralBondingCurvePool")
        assertEquals(listOf(Market.XYK, Market.TBC), subject.getAvailableSources(xor, valId, 1))
    }
    @Test fun `swap availability retains false and requested dex`() = runTest {
        coEvery { api.isSwapAvailable(xor, valId, 1) } returns false
        assertFalse(subject.isSwapAvailable(xor, valId, 1))
    }
    @Test fun `pair availability uses the persisted base dex when no local pool exists`() = runTest {
        coEvery { api.isPairEnabled(xor, valId, 1) } returns false
        assertFalse(subject.isPairEnabled(xor, valId, owner).first())
    }
    @Test fun `pair availability defaults dex only when the base token is unknown`() = runTest {
        coEvery { pools.getPoolBaseToken(xor) } returns null
        coEvery { api.isPairEnabled(xor, valId, 0) } returns true
        assertTrue(subject.isPairEnabled(xor, valId, owner).first())
    }
    @Test fun `known local pool emits immediately without repeating availability RPC`() = runTest {
        every { pools.subscribePool(valId, xor) } returns flowOf(listOf(UserPoolJoinedLocalNullable(null, basic)))
        assertTrue(subject.isPairEnabled(xor, valId, owner).first())
        assertTrue(subject.isPairPresentedInNetwork(xor, valId, owner).first())
        coVerify(exactly = 0) { api.isPairEnabled(any(), any(), any()); api.getPoolReserveAccount(any(), any()) }
    }
    @Test fun `remote pair presence follows reserve account existence`() = runTest {
        coEvery { api.getPoolReserveAccount(xor, any()) } returnsMany listOf(reserve, null)
        assertTrue(subject.isPairPresentedInNetwork(xor, valId, owner).first())
        assertFalse(subject.isPairPresentedInNetwork(xor, valId, owner).first())
    }
    @Test fun `ordinary pair presence lookup failure retains unavailable fallback`() = runTest {
        coEvery { api.getPoolReserveAccount(xor, any()) } throws IllegalStateException("offline")
        assertFalse(subject.isPairPresentedInNetwork(xor, valId, owner).first())
    }
    @Test fun `cancelled pair presence lookup propagates cancellation`() = runTest {
        coEvery { api.getPoolReserveAccount(xor, any()) } throws CancellationException("screen closed")
        try { subject.isPairPresentedInNetwork(xor, valId, owner).first(); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
    @Test fun `wallet without pool keys emits an empty snapshot`() = runTest {
        coEvery { api.getUserPoolsTokenIdsKeys(owner) } returns emptyList()
        assertEquals(listOf(""), subject.subscribeToPoolsAssets(owner).toList())
        verify(exactly = 0) { calls.observeStorage(any()) }
    }
    @Test fun `pool asset subscription waits for all keys then forwards updates`() = runTest {
        coEvery { api.getUserPoolsTokenIdsKeys(owner) } returns listOf("one", "two")
        val result = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribeToPoolsAssets(owner).toList(result) }
        runCurrent(); assertEquals(listOf("initial"), result)
        storage.getValue("one").value = "changed"; runCurrent()
        assertEquals(listOf("initial", "changed"), result)
        assertEquals(setOf("one", "two"), storage.keys)
    }
    @Test fun `wallet without pools emits an empty pool data snapshot`() = runTest {
        coEvery { api.getUserPoolsTokenIds(owner) } returns emptyList()
        assertEquals(listOf(""), subject.subscribeToPoolsData(owner).toList())
    }
    @Test fun `pool without a reserve account emits an empty snapshot`() = runTest {
        coEvery { api.getUserPoolsTokenIds(owner) } returns listOf(xor to listOf(valId.fromHex()))
        coEvery { api.getPoolReserveAccount(xor, any()) } returns null
        assertEquals(listOf(""), subject.subscribeToPoolsData(owner).toList())
        verify(exactly = 0) { calls.observeStorage(any()) }
    }
    @Test fun `pool data combines reserves issuance and selected account ownership`() = runTest {
        coEvery { api.getUserPoolsTokenIds(owner) } returns listOf(xor to listOf(valId.fromHex()))
        coEvery { pools.getBasicPool(xor, valId.removePrefix("0x")) } returns basic
        val values = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribeToPoolsData(owner).toList(values) }
        runCurrent(); assertEquals(listOf("initi"), values)
        val reserveKey = snapshot.reservesKey(xor, valId.fromHex())
        val issuanceKey = stateKey(Storage.TOTAL_ISSUANCES, reserve)
        val providerKey = stateKey(Storage.POOL_PROVIDERS, reserve, ByteArray(32) { 34 })
        assertEquals(setOf(reserveKey, issuanceKey, providerKey), storage.keys)
        storage.getValue(providerKey).value = "new-owner-balance"; runCurrent()
        assertEquals(2, values.size)
        coVerify(exactly = 0) { api.getPoolReserveAccount(any(), any()) }
    }
    @Test fun `pool subscription can recover reserve identity from network when cache is absent`() = runTest {
        coEvery { api.getUserPoolsTokenIds(owner) } returns listOf(xor to listOf(valId.fromHex()))
        coEvery { api.getPoolReserveAccount(xor, any()) } returns reserve
        assertEquals("initi", subject.subscribeToPoolsData(owner).first())
        coVerify(exactly = 1) { api.getPoolReserveAccount(xor, valId.fromHex()) }
    }
    @Test fun `no configured base tokens emits an empty basic pool snapshot`() = runTest {
        coEvery { pools.getPoolBaseTokens() } returns emptyList()
        assertEquals(listOf(""), subject.subscribeToBasicPools().toList())
    }
    @Test fun `configured base token without any pools still emits a snapshot`() = runTest {
        coEvery { calls.getStateKeys(any()) } returns emptyList()
        assertEquals(listOf(""), subject.subscribeToBasicPools().toList())
    }
    @Test fun `unwhitelisted pool list cannot stall the basic pool subscription`() = runTest {
        val key = snapshot.reservesKey(xor, valId.fromHex())
        coEvery { calls.getStateKeys(any()) } returns listOf(key)
        coEvery { assets.getWhitelistOfToken(valId) } returns null
        coEvery { api.getPoolReserveAccount(xor, any()) } returns reserve
        assertEquals(listOf(""), subject.subscribeToBasicPools().toList())
        verify(exactly = 0) { calls.observeStorage(any()) }
    }
    @Test fun `basic pool subscription joins reserve and issuance notifications`() = runTest {
        val key = snapshot.reservesKey(xor, valId.fromHex())
        coEvery { calls.getStateKeys(any()) } returns listOf(key)
        coEvery { pools.getBasicPool(xor, valId) } returns basic
        assertEquals("combined", subject.subscribeToBasicPools().first())
        assertEquals(setOf(key, stateKey(Storage.TOTAL_ISSUANCES, reserve)), storage.keys)
    }
    @Test fun `xyk observation uses the exact ordered reserve storage key`() = runTest {
        assertEquals("initial", subject.observePoolXYKReserves(xor, valId).first())
        assertEquals(setOf(snapshot.reservesKey(xor, valId.fromHex())), storage.keys)
    }
    @Test fun `bonding curve observation uses the collateral reserve key`() = runTest {
        assertEquals("initial", subject.observePoolTBCReserves(valId).first())
        assertEquals(setOf(snapshot.poolTBCReserves(valId.fromHex())), storage.keys)
    }
    @Test fun `block observation debounces bursts and keeps the latest head`() = runTest {
        val values = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribeEachBlock().toList(values) }
        runCurrent(); assertTrue(values.isEmpty())
        val key = storage.keys.single()
        storage.getValue(key).value = "head1"; runCurrent(); advanceTimeBy(4999); runCurrent(); assertTrue(values.isEmpty())
        storage.getValue(key).value = "head2"; runCurrent(); advanceTimeBy(5000); runCurrent()
        assertEquals(listOf("head2"), values)
        assertEquals(snapshot.metadata.module(Pallete.SYSTEM.palletName).storage(Storage.PARENT_HASH.storageName).storageKey(), key)
    }
    @Test fun `pool refresh scales target reserves using target precision`() = runTest {
        val key = snapshot.reservesKey(xor, valId.fromHex())
        coEvery { calls.getStateKeys(any()) } returns listOf(key)
        coEvery { calls.getStorageHex(key) } returns "0x" + u128(BigInteger("1234567890123456789")) + u128(BigInteger("2345678"))
        coEvery { api.getPoolReserveAccount(xor, any()) } returns reserve
        coEvery { api.getPoolTotalIssuances(reserve) } returns BigInteger("3000000000000000000")
        val written = slot<List<BasicPoolLocal>>()
        coEvery { pools.insertBasicPools(capture(written)) } just Runs
        subject.updateBasicPools()
        val result = written.captured.single()
        assertEquals(xor, result.tokenIdBase); assertEquals(valId, result.tokenIdTarget)
        assertDecimal("1.234567890123456789", result.reserveBase); assertDecimal("2.345678", result.reserveTarget)
        assertDecimal("3", result.totalIssuance); assertEquals(reserveAddress, result.reservesAccount)
    }
    @Test fun `pool refresh never reads unwhitelisted target storage`() = runTest {
        coEvery { calls.getStateKeys(any()) } returns listOf(snapshot.reservesKey(xor, valId.fromHex()))
        coEvery { assets.getWhitelistOfToken(valId) } returns ""
        subject.updateBasicPools()
        coVerify(exactly = 0) { calls.getStorageHex(any()) }
        coVerify { pools.insertBasicPools(emptyList()) }
    }
    @Test fun `pool refresh removes only entries absent from the new snapshot`() = runTest {
        coEvery { calls.getStateKeys(any()) } returns emptyList()
        coEvery { pools.getBasicPools() } returns listOf(basic)
        subject.updateBasicPools()
        coVerify { pools.deleteBasicPools(listOf(basic)); pools.insertBasicPools(emptyList()) }
    }
    @Test fun `pool refresh failure cannot erase a previously saved pool snapshot`() = runTest {
        coEvery { calls.getStateKeys(any()) } throws IllegalStateException("node unavailable")
        try { subject.updateBasicPools(); fail("Expected failure") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { pools.deleteBasicPools(any()); pools.insertBasicPools(any()) }
    }
    @Test fun `missing target precision cannot write a wrongly scaled pool`() = runTest {
        val key = snapshot.reservesKey(xor, valId.fromHex())
        coEvery { calls.getStateKeys(any()) } returns listOf(key)
        coEvery { calls.getStorageHex(key) } returns "0x" + u128(BigInteger.TEN) + u128(BigInteger.ONE)
        coEvery { assets.getPrecisionOfToken(valId) } returns null
        coEvery { api.getPoolReserveAccount(xor, any()) } returns reserve
        coEvery { api.getPoolTotalIssuances(reserve) } returns BigInteger.TEN
        subject.updateBasicPools()
        coVerify { pools.insertBasicPools(emptyList()) }
    }
    companion object {
        private fun repoFile(path: String): String = listOf(File("../$path"), File(path)).first(File::isFile).readText()
        private val snapshot: RuntimeSnapshot by lazy {
            val reader = RuntimeMetadataReader.read(repoFile("sorasubstrate/src/test/resources/sora2_metadata_mainnet_133").trim())
            val types = repoFile("common/src/production/assets/types_scalecodec_mobile.json")
            val parsed = TypeDefinitionParser.parseNetworkVersioning(
                tree = Gson().fromJson(types, TypeDefinitionsTree::class.java),
                typePreset = TypesParserV14.parse(reader.metadata[RuntimeMetadataSchemaV14.lookup], v14Preset()).typePreset,
                currentRuntimeVersion = 133, upto14 = false)
            val registry = TypeRegistry(parsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())
            RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(reader, registry))
        }
    }
}
