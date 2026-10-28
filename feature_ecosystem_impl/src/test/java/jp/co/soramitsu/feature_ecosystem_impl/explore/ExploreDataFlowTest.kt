package jp.co.soramitsu.feature_ecosystem_impl.explore

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.*
import jp.co.soramitsu.demeter.domain.*
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_ecosystem_impl.domain.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies.AllCurrenciesViewModel
import jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.AllDemeterViewModel
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools.AllPoolsViewModel
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.FarmDetailsViewModel
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.math.BigDecimal

/** Exercises repositories-to-screen mapping with real sorting, formatting, and stream lifecycles. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreDataFlowTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val store = ViewModelStore()
    private val assets = mockk<AssetsRepository>()
    private val explorer = mockk<BlockExplorerManager>()
    private val repository = mockk<PolkaswapRepository>()
    private val pools = mockk<PoolsInteractor>()
    private val farming = mockk<DemeterFarmingInteractor>()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val dispatchers = mockk<CoroutineManager>()
    private val mapper = EcoSystemMapper(NumbersFormatter())
    private fun token(id: String) = Token(id, "Token ${id.uppercase()}", id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private fun basic(target: String = "val", reserve: String = "100") = BasicPoolData(token("xor"), token(target), BigDecimal(reserve), BigDecimal("200"), BigDecimal("1000"), "reserve", 12.0)
    private fun user() = CommonUserPoolData(basic(), UserPoolData(BigDecimal("2"), BigDecimal("4"), 2.0, BigDecimal("10"), false, 0))
    private val ids = Triple("xor", "val", "pswap")
    private fun basicFarm(reward: String = "pswap") = DemeterFarmingBasicPool(token("xor"), token("val"), token(reward), 12.5, BigDecimal("1000"), 0.1)
    private fun farm(reward: String = "2.000000000000000001") = DemeterFarmingPool(token("xor"), token("val"), token("pswap"), 12.5, BigDecimal("5"), BigDecimal(reward))
    @Before fun setup() {
        coEvery { pools.getPoolOfCurAccount("xor" to "val") } returns user()
        coEvery { pools.getBasicPool("xor" to "val") } returns basic()
        coEvery { farming.getFarmedBasicPools() } returns listOf(basicFarm())
        coEvery { farming.getFarmedPool(ids) } returns farm()
    }
    @After fun cleanup() { store.clear() }
    private fun TestScope.farmScreen() = FarmDetailsViewModel(pools, router, NumbersFormatter(), resources, farming, "xor", "val", "pswap").also {
        store.put("farm", it); runCurrent()
    }
    private fun TestScope.tokenScreen(): AllCurrenciesViewModel {
        every { dispatchers.io } returns UnconfinedTestDispatcher(testScheduler)
        return AllCurrenciesViewModel(EcoSystemTokensInteractorImpl(assets, explorer), mapper, dispatchers).also {
            store.put("tokens", it)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} }
            runCurrent()
        }
    }
    private fun TestScope.poolScreen(): AllPoolsViewModel {
        every { dispatchers.io } returns UnconfinedTestDispatcher(testScheduler)
        return AllPoolsViewModel(EcoSystemPoolsInteractorImpl(repository), dispatchers, mapper).also {
            store.put("pools", it)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.poolsState.collect {} }
            runCurrent()
        }
    }
    @Test fun `token discovery preserves assets without market data and ranks known liquidity`() = runTest {
        val tokens = listOf(token("xor"), token("val"), token("eth"))
        every { assets.subscribeTokensList() } returns flowOf(tokens)
        coEvery { assets.tokensList() } returns tokens
        coEvery { explorer.getTokensLiquidity(listOf("xor", "val", "eth")) } returns listOf("eth" to 300.0, "xor" to 100.0)
        val vm = tokenScreen()
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("eth", "xor", "val"), vm.state.value.topTokens.map { it.second.tokenId })
        assertEquals(listOf("1", "2", "3"), vm.state.value.topTokens.map { it.first })
    }
    @Test fun `late token catalog updates replace removed assets without losing unknown liquidity`() = runTest {
        val tokens = MutableStateFlow(listOf(token("xor")))
        every { assets.subscribeTokensList() } returns tokens
        coEvery { assets.tokensList() } returns tokens.value
        coEvery { explorer.getTokensLiquidity(listOf("xor")) } returns listOf("xor" to 100.0)
        val vm = tokenScreen()
        tokens.value = listOf(token("val")); runCurrent()
        assertEquals(listOf("val"), vm.state.value.topTokens.map { it.second.tokenId })
    }
    @Test fun `one shot token discovery uses the same descending missing last order`() = runTest {
        val tokens = listOf(token("xor"), token("val"), token("eth"))
        coEvery { assets.tokensList() } returns tokens
        coEvery { explorer.getTokensLiquidity(any()) } returns listOf("val" to 200.0, "xor" to 100.0)
        val result = EcoSystemTokensInteractorImpl(assets, explorer).getTokensWithTvl()
        assertEquals(listOf("val", "xor", "eth"), result.tokens.map { it.token.id })
        assertNull(result.tokens.last().liquidityFiat)
    }
    @Test fun `empty token catalog completes loading with an empty result`() = runTest {
        every { assets.subscribeTokensList() } returns flowOf(emptyList())
        coEvery { assets.tokensList() } returns emptyList()
        coEvery { explorer.getTokensLiquidity(emptyList()) } returns emptyList()
        val vm = tokenScreen()
        assertFalse(vm.state.value.loading); assertTrue(vm.state.value.topTokens.isEmpty())
    }
    @Test fun `pool sorting keeps exact TVL order below double precision`() = runTest {
        val smaller = basic("val", "100.000000000000000001")
        val larger = basic("eth", "100.000000000000000002")
        every { repository.subscribeBasicPools() } returns flowOf(listOf(smaller, larger))
        val vm = poolScreen()
        assertFalse(vm.poolsState.value.loading)
        assertEquals(listOf("xor" to "eth", "xor" to "val"), vm.poolsState.value.pools.map { it.ids })
        assertEquals(listOf("1", "2"), vm.poolsState.value.pools.map { it.number })
    }
    @Test fun `pool without price or yield remains visible with unavailable values`() = runTest {
        val unknown = basic().copy(baseToken = token("xor").copy(fiatPrice = null), sbapy = null)
        every { repository.subscribeBasicPools() } returns flowOf(listOf(unknown))
        val item = poolScreen().poolsState.value.pools.single()
        assertEquals("xor" to "val", item.ids); assertEquals("", item.text2); assertEquals("", item.text3)
    }
    @Test fun `empty pool update removes prior rows and ends loading`() = runTest {
        val flow = MutableStateFlow(listOf(basic()))
        every { repository.subscribeBasicPools() } returns flow
        val vm = poolScreen(); assertEquals(1, vm.poolsState.value.pools.size)
        flow.value = emptyList(); runCurrent()
        assertFalse(vm.poolsState.value.loading); assertTrue(vm.poolsState.value.pools.isEmpty())
    }
    @Test fun `all farms preserves distinct reward identities and sequential display numbers`() = runTest {
        coEvery { farming.getFarmedBasicPools() } returns listOf(basicFarm("pswap"), basicFarm("deo"))
        val vm = AllDemeterViewModel(farming, NumbersFormatter()); store.put("farms", vm); runCurrent()
        assertEquals(listOf("pswap", "deo"), vm.farmsLiveData.value!!.pools.map { it.ids.third })
        assertEquals(listOf("1", "2"), vm.farmsLiveData.value!!.pools.map { it.number })
        assertFalse(vm.farmsLiveData.value!!.loading)
    }
    @Test fun `all farms read failure is reported without crashing or leaving a spinner`() = runTest {
        coEvery { farming.getFarmedBasicPools() } throws IOException("offline")
        val vm = AllDemeterViewModel(farming, NumbersFormatter()); store.put("farms", vm); runCurrent()
        assertFalse(vm.farmsLiveData.value!!.loading)
        assertNotNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun `farm detail combines pool share precise rewards and optional yield`() = runTest {
        val vm = farmScreen(); val state = vm.state.value
        assertFalse(state.loading); assertEquals(ids, state.farmIds)
        assertEquals("XOR-VAL", state.poolTitle); assertEquals("PSWAP", state.rewardsTokenSymbol)
        assertEquals("50%", state.poolShareStackedText); assertEquals(50f, state.poolShareStacked)
        assertTrue(state.hasSupplyInPool); assertTrue(state.hasRewardsAvailable)
        assertEquals("12%", state.apyText)
        vm.onClaim(); verify { router.showClaimDemeter(ids, BigDecimal("2.000000000000000001")) }
    }
    @Test fun `a zero reward with a decimal scale is not claimable`() = runTest {
        coEvery { farming.getFarmedPool(ids) } returns farm("0.000000000000000000")
        assertFalse(farmScreen().state.value.hasRewardsAvailable)
    }
    @Test fun `farm without a personal pool position cannot offer staking from missing shares`() = runTest {
        coEvery { pools.getPoolOfCurAccount(any()) } returns null
        val state = farmScreen().state.value
        assertFalse(state.hasSupplyInPool); assertEquals(0f, state.poolShareStacked)
    }
    @Test fun `farm without a user reward position has no claimable reward`() = runTest {
        coEvery { farming.getFarmedPool(ids) } returns null
        val state = farmScreen().state.value
        assertFalse(state.hasRewardsAvailable); assertNull(state.poolShareStacked)
    }
    @Test fun `farm routes pool staking and claim to the complete identifier`() = runTest {
        val vm = farmScreen(); vm.onSupplyLiquidity(); vm.onSupplyStacking(); vm.onClaim()
        verify { router.showPoolDetails("xor" to "val") }
        verify { router.showEditFarm(ids) }
        verify { router.showClaimDemeter(ids, BigDecimal("2.000000000000000001")) }
    }
    @Test fun `missing farm ends loading without inventing a reward`() = runTest {
        coEvery { farming.getFarmedBasicPools() } returns listOf(basicFarm("deo"))
        val state = farmScreen().state.value
        assertFalse(state.loading); assertFalse(state.hasRewardsAvailable)
    }
    @Test fun `farm read failure ends loading and reports the failure`() = runTest {
        coEvery { pools.getBasicPool(any()) } throws IOException("offline")
        val vm = farmScreen()
        assertFalse(vm.state.value.loading); assertNotNull(vm.errorFromResourceLiveData.value)
    }
}
