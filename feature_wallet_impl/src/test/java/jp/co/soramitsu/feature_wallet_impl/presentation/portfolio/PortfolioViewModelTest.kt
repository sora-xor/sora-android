package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import io.mockk.coVerify
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
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val refreshes = mutableListOf<List<String>>()
        var selectionFailure: Exception? = null
        var refreshFailure: Exception? = null
        var selectionGate: CompletableDeferred<Unit>? = null
        var publishSelection = true
        override val lastReads: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun observe(): Flow<PortfolioSnapshot> = snapshots
        override suspend fun refresh(walletIds: Collection<String>) {
            refreshes += walletIds.toList()
            refreshFailure?.let { throw it }
        }
        override suspend fun select(walletId: String) {
            selected += walletId
            selectionGate?.await()
            selectionFailure?.let { throw it }
            if (!publishSelection) return
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

    @Test
    fun `invalid move amounts never switch wallets or open send`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        for (amount in listOf("-1", "0", "40.000000000000000001")) {
            vm.reviewMove(savingsXor.key, destinationWalletId = MAIN, amount = BigDecimal(amount))
            advanceUntilIdle()
        }
        assertTrue(fake.selected.isEmpty())
        assertTrue(effects.isEmpty())
        verify(exactly = 0) { walletRouter.showValTransferAmount(any(), any(), any()) }
    }

    @Test
    fun `move accepts exact transferable balance without losing precision`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.reviewMove(mainXor.key, destinationWalletId = SAVINGS, amount = mainXor.transferable)
        advanceUntilIdle()
        verify(exactly = 1) { walletRouter.showValTransferAmount(SAVINGS, AssetHolder.XOR_ASSET_ID, "90") }
    }

    @Test
    fun `blank move amount defers entry to the send screen`() = runTest {
        val vm = viewModel(source())
        vm.reviewMove(mainXor.key, destinationWalletId = SAVINGS, amount = null)
        advanceUntilIdle()
        verify(exactly = 1) { walletRouter.showValTransferAmount(SAVINGS, AssetHolder.XOR_ASSET_ID, null) }
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

    @Test fun `wallet and lens selections close sheets clear highlights and persist preferences`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.openSheet(PortfolioSheet.Wallets)
        vm.selectWallet(SAVINGS)
        advanceUntilIdle()
        assertEquals(PortfolioScope.SELECTED, vm.model.value?.scope)
        assertEquals(SAVINGS, vm.model.value?.selectedWallet?.id)
        assertNull(vm.sheet.value)
        coVerify { preferences.setScope(PortfolioScope.SELECTED) }
        vm.selectLens(PortfolioLens.TOKENS)
        advanceUntilIdle()
        coVerify { preferences.setLens(PortfolioLens.TOKENS) }
        vm.selectAllWallets()
        advanceUntilIdle()
        assertEquals(PortfolioScope.ALL, vm.model.value?.scope)
        coVerify { preferences.setScope(PortfolioScope.ALL) }
    }

    @Test fun `legend highlight opens token distribution only on a repeated token tap`() = runTest {
        val vm = viewModel(source())
        val effects = effectsOf(vm)
        vm.selectLens(PortfolioLens.TOKENS)
        advanceUntilIdle()
        vm.onLegendTap(xor.id)
        advanceUntilIdle()
        assertEquals(xor.id, vm.model.value?.highlighted)
        assertTrue(effects.isEmpty())
        vm.onLegendTap(xor.id)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.OpenWhereItLives(xor.id)), effects)
        assertNull(vm.model.value?.highlighted)
        vm.onLegendTap(xor.id)
        advanceUntilIdle()
        vm.onLegendTap(null)
        advanceUntilIdle()
        assertNull(vm.model.value?.highlighted)
        vm.selectLens(PortfolioLens.WALLETS)
        advanceUntilIdle()
        vm.onLegendTap(MAIN)
        advanceUntilIdle()
        vm.onLegendTap(MAIN)
        advanceUntilIdle()
        assertEquals(1, effects.size)
        assertNull(vm.model.value?.highlighted)
    }

    @Test fun `holding row expansion toggles independently of wallet scope`() = runTest {
        val vm = viewModel(source())
        vm.toggleRow(mainXor.key)
        advanceUntilIdle()
        assertEquals(setOf(mainXor.key), vm.model.value?.expanded)
        vm.toggleRow(mainXor.key)
        advanceUntilIdle()
        assertTrue(vm.model.value!!.expanded.isEmpty())
    }

    @Test fun `quick send and receive ask for a wallet in all wallets scope`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.onQuickSend()
        assertEquals(PortfolioSheet.PickWallet(WalletAction.SEND), vm.sheet.value)
        vm.onPickWallet(WalletAction.SEND, SAVINGS)
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        assertNull(vm.sheet.value)
        verify { assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID) }
        vm.onQuickReceive()
        assertEquals(PortfolioSheet.PickWallet(WalletAction.RECEIVE), vm.sheet.value)
        vm.onPickWallet(WalletAction.RECEIVE, SAVINGS)
        advanceUntilIdle()
        verify { walletRouter.openQrCodeFlow() }
    }

    @Test fun `selected wallet quick actions open contacts and receive directly`() = runTest {
        val vm = viewModel(source())
        vm.selectWallet(MAIN)
        advanceUntilIdle()
        vm.onQuickSend()
        vm.onQuickReceive()
        assertNull(vm.sheet.value)
        verify { assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID) }
        verify { walletRouter.openQrCodeFlow() }
    }

    @Test fun `quick move and swap choose an eligible holding from the selected wallet`() = runTest {
        val vm = viewModel(source())
        vm.onQuickMove()
        assertEquals(PortfolioSheet.Move(mainXor.key), vm.sheet.value)
        vm.onQuickSwap()
        assertEquals(PortfolioSheet.Swap(mainXor.key), vm.sheet.value)
    }

    @Test fun `polkaswap promotion opens the holding sheet or preselects the destination token`() = runTest {
        val vm = viewModel(source())
        vm.openPolkaswapFor(xor.id, mainXor.key)
        assertEquals(PortfolioSheet.Swap(mainXor.key), vm.sheet.value)
        vm.openPolkaswapFor(PortfolioSlots.VAL_ID, null)
        verify { polkaswapRouter.showSwap(tokenToId = PortfolioSlots.VAL_ID) }
    }

    @Test fun `wallet and asset management dismiss the sheet before routing`() = runTest {
        val vm = viewModel(source())
        vm.openSheet(PortfolioSheet.HowWeCount)
        vm.manageWallets()
        assertNull(vm.sheet.value)
        verify { mainRouter.showAccountList() }
        vm.openSheet(PortfolioSheet.Sync)
        vm.manageAssets()
        assertNull(vm.sheet.value)
        verify { assetsRouter.showFullAssetsSettings() }
    }

    @Test fun `send uses contacts for sora2 and the matching nexus account for nexus holdings`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        vm.send(savingsXor.key)
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        verify { assetsRouter.showContacts(xor.id) }
        vm.send(mainMinamoto.key)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.OpenNexusSend(MAIN, WalletNetworkId.MINAMOTO, mainMinamoto.address, null, null)), effects)
    }

    @Test fun `sample nexus send displays an explanation without switching wallets`() = runTest {
        val fake = source(sample = true)
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        vm.send(mainMinamoto.key)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.Message(R.string.portfolio_sample_handoff_off)), effects)
        assertTrue(fake.selected.isEmpty())
        verify(exactly = 0) { assetsRouter.showContacts(any()) }
    }

    @Test fun `asset detail routing accepts only existing sora2 holdings`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.openAssetDetails("missing")
        vm.openAssetDetails(mainMinamoto.key)
        advanceUntilIdle()
        verify(exactly = 0) { assetsRouter.showAssetDetails(any()) }
        vm.openAssetDetails(savingsXor.key)
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        verify(exactly = 1) { assetsRouter.showAssetDetails(xor.id) }
    }

    @Test fun `receive selects the requested wallet and dismisses the sheet`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.openSheet(PortfolioSheet.Wallets)
        vm.receiveOnSora2(SAVINGS)
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        assertNull(vm.sheet.value)
        verify { walletRouter.openQrCodeFlow() }
    }

    @Test fun `failed wallet switch reports failure and never navigates`() = runTest {
        val fake = source().apply { selectionFailure = IllegalStateException("locked") }
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        vm.receiveOnSora2(SAVINGS)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.Message(R.string.portfolio_switch_failed)), effects)
        assertFalse(vm.busy.value)
        verify(exactly = 0) { walletRouter.openQrCodeFlow() }
    }

    @Test fun `a stalled wallet switch times out without routing and clears busy`() = runTest {
        val fake = source().apply { publishSelection = false }
        val vm = viewModel(fake)
        vm.receiveOnSora2(SAVINGS)
        runCurrent()
        assertTrue(vm.busy.value)
        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(vm.busy.value)
        verify(exactly = 0) { walletRouter.openQrCodeFlow() }
    }

    @Test fun `concurrent wallet actions cannot start a second account switch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = source().apply { selectionGate = gate }
        val vm = viewModel(fake)
        vm.receiveOnSora2(SAVINGS)
        runCurrent()
        vm.selectWallet(SAVINGS)
        runCurrent()
        assertEquals(listOf(SAVINGS), fake.selected)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.busy.value)
        verify(exactly = 1) { walletRouter.openQrCodeFlow() }
    }

    @Test fun `forced refresh includes all wallets or only the selected wallet by scope`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(listOf(MAIN, SAVINGS), fake.refreshes.last())
        vm.selectWallet(MAIN)
        advanceUntilIdle()
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(listOf(MAIN), fake.refreshes.last())
        vm.openSheet(PortfolioSheet.Wallets)
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(listOf(MAIN, SAVINGS), fake.refreshes.last())
    }

    @Test fun `refresh failures retain the last portfolio and do not surface navigation errors`() = runTest {
        val fake = source().apply { refreshFailure = IllegalStateException("offline") }
        val vm = viewModel(fake)
        val previous = vm.model.value?.snapshot
        val effects = effectsOf(vm)
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(previous, vm.model.value?.snapshot)
        assertTrue(effects.isEmpty())
    }

}
