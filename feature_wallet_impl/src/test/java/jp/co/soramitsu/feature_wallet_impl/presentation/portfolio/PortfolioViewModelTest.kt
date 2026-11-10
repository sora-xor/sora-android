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
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.LivePortfolioSource
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioDebugHooks
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioPreferences
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioSource
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.SavedPicture
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.MAIN
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.SAVINGS
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.holding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.xor
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletSync
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
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
    fun `the saved picture is there before anything has been collected`() = runTest {
        val saved = PortfolioFixtures.snapshot(listOf(mainXor, savingsXor), wallets = listOf(MAIN, SAVINGS)).copy(saved = true)
        val live = mockk<LivePortfolioSource>(relaxed = true) {
            every { savedPicture() } returns SavedPicture(saved, PortfolioScope.SELECTED)
            every { observe() } returns flow<PortfolioSnapshot> { awaitCancellation() }
        }
        coEvery { preferences.scope() } returns PortfolioScope.SELECTED
        val vm = PortfolioViewModel(
            liveSource = live,
            preferences = preferences,
            debugHooks = Optional.empty(),
            walletRouter = walletRouter,
            assetsRouter = assetsRouter,
            polkaswapRouter = polkaswapRouter,
            mainRouter = mainRouter,
            swapInteractor = swapInteractor,
        )
        // No collector, no dispatcher run: this is what the very first frame draws.
        val first = vm.model.value!!
        assertEquals(PortfolioScope.SELECTED, first.scope)
        assertEquals(listOf(MAIN), first.walletIds)
        assertTrue(first.totals.known)
        // Nothing read yet on this run: the saved picture keeps showing while it stays that way.
        backgroundScope.launch { vm.model.collect {} }
        advanceUntilIdle()
        assertEquals(first.totals, vm.model.value?.totals)
    }

    @Test
    fun `acting on a saved picture waits for what the app says is selected`() = runTest {
        val saved = PortfolioFixtures.snapshot(listOf(mainXor, savingsXor), wallets = listOf(MAIN, SAVINGS)).copy(saved = true)
        val live = mockk<LivePortfolioSource>(relaxed = true) {
            every { savedPicture() } returns SavedPicture(saved, PortfolioScope.ALL)
            every { observe() } returns flow<PortfolioSnapshot> { awaitCancellation() }
        }
        val vm = PortfolioViewModel(
            liveSource = live,
            preferences = preferences,
            debugHooks = Optional.empty(),
            walletRouter = walletRouter,
            assetsRouter = assetsRouter,
            polkaswapRouter = polkaswapRouter,
            mainRouter = mainRouter,
            swapInteractor = swapInteractor,
        )
        backgroundScope.launch { vm.model.collect {} }
        advanceUntilIdle()
        vm.openAssetDetails(mainXor.key)
        advanceUntilIdle()
        // Nothing live answered within the wait: nothing is switched or opened.
        coVerify(exactly = 0) { live.select(any()) }
        verify(exactly = 0) { assetsRouter.showAssetDetails(any()) }
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

    @Test fun `wallet selections close sheets and persist the scope`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.openSheet(PortfolioSheet.Wallets)
        vm.selectWallet(SAVINGS)
        advanceUntilIdle()
        assertEquals(PortfolioScope.SELECTED, vm.model.value?.scope)
        assertEquals(SAVINGS, vm.model.value?.selectedWallet?.id)
        assertNull(vm.sheet.value)
        coVerify { preferences.setScope(PortfolioScope.SELECTED) }
        vm.selectAllWallets()
        advanceUntilIdle()
        assertEquals(PortfolioScope.ALL, vm.model.value?.scope)
        coVerify { preferences.setScope(PortfolioScope.ALL) }
    }

    @Test fun `the first legend tap picks a token, a second one opens where it lives`() = runTest {
        val vm = viewModel(source())
        val effects = effectsOf(vm)
        vm.onLegendTap(xor.id)
        advanceUntilIdle()
        assertEquals(xor.id, vm.model.value?.highlighted)
        assertTrue(effects.isEmpty())
        vm.onLegendTap(xor.id)
        advanceUntilIdle()
        assertEquals(listOf(PortfolioEffect.OpenWhereItLives(xor.id)), effects)
        assertNull(vm.model.value?.highlighted)
    }

    @Test fun `Other in the legend picks nothing and opens nothing, however often it is tapped`() = runTest {
        val vm = viewModel(source())
        val effects = effectsOf(vm)
        vm.onLegendTap(xor.id)
        repeat(2) {
            vm.onLegendTap(OTHER_KEY)
            advanceUntilIdle()
        }
        assertNull(vm.model.value?.highlighted)
        assertTrue(effects.isEmpty())
    }

    @Test fun `a list row opens and closes on its own`() = runTest {
        val vm = viewModel(source())
        vm.toggleRow(xor.id)
        advanceUntilIdle()
        assertEquals(setOf(xor.id), vm.model.value?.expanded)
        vm.toggleRow(xor.id)
        advanceUntilIdle()
        assertTrue(vm.model.value!!.expanded.isEmpty())
    }

    @Test fun `with no saved choice a second wallet opens All wallets`() = runTest {
        val fake = FakeSource(false, PortfolioFixtures.snapshot(listOf(mainXor), wallets = listOf(MAIN)))
        val vm = viewModel(fake)
        assertEquals(PortfolioScope.SELECTED, vm.model.value?.scope)
        fake.snapshots.value = PortfolioFixtures.snapshot(listOf(mainXor, savingsXor), wallets = listOf(MAIN, SAVINGS))
        advanceUntilIdle()
        assertEquals(PortfolioScope.ALL, vm.model.value?.scope)
    }

    @Test fun `tapping the only wallet is no choice, so a second wallet still opens All wallets`() = runTest {
        val fake = FakeSource(false, PortfolioFixtures.snapshot(listOf(mainXor), wallets = listOf(MAIN)))
        val vm = viewModel(fake)
        vm.openSheet(PortfolioSheet.Wallets)
        vm.selectWallet(MAIN)
        advanceUntilIdle()
        assertNull(vm.sheet.value)
        coVerify(exactly = 0) { preferences.setScope(any()) }
        fake.snapshots.value = PortfolioFixtures.snapshot(listOf(mainXor, savingsXor), wallets = listOf(MAIN, SAVINGS))
        advanceUntilIdle()
        assertEquals(PortfolioScope.ALL, vm.model.value?.scope)
    }

    @Test fun `a saved choice of one wallet stays when another wallet appears`() = runTest {
        coEvery { preferences.scope() } returns PortfolioScope.SELECTED
        val fake = FakeSource(false, PortfolioFixtures.snapshot(listOf(mainXor), wallets = listOf(MAIN)))
        val vm = viewModel(fake)
        fake.snapshots.value = PortfolioFixtures.snapshot(listOf(mainXor, savingsXor), wallets = listOf(MAIN, SAVINGS))
        advanceUntilIdle()
        assertEquals(PortfolioScope.SELECTED, vm.model.value?.scope)
    }

    private val noPrice = PortfolioFixtures.token(xor.id, "XOR", null)

    private fun TestScope.noTotalOf(holdings: List<PortfolioHolding>, vararg sync: Pair<String, WalletSync>, connected: Boolean = true): NoTotal? {
        val snapshot = PortfolioFixtures.snapshot(holdings, wallets = listOf(MAIN, SAVINGS), sync = mapOf(*sync), connected = connected)
        return viewModel(FakeSource(false, snapshot)).model.value!!.noTotal
    }

    private val readOk = WalletSync(1L, live = true)
    private val neverRead = WalletSync(null, unread = true)
    private val readFailed = WalletSync(null, unreachable = setOf(WalletNetworkId.SORA2), unread = true)

    @Test fun `a total valued with old prices says how old they are`() = runTest {
        val old = System.currentTimeMillis() - PortfolioFreshness.PRICES_OLD_AFTER_MS - 60_000L
        fun asOf(holdings: List<PortfolioHolding>, readAt: Long?): Long? {
            val snapshot = PortfolioFixtures.snapshot(holdings, wallets = listOf(MAIN, SAVINGS), sync = mapOf(MAIN to readOk, SAVINGS to readOk))
                .copy(pricesReadAtMillis = readAt)
            return viewModel(FakeSource(false, snapshot)).model.value!!.pricesAsOf
        }
        assertEquals(old, asOf(listOf(mainXor), old))
        assertNull(asOf(listOf(mainXor), System.currentTimeMillis()))
        assertNull(asOf(listOf(mainXor), null))
        // No total, so nothing is valued with them.
        assertNull(asOf(listOf(holding(MAIN, noPrice, "5")), old))
    }

    @Test fun `a total of priced holdings has nothing to explain`() = runTest {
        assertNull(noTotalOf(listOf(mainXor), MAIN to readOk, SAVINGS to readOk))
        assertNull(noTotalOf(listOf(mainXor), MAIN to readOk, SAVINGS to neverRead))
    }

    @Test fun `holdings with no price give Price unavailable`() = runTest {
        assertEquals(NoTotal.PRICE, noTotalOf(listOf(holding(MAIN, noPrice, "5")), MAIN to readOk, SAVINGS to readOk))
    }

    @Test fun `a wallet that could not be read gives Balance unavailable, even next to unpriced holdings`() = runTest {
        assertEquals(NoTotal.FAILED, noTotalOf(emptyList(), MAIN to readFailed, SAVINGS to readFailed))
        assertEquals(NoTotal.FAILED, noTotalOf(listOf(holding(MAIN, noPrice, "5")), MAIN to readOk, SAVINGS to readFailed))
    }

    @Test fun `a network that failed for a wallet with nothing known gives Balance unavailable`() = runTest {
        val taira = WalletSync(1L, unreachable = setOf(WalletNetworkId.TAIRA))
        assertEquals(NoTotal.FAILED, noTotalOf(emptyList(), MAIN to taira, SAVINGS to readOk))
    }

    @Test fun `offline with a wallet never read gives Balance unavailable, not reading`() = runTest {
        assertEquals(NoTotal.FAILED, noTotalOf(emptyList(), MAIN to neverRead, SAVINGS to readOk, connected = false))
    }

    @Test fun `while the first read runs the list says so`() = runTest {
        assertEquals(NoTotal.READING, noTotalOf(emptyList(), MAIN to neverRead, SAVINGS to neverRead))
    }

    @Test fun `only testnet holdings have no monetary value`() = runTest {
        val taira = holding(MAIN, xor, "5", network = WalletNetworkId.TAIRA)
        assertEquals(NoTotal.TESTNET, noTotalOf(listOf(taira), MAIN to readOk, SAVINGS to readOk))
    }

    @Test fun `everything hidden, or nothing held, needs no reason`() = runTest {
        assertNull(noTotalOf(listOf(holding(MAIN, xor, "5", hidden = true)), MAIN to readOk, SAVINGS to readOk))
        assertNull(noTotalOf(emptyList(), MAIN to readOk, SAVINGS to readOk))
    }

    @Test fun `the stored chart colours are never overwritten while they load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val stored = mapOf("all|tokens" to mapOf(xor.id to 3))
        coEvery { preferences.slotMaps() } coAnswers { gate.await(); stored }
        val vm = viewModel(source())
        assertTrue(vm.model.value != null)
        coVerify(exactly = 0) { preferences.setSlotMaps(any()) }
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(3, vm.model.value?.slots?.get(xor.id))
    }

    @Test fun `the first picture never waits for the stored chart colours`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { preferences.slotMaps() } coAnswers { gate.await(); emptyMap() }
        val vm = viewModel(source())
        assertTrue(vm.model.value != null)
        gate.complete(Unit)
    }

    @Test fun `the stored scope is read before the first picture, so it never flashes All wallets`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { preferences.scope() } coAnswers { gate.await(); PortfolioScope.SELECTED }
        val vm = viewModel(source())
        assertNull(vm.model.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PortfolioScope.SELECTED, vm.model.value?.scope)
    }

    @Test fun `a wallet that was read and holds nothing is empty, an unread one is not`() = runTest {
        val read = FakeSource(
            false,
            PortfolioFixtures.snapshot(emptyList(), wallets = listOf(MAIN), sync = mapOf(MAIN to WalletSync(1L, live = true))),
        )
        assertTrue(viewModel(read).model.value!!.empty)
        val unread = FakeSource(
            false,
            PortfolioFixtures.snapshot(emptyList(), wallets = listOf(MAIN), sync = mapOf(MAIN to WalletSync(null, unread = true))),
        )
        assertFalse(viewModel(unread).model.value!!.empty)
        val failed = FakeSource(
            false,
            PortfolioFixtures.snapshot(
                emptyList(),
                wallets = listOf(MAIN),
                sync = mapOf(MAIN to WalletSync(1L, unreachable = setOf(WalletNetworkId.TAIRA))),
            ),
        )
        assertFalse(viewModel(failed).model.value!!.empty)
    }

    @Test fun `opening the home reads the wallets at once, and only while it is visible`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        assertTrue(fake.refreshes.isEmpty())
        vm.setVisible(true)
        runCurrent()
        assertEquals(listOf(MAIN, SAVINGS), fake.refreshes.first())
        vm.setVisible(false)
        runCurrent()
        val count = fake.refreshes.size
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(count, fake.refreshes.size)
    }

    @Test fun `quick send and receive ask for a wallet in all wallets scope`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        vm.onQuickSend()
        assertEquals(PortfolioSheet.PickWallet(WalletAction.SEND), vm.sheet.value)
        val effects = effectsOf(vm)
        vm.onPickWallet(WalletAction.SEND, SAVINGS)
        advanceUntilIdle()
        assertEquals(listOf(SAVINGS), fake.selected)
        assertNull(vm.sheet.value)
        // The host asks which network, if this wallet has a choice, then SORA2's own Send.
        assertEquals(listOf(PortfolioEffect.ContinueAction(WalletAction.SEND, SAVINGS)), effects)
        vm.openSora2Action(WalletAction.SEND)
        verify { assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID) }
        vm.onQuickReceive()
        assertEquals(PortfolioSheet.PickWallet(WalletAction.RECEIVE), vm.sheet.value)
        vm.onPickWallet(WalletAction.RECEIVE, SAVINGS)
        advanceUntilIdle()
        assertEquals(PortfolioEffect.ContinueAction(WalletAction.RECEIVE, SAVINGS), effects.last())
        vm.openSora2Action(WalletAction.RECEIVE)
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

    @Test fun `a holding's full page is SORA2's asset page or that network's XOR page`() = runTest {
        val fake = source()
        val vm = viewModel(fake)
        val effects = effectsOf(vm)
        vm.openAssetDetails("missing")
        vm.openAssetDetails(mainMinamoto.key)
        advanceUntilIdle()
        verify(exactly = 0) { assetsRouter.showAssetDetails(any()) }
        assertEquals(
            listOf(PortfolioEffect.OpenNexusPage(MAIN, WalletNetworkId.MINAMOTO, mainMinamoto.address)),
            effects,
        )
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
