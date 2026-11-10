package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.TestTokens
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LivePortfolioSourceTest {

    private val userRepository = mockk<UserRepository>()
    private val assetsRepository = mockk<AssetsRepository>()
    private val nexus = mockk<NexusPortfolioRepository>()
    private val connection = mockk<ConnectionManager>()
    private val store = mockk<PortfolioReadStore>(relaxed = true)

    private val main = SoraAccount("cnMain", "Main")
    private val savings = SoraAccount("cnSavings", "Savings")
    private val accounts = MutableStateFlow(listOf(main, savings))
    private val current = MutableStateFlow(main)
    private val connected = MutableStateFlow(true)

    private val xorToken: Token = TestTokens.xorToken.copy(isHidable = false, fiatPrice = 0.004, fiatPriceChange = 0.05, fiatSymbol = "$")
    private val valToken: Token = TestTokens.valToken.copy(fiatPrice = 0.1, fiatSymbol = "$")
    private val daiToken: Token = TestTokens.daiToken.copy(fiatPrice = 1.0, fiatSymbol = "$")
    private val rareToken: Token = valToken.copy(id = "0xrare", name = "Rare", symbol = "RARE", fiatPrice = null)

    private val mainAssets = MutableStateFlow(
        listOf(
            asset(xorToken, total = "10", transferable = "8"),
            asset(valToken, total = "0"),
            asset(daiToken, total = "5", visible = false),
        )
    )
    private val savingsAssets =
        MutableStateFlow(listOf(asset(xorToken, total = "3"), asset(valToken, total = "0")))
    private val mainNexus = MutableStateFlow(
        listOf(
            nexusRow("cnMain", WalletNetworkId.TAIRA, "2.5"),
            nexusRow("cnMain", WalletNetworkId.MINAMOTO, null, live = false),
        )
    )
    private val tokens = MutableStateFlow(listOf(xorToken, valToken, daiToken))
    private val pricesReadAt = MutableStateFlow<Long?>(null)

    private fun setUp() {
        every { userRepository.flowSoraAccountsList() } returns accounts
        every { userRepository.flowCurSoraAccount() } returns current
        every { assetsRepository.subscribeAssetsOfWhitelist("cnMain") } returns mainAssets
        every { assetsRepository.subscribeAssetsOfWhitelist("cnSavings") } returns savingsAssets
        every { assetsRepository.subscribeTokensList() } returns tokens
        every { assetsRepository.subscribeNewestPriceMillis() } returns pricesReadAt
        every { nexus.observeWallet("cnMain", history = false) } returns mainNexus
        every { nexus.observeWallet("cnSavings", history = false) } returns MutableStateFlow(emptyList())
        every { nexus.refresh() } just Runs
        every { connection.connectionState } returns connected
        every { connection.isConnected } answers { connected.value }
        coEvery { store.load() } returns null
    }

    private val source by lazy {
        setUp()
        newSource()
    }

    private fun newSource() = LivePortfolioSource(userRepository, assetsRepository, nexus, connection, store)
        .also {
            it.clock = { now }
            it.workContext = EmptyCoroutineContext
        }

    private var now = 1_000L

    private fun asset(token: Token, total: String, transferable: String = total, visible: Boolean = true) = Asset(
        token = token,
        favorite = true,
        position = 0,
        balance = AssetBalance(
            transferable = BigDecimal(transferable),
            reserved = BigDecimal.ZERO,
            miscFrozen = BigDecimal.ZERO,
            feeFrozen = BigDecimal.ZERO,
            bonded = BigDecimal.ZERO,
            redeemable = BigDecimal.ZERO,
            unbonding = BigDecimal.ZERO,
            total = BigDecimal(total),
        ),
        visibility = visible,
    )

    private fun nexusRow(wallet: String, network: WalletNetworkId, quantity: String?, live: Boolean = true) = NexusPortfolioBalance(
        walletId = wallet,
        networkId = network,
        networkName = network.wireId,
        isTestnet = network == WalletNetworkId.TAIRA,
        address = "$wallet-${network.wireId}",
        xorQuantity = quantity,
        assetDefinitionId = null,
        explorerBaseUrl = "https://example.invalid",
        sendAvailable = live,
        pendingTransactions = emptyList(),
        confirmedTransfers = emptyList(),
        historyErrorCode = null,
        errorCode = if (live && quantity == null) "NEXUS_BALANCE_UNAVAILABLE" else null,
        networkLive = live,
    )

    private fun xorBalance(transferable: String, total: String = transferable) = XorAssetBalance(
        transferable = BigDecimal(transferable),
        frozen = BigDecimal(total) - BigDecimal(transferable),
        totalBalance = BigDecimal(total),
        locked = BigDecimal.ZERO,
        bonded = BigDecimal.ZERO,
        reserved = BigDecimal(total) - BigDecimal(transferable),
        redeemable = BigDecimal.ZERO,
        unbonding = BigDecimal.ZERO,
    )

    private fun PortfolioSnapshot.holding(wallet: String, network: WalletNetworkId, tokenId: String): PortfolioHolding? =
        holdings.firstOrNull { it.walletId == wallet && it.network == network && it.token.id == tokenId }

    private val savedXor = SavedBalance(
        token = SavedToken(
            id = "0x0200000000000000000000000000000000000000000000000000000000000000",
            symbol = "XOR", name = "XOR", iconUri = "file:///saved-xor", precision = 18,
            price = 0.5, change = 0.1,
        ),
        total = "70",
        transferable = "60",
    )

    /** Nothing has answered yet: no rows, no tokens, no Nexus, the node still connecting. */
    private fun nothingAnswers() {
        setUp()
        val never: Flow<Nothing> = flow { awaitCancellation() }
        every { assetsRepository.subscribeAssetsOfWhitelist(any()) } returns never
        every { assetsRepository.subscribeTokensList() } returns never
        every { nexus.observeWallet(any(), history = false) } returns never
        connected.value = false
    }

    private suspend fun loaded(): PortfolioSnapshot =
        source.observe().first { it.addresses["cnMain"].orEmpty().size > 1 && it.holding("cnMain", WalletNetworkId.SORA2, xorToken.id) != null }

    @Test
    fun `reads every wallet by address and maps its holdings`() = runTest {
        val snapshot = loaded()
        val wallets = snapshot.wallets.map { it.id to it.selected }
        assertEquals(listOf("cnMain" to true, "cnSavings" to false), wallets)
        assertEquals(listOf("Main", "Savings"), snapshot.wallets.map { it.name })
        val xor = snapshot.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!
        assertEquals(0, BigDecimal("10").compareTo(xor.total))
        assertEquals(0, BigDecimal("8").compareTo(xor.transferable))
        assertFalse(xor.hidden)
        // A zero balance is not a holding.
        assertNull(snapshot.holding("cnMain", WalletNetworkId.SORA2, valToken.id))
        // Taira XOR is the XOR token (own price); a network that is not live has no holding.
        val taira = snapshot.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id)!!
        assertEquals(0.004, taira.token.price!!, 0.0)
        assertEquals("cnMain-taira", taira.address)
        assertNull(snapshot.holding("cnMain", WalletNetworkId.MINAMOTO, xorToken.id))
        assertEquals("cnMain-minamoto", snapshot.addresses["cnMain"]?.get(WalletNetworkId.MINAMOTO))
        assertEquals("cnSavings", snapshot.addresses["cnSavings"]?.get(WalletNetworkId.SORA2))
        assertTrue(snapshot.sync["cnMain"]!!.live)
        assertNull(snapshot.sync["cnSavings"]!!.refreshedAtMillis)
        assertTrue(snapshot.connected)
        assertEquals("$", snapshot.fiatSymbol)
    }

    @Test
    fun `the first picture never waits for the network`() = runTest {
        nothingAnswers()
        val first = newSource().observe().first()
        assertEquals(listOf("cnMain", "cnSavings"), first.wallets.map { it.id })
        assertTrue(first.holdings.isEmpty())
        assertTrue(first.sync.values.all { it.unread })
    }

    @Test
    fun `a cold launch shows every wallet's saved balances before anything answers`() = runTest {
        nothingAnswers()
        coEvery { store.load() } returns SavedPortfolio(
            fiatSymbol = "€",
            wallets = mapOf(
                "cnMain" to SavedWallet(
                    readAtMillis = 900L,
                    sora2 = listOf(savedXor),
                    networks = listOf(SavedNetworkBalance("taira", "cnMain-taira", "5")),
                ),
                "cnSavings" to SavedWallet(readAtMillis = 800L, sora2 = listOf(savedXor.copy(total = "30", transferable = "30"))),
            ),
        )
        val first = newSource().observe().first()
        assertEquals("€", first.fiatSymbol)
        val xor = first.holding("cnMain", WalletNetworkId.SORA2, savedXor.token.id)!!
        assertEquals(0, BigDecimal("70").compareTo(xor.total))
        assertEquals(0, BigDecimal("60").compareTo(xor.transferable))
        assertEquals(0.5, xor.token.price!!, 0.0)
        assertEquals("file:///saved-xor", xor.token.iconUri)
        assertEquals(0, BigDecimal("30").compareTo(first.holding("cnSavings", WalletNetworkId.SORA2, savedXor.token.id)!!.total))
        // The network has confirmed nothing yet: its saved balance shows, without Send or a Move target.
        val taira = first.holding("cnMain", WalletNetworkId.TAIRA, savedXor.token.id)!!
        assertEquals(0, BigDecimal("5").compareTo(taira.total))
        assertFalse(taira.sendAvailable)
        assertNull(first.addresses["cnMain"]?.get(WalletNetworkId.TAIRA))
        assertFalse(first.sync.values.any { it.unread })
        // Still connecting is not offline, and nothing claims to be live: it shows its age.
        assertTrue(first.connected)
        assertFalse(first.sync["cnMain"]!!.live)
        assertEquals(900L, first.sync["cnMain"]!!.refreshedAtMillis)
        val totals = PortfolioCalculator.scopeTotals(first, listOf("cnMain", "cnSavings"))
        assertEquals(50.0, totals.fiat, 0.001)
    }

    private fun selectedInMemory(address: String?) {
        setUp()
        every { userRepository.curSoraAccountIfLoaded() } returns address?.let { SoraAccount(it, it) }
    }

    private val savedPicture = SavedPortfolio(
        fiatSymbol = "€",
        scope = "selected",
        xor = savedXor.token,
        wallets = mapOf(
            "cnMain" to SavedWallet(
                name = "Main",
                readAtMillis = 900L,
                sora2 = listOf(savedXor),
                networks = listOf(SavedNetworkBalance("taira", "cnMain-taira", "5")),
            ),
            "cnSavings" to SavedWallet(name = "Savings", read = false),
        ),
    )

    @Test
    fun `switching wallets restarts no read and keeps every wallet's networks`() = runTest {
        val source = source
        val snapshots = mutableListOf<PortfolioSnapshot>()
        val collecting = launch { source.observe().collect { snapshots += it } }
        runCurrent()
        assertEquals("cnMain-taira", snapshots.last().addresses["cnMain"]?.get(WalletNetworkId.TAIRA))
        current.value = savings
        runCurrent()
        // The very first picture of the switch still knows the other network's address.
        val switched = snapshots.first { it.selectedWallet?.id == "cnSavings" }
        assertEquals("cnMain-taira", switched.addresses["cnMain"]?.get(WalletNetworkId.TAIRA))
        verify(exactly = 1) { nexus.observeWallet("cnMain", history = false) }
        verify(exactly = 1) { assetsRepository.subscribeAssetsOfWhitelist("cnMain") }
        collecting.cancel()
    }

    @Test
    fun `the first frame is the saved picture, with nothing else answering`() {
        selectedInMemory("cnMain")
        every { store.loadNow() } returns savedPicture
        val picture = newSource().savedPicture()!!
        assertEquals(PortfolioScope.SELECTED, picture.scope)
        val first = picture.snapshot
        assertTrue(first.saved)
        assertEquals("€", first.fiatSymbol)
        assertEquals(listOf("Main" to true, "Savings" to false), first.wallets.map { it.name to it.selected })
        assertEquals(0, BigDecimal("70").compareTo(first.holding("cnMain", WalletNetworkId.SORA2, savedXor.token.id)!!.total))
        // The saved network row uses the saved XOR, and offers no Send until the network answers.
        val taira = first.holding("cnMain", WalletNetworkId.TAIRA, savedXor.token.id)!!
        assertEquals("file:///saved-xor", taira.token.iconUri)
        assertFalse(taira.sendAvailable)
        // A wallet that had not been read when this was saved is still unknown, not empty.
        assertTrue(first.sync["cnSavings"]!!.unread)
        assertEquals(900L, first.sync["cnMain"]!!.refreshedAtMillis)
    }

    @Test
    fun `old prices in the saved picture carry their time and no 24 hour change`() {
        selectedInMemory("cnMain")
        val change = savedXor.copy(token = savedXor.token.copy(change = 0.05))
        val wallet = savedPicture.wallets.getValue("cnMain").copy(sora2 = listOf(change))
        val withWallet = savedPicture.copy(wallets = savedPicture.wallets + ("cnMain" to wallet))
        fun changeOf(picture: SavedPortfolio): Double? {
            every { store.loadNow() } returns picture
            return newSource().savedPicture()!!.snapshot.holding("cnMain", WalletNetworkId.SORA2, change.token.id)!!.token.change24h
        }
        now = 10L * 60_000L + 1L
        assertNull(changeOf(withWallet.copy(pricesReadAtMillis = 0L)))
        assertEquals(0.05, changeOf(withWallet.copy(pricesReadAtMillis = 1L))!!, 0.0)
        // Nothing is known about their age: they stay as they were saved.
        assertEquals(0.05, changeOf(withWallet)!!, 0.0)
        every { store.loadNow() } returns withWallet.copy(pricesReadAtMillis = 123L)
        assertEquals(123L, newSource().savedPicture()!!.snapshot.pricesReadAtMillis)
    }

    @Test
    fun `old prices say when they were read and lose their 24 hour change`() = runTest {
        setUp()
        now = 20L * 60_000L
        val source = newSource()
        val snapshots = mutableListOf<PortfolioSnapshot>()
        val collecting = launch { source.observe().collect { snapshots += it } }
        runCurrent()
        val xorOf = { snapshots.last().holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.token }
        // No time known: nothing says they are old.
        assertNull(snapshots.last().pricesReadAtMillis)
        assertEquals(0.05, xorOf().change24h!!, 0.0)
        pricesReadAt.value = now - 60_000L
        runCurrent()
        assertEquals(now - 60_000L, snapshots.last().pricesReadAtMillis)
        assertEquals(0.05, xorOf().change24h!!, 0.0)
        pricesReadAt.value = 1L
        runCurrent()
        assertEquals(1L, snapshots.last().pricesReadAtMillis)
        assertNull(xorOf().change24h)
        // The price itself stays: the total is valued with it and the hero says how old it is.
        assertEquals(0.004, xorOf().price!!, 0.0)
        // On the Nexus row as well.
        assertNull(snapshots.last().holding("cnMain", WalletNetworkId.TAIRA, xorToken.id)!!.token.change24h)
        collecting.cancel()
    }

    @Test
    fun `prices that keep coming do not change the picture every time`() = runTest {
        setUp()
        val source = newSource()
        val snapshots = mutableListOf<PortfolioSnapshot>()
        val collecting = launch { source.observe().collect { snapshots += it } }
        pricesReadAt.value = 1_000_000L
        runCurrent()
        assertEquals(1_000_000L, snapshots.last().pricesReadAtMillis)
        pricesReadAt.value = 1_020_000L
        runCurrent()
        assertEquals(1_000_000L, snapshots.last().pricesReadAtMillis)
        val later = 1_000_000L + LivePortfolioSource.PRICE_TIME_STEP_MS
        pricesReadAt.value = later
        runCurrent()
        assertEquals(later, snapshots.last().pricesReadAtMillis)
        collecting.cancel()
    }

    @Test
    fun `the price time is saved with the picture and comes back with it`() = runTest {
        setUp()
        val saved = slot<SavedPortfolio>()
        coEvery { store.save(capture(saved)) } just Runs
        pricesReadAt.value = 77L
        val source = newSource()
        val collecting = launch { source.observe().collect { } }
        advanceTimeBy(LivePortfolioSource.SAVE_DELAY_MS + 1)
        runCurrent()
        assertEquals(77L, saved.captured.pricesReadAtMillis)
        collecting.cancel()

        // Before the database has said anything, the saved time still stands.
        pricesReadAt.value = null
        coEvery { store.load() } returns savedPicture.copy(pricesReadAtMillis = 55L)
        val restored = newSource()
        val snapshots = mutableListOf<PortfolioSnapshot>()
        val second = launch { restored.observe().collect { snapshots += it } }
        runCurrent()
        assertEquals(55L, snapshots.last().pricesReadAtMillis)
        second.cancel()
    }

    @Test
    fun `a price time that cannot be read says nothing about the prices`() = runTest {
        setUp()
        every { assetsRepository.subscribeNewestPriceMillis() } returns flow { throw IllegalStateException("no database") }
        val source = newSource()
        val snapshots = mutableListOf<PortfolioSnapshot>()
        val collecting = launch { source.observe().collect { snapshots += it } }
        runCurrent()
        assertNull(snapshots.last().pricesReadAtMillis)
        assertEquals(0.05, snapshots.last().holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.token.change24h!!, 0.0)
        collecting.cancel()
    }

    @Test
    fun `a saved picture without the selected wallet never shows`() {
        selectedInMemory("cnNewWallet")
        every { store.loadNow() } returns savedPicture
        assertNull(newSource().savedPicture())
    }

    @Test
    fun `no saved picture, or no selected wallet in memory, means no first picture`() {
        selectedInMemory(null)
        every { store.loadNow() } returns savedPicture
        assertNull(newSource().savedPicture())
        selectedInMemory("cnMain")
        every { store.loadNow() } returns null
        assertNull(newSource().savedPicture())
    }

    @Test
    fun `live rows replace the saved balances of the selected wallet`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(wallets = mapOf("cnMain" to SavedWallet(readAtMillis = 900L, sora2 = listOf(savedXor))))
        val fresh = newSource().observe().first { it.sync["cnMain"]!!.live }
        assertEquals(0, BigDecimal("10").compareTo(fresh.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.total))
    }

    @Test
    fun `a network's answer replaces its saved balance, and a failed read keeps it`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(
            wallets = mapOf("cnMain" to SavedWallet(readAtMillis = 900L, networks = listOf(SavedNetworkBalance("taira", "cnMain-taira", "5")))),
        )
        val source = newSource()
        val answered = source.observe().first { it.addresses["cnMain"].orEmpty().containsKey(WalletNetworkId.TAIRA) }
        val live = answered.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id)!!
        assertEquals(0, BigDecimal("2.5").compareTo(live.total))
        assertTrue(live.sendAvailable)
        mainNexus.value = listOf(nexusRow("cnMain", WalletNetworkId.TAIRA, null))
        val failed = source.observe().first { it.sync["cnMain"]!!.unreachable.isNotEmpty() }
        val kept = failed.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id)!!
        assertEquals(0, BigDecimal("2.5").compareTo(kept.total))
        assertFalse(kept.sendAvailable)
    }

    @Test
    fun `a network failure after a successful zero never resurrects saved funds`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(
            wallets = mapOf("cnMain" to SavedWallet(readAtMillis = 900L, networks = listOf(SavedNetworkBalance("taira", "cnMain-taira", "5")))),
        )
        mainNexus.value = listOf(nexusRow("cnMain", WalletNetworkId.TAIRA, "0"))
        val source = newSource()
        source.observe().first { it.addresses["cnMain"].orEmpty().containsKey(WalletNetworkId.TAIRA) }
        mainNexus.value = listOf(nexusRow("cnMain", WalletNetworkId.TAIRA, null))
        val failed = source.observe().first { it.sync["cnMain"]!!.unreachable.isNotEmpty() }
        assertNull(failed.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id))
    }

    @Test
    fun `spending the selected wallet's last funds never restores an older read`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(
            wallets = mapOf("cnMain" to SavedWallet(readAtMillis = 900L, sora2 = listOf(savedXor))),
        )
        mainNexus.value = emptyList()
        val source = newSource()
        val seen = mutableListOf<PortfolioSnapshot>()
        val job = launch { source.observe().collect { seen += it } }
        runCurrent()
        assertEquals(BigDecimal("10"), seen.last().holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.total)
        mainAssets.value = listOf(asset(xorToken, total = "0"))
        runCurrent()
        val empty = seen.last()
        assertTrue(empty.holdings.none { it.walletId == "cnMain" })
        assertFalse(empty.sync.getValue("cnMain").unread)
        assertTrue(empty.sync.getValue("cnMain").live)
        advanceTimeBy(LivePortfolioSource.SAVE_DELAY_MS + 1)
        coVerify { store.save(match { it.wallets["cnMain"]?.sora2?.isEmpty() == true }) }
        job.cancel()
        runCurrent()
        seen.clear()
        every { assetsRepository.subscribeAssetsOfWhitelist("cnMain") } returns flow { awaitCancellation() }
        val restarted = launch { source.observe().collect { seen += it } }
        runCurrent()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { snapshot -> snapshot.holdings.none { it.walletId == "cnMain" } })
        assertFalse(seen.last().sync.getValue("cnMain").unread)
        assertFalse(seen.last().sync.getValue("cnMain").live)
        restarted.cancel()
    }

    @Test
    fun `a row observation failure after spending cannot resurrect saved funds`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(
            wallets = mapOf("cnMain" to SavedWallet(readAtMillis = 900L, sora2 = listOf(savedXor))),
        )
        mainNexus.value = emptyList()
        val spent = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<Unit>()
        every { assetsRepository.subscribeAssetsOfWhitelist("cnMain") } returns flow {
            emit(listOf(asset(xorToken, total = "10")))
            spent.await()
            emit(listOf(asset(xorToken, total = "0")))
            failed.await()
            throw IllegalStateException("database observation stopped")
        }
        val source = newSource()
        val seen = mutableListOf<PortfolioSnapshot>()
        val job = launch { source.observe().collect { seen += it } }
        runCurrent()
        spent.complete(Unit)
        runCurrent()
        failed.complete(Unit)
        runCurrent()
        assertNull(seen.last().holding("cnMain", WalletNetworkId.SORA2, xorToken.id))
        assertTrue(WalletNetworkId.SORA2 in seen.last().sync.getValue("cnMain").unreachable)
        assertFalse(seen.last().sync.getValue("cnMain").live)
        job.cancel()
    }

    @Test
    fun `a network that answered and no longer lists the wallet shows nothing saved`() = runTest {
        setUp()
        coEvery { store.load() } returns SavedPortfolio(
            wallets = mapOf("cnSavings" to SavedWallet(readAtMillis = 900L, networks = listOf(SavedNetworkBalance("taira", "a", "5")))),
        )
        val snapshot = newSource().observe().first { s -> s.holdings.none { it.walletId == "cnSavings" && it.network == WalletNetworkId.TAIRA } }
        assertNull(snapshot.holding("cnSavings", WalletNetworkId.TAIRA, xorToken.id))
    }

    @Test
    fun `the picture is saved once it settles, only for the wallets this app has`() = runTest {
        setUp()
        val saved = slot<SavedPortfolio>()
        coEvery { store.save(capture(saved)) } just Runs
        coEvery { store.load() } returns SavedPortfolio(wallets = mapOf("cnGone" to SavedWallet(readAtMillis = 1L, sora2 = listOf(savedXor))))
        val source = newSource()
        val job = launch { source.observe().collect {} }
        advanceTimeBy(LivePortfolioSource.SAVE_DELAY_MS + 1)
        runCurrent()
        assertTrue(saved.isCaptured)
        assertEquals(setOf("cnMain", "cnSavings"), saved.captured.wallets.keys)
        val main = saved.captured.wallets.getValue("cnMain")
        assertEquals(listOf(xorToken.id, daiToken.id), main.sora2.map { it.token.id })
        assertEquals("10", main.sora2.first().total)
        assertEquals(listOf("taira"), main.networks.map { it.network })
        job.cancel()
    }

    @Test
    fun `no accounts left clears the saved reads`() = runTest {
        setUp()
        accounts.value = emptyList()
        val job = launch { newSource().observe().collect {} }
        advanceTimeBy(LivePortfolioSource.SAVE_DELAY_MS + 1)
        runCurrent()
        coVerify { store.clear() }
        job.cancel()
    }

    @Test
    fun `removing a wallet prunes its saved balances even while the view is not observing`() = runTest {
        setUp()
        val source = newSource()
        val job = launch { source.pruneRemovedWallets() }
        runCurrent()
        accounts.value = listOf(main)
        runCurrent()
        coVerify { store.retain(setOf("cnMain", "cnSavings")) }
        coVerify { store.retain(setOf("cnMain")) }
        job.cancel()
    }

    @Test
    fun `a wallet that has never been selected shows what the chain says it holds`() = runTest {
        setUp()
        // No rows at all: this wallet was added and never selected.
        every { assetsRepository.subscribeAssetsOfWhitelist("cnSavings") } returns MutableStateFlow(emptyList())
        tokens.value = listOf(xorToken, valToken, rareToken)
        coEvery {
            assetsRepository.fetchBalance("cnSavings", listOf(valToken.id, rareToken.id))
        } returns listOf(BigInteger("2000000000000000000"), BigInteger("7000000000000000000"))
        coEvery { assetsRepository.getXORBalance("cnSavings", 18) } returns xorBalance("4", "4.5")
        val source = newSource()
        val before = source.observe().first { it.wallets.isNotEmpty() }
        assertTrue(before.sync["cnSavings"]!!.unread)
        assertTrue(before.holdings.none { it.walletId == "cnSavings" })
        now = 42_000L
        source.refresh(listOf("cnSavings"))
        val snapshot = source.observe().first { it.sync["cnSavings"]?.refreshedAtMillis == 42_000L }
        assertFalse(snapshot.sync["cnSavings"]!!.unread)
        val xor = snapshot.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id)!!
        assertEquals(0, BigDecimal("4.5").compareTo(xor.total))
        assertEquals(0, BigDecimal("4").compareTo(xor.transferable))
        assertEquals(0, BigDecimal("2").compareTo(snapshot.holding("cnSavings", WalletNetworkId.SORA2, valToken.id)!!.total))
        // A token nobody turned on is listed: the wallet holds it.
        val rare = snapshot.holding("cnSavings", WalletNetworkId.SORA2, rareToken.id)!!
        assertEquals(0, BigDecimal("7").compareTo(rare.total))
        assertFalse(rare.hidden)
        assertEquals(42_000L, source.lastReads.value["cnSavings"])
        coVerify(exactly = 0) { assetsRepository.updateBalancesVisibleAssets(any()) }
        coVerify(exactly = 0) { assetsRepository.updateWhitelistBalances(any()) }
        coVerify(exactly = 0) { assetsRepository.getAssetsWhitelist(any()) }
        verify { nexus.refresh() }
    }

    @Test
    fun `the same token has one price in every wallet`() = runTest {
        setUp()
        tokens.value = listOf(xorToken.copy(fiatPrice = 9.0), valToken, daiToken)
        val snapshot = source.observe().first { s -> s.holdings.any { it.token.price == 9.0 } }
        assertEquals(9.0, snapshot.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.token.price!!, 0.0)
        assertEquals(9.0, snapshot.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id)!!.token.price!!, 0.0)
    }

    @Test
    fun `only the known tokens' visibility is a choice`() = runTest {
        setUp()
        mainAssets.value = listOf(
            asset(xorToken, total = "1"),
            asset(valToken.copy(id = TestTokens.valToken.id), total = "2", visible = false),
            asset(rareToken, total = "3", visible = false),
        )
        val snapshot = source.observe().first { s -> s.holding("cnMain", WalletNetworkId.SORA2, rareToken.id) != null }
        assertFalse(snapshot.holding("cnMain", WalletNetworkId.SORA2, rareToken.id)!!.hidden)
        assertEquals(
            jp.co.soramitsu.common.domain.AssetHolder.isKnownAsset(TestTokens.valToken.id),
            snapshot.holding("cnMain", WalletNetworkId.SORA2, TestTokens.valToken.id)!!.hidden,
        )
    }

    @Test
    fun `reads arrive one by one and a slow wallet holds back nobody`() = runTest {
        setUp()
        val third = SoraAccount("cnThird", "Third")
        accounts.value = listOf(main, savings, third)
        every { assetsRepository.subscribeAssetsOfWhitelist("cnThird") } returns MutableStateFlow(emptyList())
        every { nexus.observeWallet("cnThird", history = false) } returns MutableStateFlow(emptyList())
        tokens.value = listOf(xorToken)
        val slow = CompletableDeferred<XorAssetBalance>()
        coEvery { assetsRepository.getXORBalance("cnSavings", any()) } coAnswers { slow.await() }
        coEvery { assetsRepository.getXORBalance("cnThird", any()) } returns xorBalance("6")
        val source = newSource()
        val refreshing = async { source.refresh(listOf("cnSavings", "cnThird")) }
        runCurrent()
        val partial = source.observe().first { it.wallets.size == 3 && it.sync["cnThird"]?.unread == false }
        assertEquals(0, BigDecimal("6").compareTo(partial.holding("cnThird", WalletNetworkId.SORA2, xorToken.id)!!.total))
        assertFalse(refreshing.isCompleted)
        slow.complete(xorBalance("9"))
        refreshing.await()
    }

    @Test
    fun `a failed read keeps the last values and only records the attempt`() = runTest {
        setUp()
        coEvery { assetsRepository.subscribeTokensList() } returns flow { throw IllegalStateException("db") }
        coEvery { assetsRepository.tokensList() } throws IllegalStateException("socket closed")
        val source = newSource()
        source.observe().first { it.wallets.isNotEmpty() }
        now = 9_000L
        source.refresh(listOf("cnSavings"))
        val snapshot = source.observe().first { it.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id) != null }
        assertNull(snapshot.sync["cnSavings"]!!.refreshedAtMillis)
        // Its old rows held something: those show with no age, and they are not "unread".
        assertEquals(0, BigDecimal("3").compareTo(snapshot.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id)!!.total))
        assertFalse(snapshot.sync["cnSavings"]!!.unread)
        assertTrue(snapshot.sync["cnSavings"]!!.unreachable.isEmpty())
        assertEquals(9_000L, source.lastReads.value["cnSavings"])
    }

    @Test
    fun `a wallet that could never be read is unknown and says so, it does not look empty`() = runTest {
        setUp()
        every { assetsRepository.subscribeAssetsOfWhitelist("cnSavings") } returns MutableStateFlow(emptyList())
        coEvery { assetsRepository.getXORBalance("cnSavings", any()) } throws IllegalStateException("no node")
        coEvery { assetsRepository.fetchBalance("cnSavings", any()) } throws IllegalStateException("no node")
        val source = newSource()
        source.refresh(listOf("cnSavings"))
        val snapshot = source.observe().first { it.sync["cnSavings"]?.unreachable?.isNotEmpty() == true }
        assertTrue(snapshot.sync["cnSavings"]!!.unread)
        assertEquals(setOf(WalletNetworkId.SORA2), snapshot.sync["cnSavings"]!!.unreachable)
        assertTrue(snapshot.holdings.none { it.walletId == "cnSavings" })
        val totals = PortfolioCalculator.walletTotals(snapshot, "cnSavings")
        assertEquals(1, totals.unread)
        assertFalse(totals.known)
    }

    @Test
    fun `a read that never answers times out and counts as failed`() = runTest {
        setUp()
        coEvery { assetsRepository.subscribeTokensList() } returns tokens
        coEvery { assetsRepository.fetchBalance("cnSavings", any()) } coAnswers { awaitCancellation() }
        val source = newSource()
        now = 5_000L
        source.refresh(listOf("cnSavings"))
        assertEquals(LivePortfolioSource.READ_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(5_000L, source.lastReads.value["cnSavings"])
    }

    @Test
    fun `an unreachable Nexus network makes the wallet partial`() = runTest {
        mainNexus.value = listOf(nexusRow("cnMain", WalletNetworkId.TAIRA, null))
        val snapshot = source.observe().first { it.addresses["cnMain"].orEmpty().size > 1 }
        assertEquals(setOf(WalletNetworkId.TAIRA), snapshot.sync["cnMain"]!!.unreachable)
        assertNull(snapshot.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id))
    }

    @Test
    fun `offline only after the node has stayed away, the selected wallet too`() = runTest {
        connected.value = false
        val first = source.observe().first()
        assertTrue(first.connected)
        val snapshot = source.observe().first { !it.connected }
        assertFalse(snapshot.connected)
        assertEquals(LivePortfolioSource.OFFLINE_AFTER_MS, testScheduler.currentTime)
        val ids = snapshot.wallets.map { it.id }
        assertEquals(FreshnessKind.OFFLINE, PortfolioCalculator.worstFreshness(snapshot, ids, now)?.kind)
        assertEquals(FreshnessKind.OFFLINE, PortfolioFreshness.of(snapshot.sync["cnMain"], snapshot.connected, now).kind)
        connected.value = true
        val back = source.observe().first { it.connected && it.sync["cnMain"]!!.live }
        assertEquals(FreshnessKind.LIVE, PortfolioFreshness.of(back.sync["cnMain"], back.connected, now).kind)
    }

    @Test
    fun `switching wallets uses the app's account switch and keeps live values`() = runTest {
        setUp()
        coEvery { userRepository.soraAccountsList() } returns accounts.value
        coEvery { userRepository.setCurSoraAccount(savings) } answers { current.value = savings }
        val source = newSource()
        val seen = mutableListOf<PortfolioSnapshot>()
        val job = launch { source.observe().collect { seen += it } }
        runCurrent()
        now = 77_000L
        source.select("cnSavings")
        runCurrent()
        coVerify { userRepository.setCurSoraAccount(savings) }
        val snapshot = seen.last { it.selectedWallet?.id == "cnSavings" }
        // The wallet that was live until now keeps its live values; the switch time is its last sync.
        assertEquals(77_000L, snapshot.sync["cnMain"]!!.refreshedAtMillis)
        assertEquals(0, BigDecimal("10").compareTo(snapshot.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.total))
        assertTrue(snapshot.sync["cnSavings"]!!.live)
        val unknown = runCatching { source.select("cnUnknown") }
        assertTrue(unknown.isFailure)
        job.cancel()
    }

    @Test
    fun `the selected wallet shows its last read until its rows hold something`() = runTest {
        setUp()
        val rows = MutableStateFlow(listOf(asset(xorToken, total = "0")))
        every { assetsRepository.subscribeAssetsOfWhitelist("cnMain") } returns rows
        tokens.value = listOf(xorToken)
        coEvery { assetsRepository.getXORBalance("cnMain", any()) } returns xorBalance("11")
        val source = newSource()
        source.refresh(listOf("cnMain"))
        val read = source.observe().first { it.holding("cnMain", WalletNetworkId.SORA2, xorToken.id) != null }
        assertEquals(0, BigDecimal("11").compareTo(read.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)!!.total))
        rows.value = listOf(asset(xorToken, total = "12"))
        val live = source.observe().first { it.holding("cnMain", WalletNetworkId.SORA2, xorToken.id)?.total?.compareTo(BigDecimal("12")) == 0 }
        assertTrue(live.sync["cnMain"]!!.live)
    }
}
