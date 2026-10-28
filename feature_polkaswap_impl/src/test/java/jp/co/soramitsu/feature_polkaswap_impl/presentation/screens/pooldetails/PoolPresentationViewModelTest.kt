package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.*
import jp.co.soramitsu.demeter.domain.*
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoolsettings.FullPoolSettingsViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PoolPresentationViewModelTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val store = ViewModelStore()
    private val pools = mockk<PoolsInteractor>(relaxed = true)
    private val farms = mockk<DemeterFarmingInteractor>()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private fun token(id: String) = Token(id, id.uppercase(), id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private fun basic(target: String = "val") = BasicPoolData(token("xor"), token(target), BigDecimal("100"), BigDecimal("200"), BigDecimal("100"), "pool-address", 12.0)
    private fun user() = UserPoolData(BigDecimal("2"), BigDecimal("4"), 2.0, BigDecimal("10"), false, 0)
    private val flow = MutableStateFlow<CommonPoolData?>(CommonPoolData(basic(), user()))
    private fun farm(reward: String, amount: String) = DemeterFarmingPool(token("xor"), token("val"), token(reward), 12.0, BigDecimal(amount), BigDecimal("1"))
    @Before fun setup() {
        every { pools.subscribePoolCacheOfCurAccount("xor", "val") } returns flow
        coEvery { pools.getRewardToken() } returns token("pswap")
        coEvery { pools.getPoolsCacheOfCurAccount() } returns listOf(CommonUserPoolData(basic(), user()), CommonUserPoolData(basic("eth"), user()))
        coEvery { farms.getFarmedBasicPools() } returns emptyList()
        coEvery { farms.getFarmedPools() } returns emptyList()
    }
    @After fun cleanup() { store.clear() }
    private fun TestScope.details() = PoolDetailsViewModel(pools, NumbersFormatter(), resources, router, farms, "xor", "val").also { store.put("details", it); runCurrent() }
    private fun TestScope.settings() = FullPoolSettingsViewModel(pools, NumbersFormatter(), walletRouter).also { store.put("settings", it); runCurrent() }

    @Test fun `pool detail renders both token amounts and the user's exact share`() = runTest {
        val vm = details()
        assertEquals("XOR", vm.detailsState.symbol1); assertEquals("VAL", vm.detailsState.symbol2)
        assertEquals("PSWAP", vm.detailsState.rewardsTokenSymbol)
        assertEquals("2 XOR", vm.detailsState.pooled1); assertEquals("4 VAL", vm.detailsState.pooled2)
        assertEquals("2%", vm.detailsState.userPoolSharePercent)
        assertTrue(vm.detailsState.addEnabled); assertTrue(vm.detailsState.removeEnabled)
    }
    @Test fun `pool without a user position permits supply but not removal`() = runTest {
        flow.value = CommonPoolData(basic(), null)
        val vm = details()
        assertNull(vm.detailsState.pooled1); assertNull(vm.detailsState.pooled2)
        assertNull(vm.detailsState.userPoolSharePercent)
        assertTrue(vm.detailsState.addEnabled); assertFalse(vm.detailsState.removeEnabled)
    }
    @Test fun `unavailable pool disables both liquidity actions`() = runTest {
        flow.value = null
        val vm = details()
        assertFalse(vm.detailsState.addEnabled); assertFalse(vm.detailsState.removeEnabled)
    }
    @Test fun `pool detail routes supply withdrawal and farm by their exact identifiers`() = runTest {
        val vm = details()
        vm.onSupply(); vm.onRemove(); vm.onFarm(Triple("xor", "val", "pswap"))
        verify(exactly = 1) { router.showAddLiquidity("xor", "val") }
        verify(exactly = 1) { router.showRemoveLiquidity("xor" to "val") }
        verify(exactly = 1) { router.showFarmDetails(Triple("xor", "val", "pswap")) }
    }
    @Test fun `available farms only include the current pair and retain reward identity`() = runTest {
        val current = DemeterFarmingBasicPool(token("xor"), token("val"), token("pswap"), 12.5, BigDecimal("1000"), 0.1)
        coEvery { farms.getFarmedBasicPools() } returns listOf(current.copy(tokenTarget = token("eth")), current, current.copy(tokenReward = token("deo")))
        val vm = details()
        assertEquals(listOf(Triple("xor", "val", "pswap"), Triple("xor", "val", "deo")), vm.detailsState.availableDemeterFarms!!.map { it.ids })
        assertEquals(listOf("1", "2"), vm.detailsState.availableDemeterFarms!!.map { it.number })
    }
    @Test fun `fully staked pool disables removal and shows each user farm`() = runTest {
        coEvery { farms.getFarmedPools() } returns listOf(farm("pswap", "10"))
        val vm = details()
        assertTrue(vm.detailsState.demeter100Percent)
        assertFalse(vm.detailsState.removeEnabled)
        assertEquals("100%", vm.detailsState.demeterPools?.single()?.text4)
    }
    @Test fun `a later empty farm cannot erase an already fully staked pool`() = runTest {
        coEvery { farms.getFarmedPools() } returns listOf(farm("pswap", "10"), farm("deo", "0"))
        val vm = details()
        assertTrue(vm.detailsState.demeter100Percent)
        assertFalse(vm.detailsState.removeEnabled)
    }
    @Test fun `farm updates are recomputed when the pool balance changes`() = runTest {
        coEvery { farms.getFarmedPools() } returns listOf(farm("pswap", "5"))
        val vm = details()
        assertEquals("50%", vm.detailsState.demeterPools?.single()?.text4)
        flow.value = CommonPoolData(basic(), user().copy(poolProvidersBalance = BigDecimal("5")))
        runCurrent()
        assertEquals("100%", vm.detailsState.demeterPools?.single()?.text4)
        assertFalse(vm.detailsState.removeEnabled)
    }
    @Test fun `missing fiat and yield are displayed without fabricated values`() = runTest {
        flow.value = CommonPoolData(basic().copy(baseToken = token("xor").copy(fiatPrice = null), sbapy = null), null)
        val vm = details()
        assertEquals("", vm.detailsState.apy)
        assertFalse(vm.detailsState.tvl.contains("NaN"))
        assertFalse(vm.detailsState.tvl.contains("null"))
    }
    @Test fun `settings maps and searches pool names symbols and both identifiers`() = runTest {
        val vm = settings()
        assertEquals(listOf("xor" to "val", "xor" to "eth"), vm.settingsState.value!!.map { it.id })
        vm.onToolbarSearch("ETH"); assertEquals(listOf("xor" to "eth"), vm.settingsState.value!!.map { it.id })
        assertFalse(vm.dragList.value!!)
        vm.onToolbarSearch("no such pool"); assertTrue(vm.poolsEmptyState.value); assertEquals("", vm.fiatSum.value)
        vm.onToolbarSearch(""); assertEquals(2, vm.settingsState.value!!.size); assertFalse(vm.poolsEmptyState.value); assertTrue(vm.dragList.value!!)
    }
    @Test fun `settings empty cache stays empty and has no fiat sum`() = runTest {
        coEvery { pools.getPoolsCacheOfCurAccount() } returns emptyList()
        val vm = settings()
        assertTrue(vm.poolsEmptyState.value); assertEquals("", vm.fiatSum.value)
    }
    @Test fun `settings favorite updates the selected pool and repository`() = runTest {
        val vm = settings(); val first = vm.settingsState.value!!.first()
        vm.onFavoriteClick(first); runCurrent()
        assertTrue(vm.settingsState.value!!.first().favorite)
        coVerify(exactly = 1) { pools.poolFavoriteOn("xor" to "val") }
        vm.onFavoriteClick(vm.settingsState.value!!.first()); runCurrent()
        assertFalse(vm.settingsState.value!!.first().favorite)
        coVerify(exactly = 1) { pools.poolFavoriteOff("xor" to "val") }
    }
    @Test fun `rapid favorite callbacks use the latest state instead of a stale row snapshot`() = runTest {
        val vm = settings(); val snapshot = vm.settingsState.value!!.first()
        vm.onFavoriteClick(snapshot); vm.onFavoriteClick(snapshot); runCurrent()
        assertFalse(vm.settingsState.value!!.first().favorite)
        coVerify(exactly = 1) { pools.poolFavoriteOn(snapshot.id) }
        coVerify(exactly = 1) { pools.poolFavoriteOff(snapshot.id) }
    }
    @Test fun `unknown favorite row does not modify any current pool`() = runTest {
        val vm = settings(); val before = vm.settingsState.value!!.toList()
        vm.onFavoriteClick(before.first().copy(id = "other" to "pair")); runCurrent()
        assertEquals(before, vm.settingsState.value)
        coVerify(exactly = 0) { pools.poolFavoriteOn(any()) }
    }
    @Test fun `pool reorder persists exact token pair positions`() = runTest {
        val vm = settings()
        assertTrue(vm.assetPositionChanged(0, 1)); runCurrent()
        assertEquals(listOf("xor" to "eth", "xor" to "val"), vm.settingsState.value!!.map { it.id })
        assertEquals(0 to 1, vm.assetPositions.value)
        coVerify(exactly = 1) { pools.updatePoolPosition(mapOf(("xor" to "eth") to 0, ("xor" to "val") to 1)) }
    }
    @Test fun `outdated drag indices cannot crash or modify saved positions`() = runTest {
        val vm = settings(); val before = vm.settingsState.value!!.toList()
        for ((from, to) in listOf(-1 to 0, 0 to -1, 3 to 0, 0 to 3)) assertFalse(vm.assetPositionChanged(from, to))
        runCurrent(); assertEquals(before, vm.settingsState.value)
        coVerify(exactly = 0) { pools.updatePoolPosition(any()) }
    }
    @Test fun `a filtered pool list cannot reorder unrelated hidden pools`() = runTest {
        val vm = settings(); vm.onToolbarSearch("ETH")
        assertFalse(vm.assetPositionChanged(0, 1)); runCurrent()
        coVerify(exactly = 0) { pools.updatePoolPosition(any()) }
        vm.onToolbarSearch("")
        assertEquals(listOf("xor" to "val", "xor" to "eth"), vm.settingsState.value!!.map { it.id })
    }
    @Test fun `settings done and close route back without mutating pool order`() = runTest {
        val vm = settings(); vm.onAction(); vm.onNavIcon()
        verify(exactly = 2) { walletRouter.popBackStackFragment() }
        coVerify(exactly = 0) { pools.updatePoolPosition(any()) }
    }
}
