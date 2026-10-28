package jp.co.soramitsu.feature_ecosystem_impl.explore

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.demeter.domain.*
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.domain.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.explore.*
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

/** Search preserves catalog rank and exact identities while formatting real pool/farm rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreSearchContractTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val store = ViewModelStore()
    private val assets = mockk<AssetsRouter>(relaxed = true)
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val pools = mockk<PoolsInteractor>()
    private val farms = mockk<DemeterFarmingInteractor>()
    private val tokens = mockk<EcoSystemTokensInteractor>()
    private val formatter = NumbersFormatter()
    private fun token(id: String) = Token(id, "Token ${id.uppercase()}", id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private fun pool(target: String, reserve: String) = BasicPoolData(token("xor"), token(target), BigDecimal(reserve), BigDecimal("200"), BigDecimal("1000"), "reserve", 12.5)
    private fun farm(target: String, reward: String) = DemeterFarmingBasicPool(token("xor"), token(target), token(reward), 12.5, BigDecimal("1000"), 0.1)
    @Before fun setup() {
        coEvery { tokens.getTokensWithTvl() } returns EcoSystemTokens(listOf(EcoSystemToken(token("eth"), 300.0), EcoSystemToken(token("val"), 200.0)))
        coEvery { pools.getBasicPools() } returns listOf(pool("val", "100"), pool("eth", "300"))
        coEvery { farms.getFarmedBasicPools() } returns listOf(farm("eth", "deo"), farm("val", "pswap"))
    }
    @After fun cleanup() { store.clear() }
    private fun screen(): ExploreViewModel = ExploreViewModel(mockk<ResourceManager>(relaxed = true), farms, pools, tokens, EcoSystemMapper(formatter), router, assets, formatter).also { store.put("explore", it) }

    @Test fun `matching rows retain ranks in their complete catalogs`() = runTest {
        val vm = screen(); vm.onToolbarSearch("vAl"); advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state.isSearching); assertFalse(state.isLoading)
        assertEquals("vAl", vm.toolbarState.value!!.basic.searchValue)
        assertEquals("2", state.ecoSystemTokensState!!.topTokens.single().first)
        assertEquals("val", state.ecoSystemTokensState!!.topTokens.single().second.tokenId)
        val pool = state.pools.single()
        assertEquals("2", pool.number); assertEquals("xor" to "val", pool.ids)
        assertEquals("XOR-VAL", pool.text1); assertTrue(pool.text2.contains("200")); assertEquals("12.5%", pool.text3)
        val farm = state.farms.single()
        assertEquals("2", farm.number); assertEquals(Triple("xor", "val", "pswap"), farm.ids)
        assertEquals("PSWAP", farm.rewardTokenSymbol); assertEquals("XOR-VAL", farm.text1); assertEquals("12.5%", farm.text3)
    }
    @Test fun `unknown pool price and yield remain unavailable in matching results`() = runTest {
        coEvery { pools.getBasicPools() } returns listOf(pool("val", "100").copy(baseToken = token("xor").copy(fiatPrice = null), sbapy = null))
        val vm = screen(); vm.onToolbarSearch("val"); advanceUntilIdle()
        val row = vm.state.value.pools.single()
        assertEquals("", row.text2); assertEquals("", row.text3); assertEquals("1", row.number)
    }
    @Test fun `reward symbol search finds the farm without inventing token or pool matches`() = runTest {
        val vm = screen(); vm.onToolbarSearch("PSWAP"); advanceUntilIdle()
        assertTrue(vm.state.value.pools.isEmpty()); assertTrue(vm.state.value.ecoSystemTokensState!!.topTokens.isEmpty())
        assertEquals("pswap", vm.state.value.farms.single().ids.third)
    }
    @Test fun `clearing search restores discovery state without another repository read`() = runTest {
        val vm = screen(); vm.onToolbarSearch("val"); advanceUntilIdle(); vm.onToolbarSearch(""); advanceUntilIdle()
        assertEquals(ExploreScreenState(), vm.state.value)
        coVerify(exactly = 1) { tokens.getTokensWithTvl(); pools.getBasicPools() }
    }
    @Test fun `catalog actions route the complete selected identities`() = runTest {
        val vm = screen(); vm.onTokenClicked("val"); vm.onPoolClicked("xor" to "val"); vm.onFarmClicked(Triple("xor", "val", "pswap")); vm.onPoolPlus()
        verify(exactly = 1) { assets.showAssetDetails("val"); router.showPoolDetails("xor" to "val"); router.showFarmDetails(Triple("xor", "val", "pswap")); router.showAddLiquidity(SubstrateOptionsProvider.feeAssetId) }
    }
}
