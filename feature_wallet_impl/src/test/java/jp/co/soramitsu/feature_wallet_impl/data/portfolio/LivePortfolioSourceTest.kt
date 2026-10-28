package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortfolioSourceTest {

    private val userRepository = mockk<UserRepository>()
    private val assetsRepository = mockk<AssetsRepository>()
    private val nexus = mockk<NexusPortfolioRepository>()
    private val connection = mockk<ConnectionManager>()

    private val main = SoraAccount("cnMain", "Main")
    private val savings = SoraAccount("cnSavings", "Savings")
    private val accounts = MutableStateFlow(listOf(main, savings))
    private val current = MutableStateFlow(main)
    private val connected = MutableStateFlow(true)

    private val xorToken: Token = TestTokens.xorToken.copy(isHidable = false, fiatPrice = 0.004, fiatPriceChange = 0.05, fiatSymbol = "$")
    private val valToken: Token = TestTokens.valToken.copy(fiatPrice = 0.1, fiatSymbol = "$")
    private val daiToken: Token = TestTokens.daiToken.copy(fiatPrice = 1.0, fiatSymbol = "$")

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

    private val source by lazy {
        every { userRepository.flowSoraAccountsList() } returns accounts
        every { userRepository.flowCurSoraAccount() } returns current
        every { assetsRepository.subscribeAssetsActive("cnMain") } returns mainAssets
        every { assetsRepository.subscribeAssetsActive("cnSavings") } returns savingsAssets
        every { nexus.observeWallet("cnMain") } returns mainNexus
        every { nexus.observeWallet("cnSavings") } returns MutableStateFlow(emptyList())
        every { nexus.refresh() } just Runs
        every { connection.connectionState } returns connected
        every { connection.isConnected } answers { connected.value }
        LivePortfolioSource(userRepository, assetsRepository, nexus, connection).also { it.clock = { now } }
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

    private fun PortfolioSnapshot.holding(wallet: String, network: WalletNetworkId, tokenId: String): PortfolioHolding? =
        holdings.firstOrNull { it.walletId == wallet && it.network == network && it.token.id == tokenId }

    /**
     * The first snapshot comes from the cached SORA2 balances alone (Nexus rows start empty so a
     * slow network never holds the dashboard back); this waits for the main wallet's Nexus rows.
     */
    private suspend fun loaded(): PortfolioSnapshot =
        source.observe().first { it.addresses["cnMain"].orEmpty().size > 1 }

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
        // A zero balance is not a holding; the user's hidden choice is kept per wallet.
        assertNull(snapshot.holding("cnMain", WalletNetworkId.SORA2, valToken.id))
        assertTrue(snapshot.holding("cnMain", WalletNetworkId.SORA2, daiToken.id)!!.hidden)
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
    fun `the cached SORA2 balances show before the Nexus networks answer`() = runTest {
        val first = source.observe().first()
        assertTrue(first.holding("cnMain", WalletNetworkId.SORA2, xorToken.id) != null)
        assertTrue(first.holdings.none { it.network != WalletNetworkId.SORA2 })
    }

    @Test
    fun `refresh reads another wallet read-only and overlays the fresh balances`() = runTest {
        coEvery { assetsRepository.getAssetsActive("cnSavings") } returns savingsAssets.value
        coEvery {
            assetsRepository.fetchBalance("cnSavings", listOf(valToken.id))
        } returns listOf(BigInteger("2000000000000000000"))
        coEvery { assetsRepository.getXORBalance("cnSavings", 18) } returns XorAssetBalance(
            transferable = BigDecimal("4"),
            frozen = BigDecimal("0.5"),
            totalBalance = BigDecimal("4.5"),
            locked = BigDecimal.ZERO,
            bonded = BigDecimal.ZERO,
            reserved = BigDecimal("0.5"),
            redeemable = BigDecimal.ZERO,
            unbonding = BigDecimal.ZERO,
        )
        source.observe().first { it.wallets.isNotEmpty() }
        now = 42_000L
        source.refresh(listOf("cnSavings"))
        val snapshot = source.observe().first { it.sync["cnSavings"]?.refreshedAtMillis == 42_000L }
        val xor = snapshot.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id)!!
        assertEquals(0, BigDecimal("4.5").compareTo(xor.total))
        assertEquals(0, BigDecimal("4").compareTo(xor.transferable))
        assertEquals(0, BigDecimal("2").compareTo(snapshot.holding("cnSavings", WalletNetworkId.SORA2, valToken.id)!!.total))
        assertEquals(42_000L, source.lastReads.value["cnSavings"])
        coVerify(exactly = 0) { assetsRepository.updateBalancesVisibleAssets(any()) }
        coVerify(exactly = 0) { assetsRepository.updateWhitelistBalances(any()) }
        verify { nexus.refresh() }
    }

    @Test
    fun `a failed read keeps the last values and only records the attempt`() = runTest {
        coEvery {
            assetsRepository.getAssetsActive("cnSavings")
        } throws IllegalStateException("socket closed")
        source.observe().first { it.wallets.isNotEmpty() }
        now = 9_000L
        source.refresh(listOf("cnSavings"))
        val snapshot = source.observe().first()
        assertNull(snapshot.sync["cnSavings"]!!.refreshedAtMillis)
        assertEquals(0, BigDecimal("3").compareTo(snapshot.holding("cnSavings", WalletNetworkId.SORA2, xorToken.id)!!.total))
        assertEquals(9_000L, source.lastReads.value["cnSavings"])
    }

    @Test
    fun `a read that never answers times out and counts as failed`() = runTest {
        coEvery { assetsRepository.getAssetsActive("cnSavings") } coAnswers { awaitCancellation() }
        source.observe().first { it.wallets.isNotEmpty() }
        now = 5_000L
        source.refresh(listOf("cnSavings"))
        assertEquals(LivePortfolioSource.READ_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(5_000L, source.lastReads.value["cnSavings"])
        assertNull(source.observe().first().sync["cnSavings"]!!.refreshedAtMillis)
    }

    @Test
    fun `an unreachable Nexus network makes the wallet partial`() = runTest {
        mainNexus.value = listOf(nexusRow("cnMain", WalletNetworkId.TAIRA, null))
        val snapshot = source.observe().first { it.addresses["cnMain"].orEmpty().size > 1 }
        assertEquals(setOf(WalletNetworkId.TAIRA), snapshot.sync["cnMain"]!!.unreachable)
        assertNull(snapshot.holding("cnMain", WalletNetworkId.TAIRA, xorToken.id))
    }

    @Test
    fun `without a node connection every wallet is offline, the selected one too`() = runTest {
        connected.value = false
        val snapshot = loaded()
        assertFalse(snapshot.connected)
        val ids = snapshot.wallets.map { it.id }
        assertEquals(FreshnessKind.OFFLINE, PortfolioCalculator.worstFreshness(snapshot, ids, now)?.kind)
        assertEquals(FreshnessKind.OFFLINE, PortfolioFreshness.of(snapshot.sync["cnMain"], snapshot.connected, now).kind)
        connected.value = true
        val back = source.observe().first { it.connected }
        assertEquals(FreshnessKind.LIVE, PortfolioFreshness.of(back.sync["cnMain"], back.connected, now).kind)
    }

    @Test
    fun `switching wallets uses the app's account switch and keeps live values`() = runTest {
        coEvery { userRepository.soraAccountsList() } returns accounts.value
        coEvery { userRepository.setCurSoraAccount(savings) } answers { current.value = savings }
        source.observe().first { it.wallets.isNotEmpty() }
        now = 77_000L
        source.select("cnSavings")
        coVerify { userRepository.setCurSoraAccount(savings) }
        val snapshot = source.observe().first { it.selectedWallet?.id == "cnSavings" }
        // The wallet that was live until now keeps its live values; the switch time is its last sync.
        assertEquals(77_000L, snapshot.sync["cnMain"]!!.refreshedAtMillis)
        assertTrue(snapshot.sync["cnSavings"]!!.live)
        val unknown = runCatching { source.select("cnUnknown") }
        assertTrue(unknown.isFailure)
    }
}
