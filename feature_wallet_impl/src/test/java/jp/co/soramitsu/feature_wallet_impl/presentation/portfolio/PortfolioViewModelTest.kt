package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.util.Optional
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioDebugHooks
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioPreferences
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioSource
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.MAIN
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.SAVINGS
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.holding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.xor
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The dashboard only picks parameters and hands off; these pin what it hands to which router. */
@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val assetsRouter = mockk<AssetsRouter>(relaxed = true)
    private val polkaswapRouter = mockk<PolkaswapRouter>(relaxed = true)
    private val mainRouter = mockk<MainRouter>(relaxed = true)
    private val swapInteractor = mockk<SwapInteractor> {
        every { getPolkaswapDisclaimerVisibility() } returns flowOf(false)
    }
    private val preferences = mockk<PortfolioPreferences>(relaxed = true) {
        coEvery { scope() } returns null
        coEvery { lens() } returns null
        coEvery { slotMaps() } returns emptyMap()
    }

    private val mainXor = holding(MAIN, xor, "100", transferable = "90")
    private val savingsXor = holding(SAVINGS, xor, "40")
    private val mainMinamoto = holding(MAIN, xor, "7", network = WalletNetworkId.MINAMOTO)

    private class FakeSource(override val isSample: Boolean, initial: PortfolioSnapshot) : PortfolioSource {
        val snapshots = MutableStateFlow(initial)
        val selected = mutableListOf<String>()
        override val lastReads: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun observe(): Flow<PortfolioSnapshot> = snapshots
        override suspend fun refresh(walletIds: Collection<String>) = Unit
        override suspend fun select(walletId: String) {
            selected += walletId
            snapshots.update { s -> s.copy(wallets = s.wallets.map { it.copy(selected = it.id == walletId) }) }
        }
    }

    private fun source(sample: Boolean = false) = FakeSource(
        sample,
        PortfolioFixtures.snapshot(
            listOf(mainXor, savingsXor, mainMinamoto),
            wallets = listOf(MAIN, SAVINGS),
        ),
    )

    private fun TestScope.viewModel(source: PortfolioSource): PortfolioViewModel {
        val hooks = object : PortfolioDebugHooks {
            override val sampleSource: StateFlow<PortfolioSource?> = MutableStateFlow(source)
            override val pinnedTilt: StateFlow<PinnedTilt?> = MutableStateFlow(null)
        }
        val vm = PortfolioViewModel(
            liveSource = mockk(relaxed = true),
            preferences = preferences,
            debugHooks = Optional.of(hooks),
            walletRouter = walletRouter,
            assetsRouter = assetsRouter,
            polkaswapRouter = polkaswapRouter,
            mainRouter = mainRouter,
            swapInteractor = swapInteractor,
        )
        backgroundScope.launch { vm.model.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `two wallets default to All wallets`() = runTest {
        val vm = viewModel(source())
        assertEquals(PortfolioScope.ALL, vm.model.value?.scope)
    }

    @Test
    fun `swap hands the pair to Polkaswap and nothing else`() = runTest {
        val vm = viewModel(source())
        vm.openSwap(mainXor.key, PortfolioSlots.VAL_ID)
        advanceUntilIdle()
        verify(exactly = 1) {
            polkaswapRouter.showSwap(tokenFromId = AssetHolder.XOR_ASSET_ID, tokenToId = PortfolioSlots.VAL_ID)
        }
        confirmVerified(polkaswapRouter, walletRouter)
    }

    @Test
    fun `acting on another wallet's holding switches to it first`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.reviewMove(savingsXor.key, destinationWalletId = MAIN, amount = BigDecimal("12.50"))
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        verify(exactly = 1) { walletRouter.showValTransferAmount(MAIN, AssetHolder.XOR_ASSET_ID, "12.5") }
    }

    /** Collects effects as they are sent (an unconfined collector, so nothing waits on the scheduler). */
    private fun TestScope.effectsOf(vm: PortfolioViewModel): List<PortfolioEffect> {
        val effects = mutableListOf<PortfolioEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    @Test
    fun `move on a Nexus network goes to the app's Nexus send`() = runTest {
        val vm = viewModel(source())
        val effects = effectsOf(vm)
        vm.reviewMove(mainMinamoto.key, destinationWalletId = SAVINGS, amount = null)
        advanceUntilIdle()
        val expected = PortfolioEffect.OpenNexusSend(
            walletId = MAIN,
            network = WalletNetworkId.MINAMOTO,
            address = mainMinamoto.address,
            recipient = "$SAVINGS-minamoto",
            amount = null,
        )
        assertEquals(listOf(expected), effects)
        verify(exactly = 0) { walletRouter.showValTransferAmount(any(), any(), any()) }
    }

    @Test
    fun `the sample never hands its invented addresses to Send`() = runTest {
        val fake = source(sample = true)
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        vm.reviewMove(mainXor.key, destinationWalletId = SAVINGS, amount = null)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.Message(R.string.portfolio_sample_handoff_off)), effects)
        assertTrue(fake.selected.isEmpty())
        verify(exactly = 0) { walletRouter.showValTransferAmount(any(), any(), any()) }
    }

    @Test
    fun `a holding that cannot swap opens nothing`() = runTest {
        val vm = viewModel(source())
        vm.openSwap(mainMinamoto.key, PortfolioSlots.VAL_ID)
        advanceUntilIdle()
        verify(exactly = 0) { polkaswapRouter.showSwap(any(), any()) }
    }
}
