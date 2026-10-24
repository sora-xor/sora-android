package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.DAI
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.MAIN
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.SAVINGS
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.TRADING
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.dai
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.eth
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.holding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.kxor
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.pswap
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.snapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.valToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.xor
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.xstusd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioCalculatorTest {

    private val mixed = listOf(
        holding(MAIN, xor, "1000"),
        holding(MAIN, eth, "1"),
        holding(MAIN, dai, "50", hidden = true),
        holding(MAIN, kxor, "10"),
        holding(MAIN, xor, "500", network = WalletNetworkId.TAIRA),
        holding(MAIN, xor, "250", network = WalletNetworkId.MINAMOTO),
    )

    @Test
    fun `counts only visible priced mainnet holdings and names the rest`() {
        val totals = PortfolioCalculator.totals(mixed)
        // XOR 1000 × 0.004 + ETH 1 × 2000 + Minamoto XOR 250 × 0.004 (its own price).
        assertEquals(4.0 + 2000.0 + 1.0, totals.fiat, 1e-9)
        assertEquals(1, totals.hidden)
        assertEquals(1, totals.unpriced)
        assertEquals(1, totals.testnet)
        assertEquals(3, totals.excluded)
    }

    @Test
    fun `exclusion kinds and counted fiat use the asset's own price only`() {
        assertEquals(Exclusion.TESTNET, PortfolioCalculator.exclusion(holding(MAIN, xor, "1", network = WalletNetworkId.TAIRA)))
        assertEquals(Exclusion.HIDDEN, PortfolioCalculator.exclusion(holding(MAIN, kxor, "1", hidden = true)))
        assertEquals(Exclusion.UNPRICED, PortfolioCalculator.exclusion(holding(MAIN, kxor, "1")))
        assertNull(PortfolioCalculator.exclusion(holding(MAIN, xor, "1", network = WalletNetworkId.MINAMOTO)))
        assertNull(PortfolioCalculator.countedFiat(holding(MAIN, kxor, "10")))
        assertNull(PortfolioCalculator.countedFiat(holding(MAIN, xor, "10", network = WalletNetworkId.TAIRA)))
        assertEquals(0.04, PortfolioCalculator.countedFiat(holding(MAIN, xor, "10"))!!, 1e-12)
    }

    @Test
    fun `24 h change comes from each counted token's own price change`() {
        val totals = PortfolioCalculator.totals(listOf(holding(MAIN, xor, "1000"), holding(MAIN, eth, "1"), holding(MAIN, pswap, "100")))
        val xorChange = 4.0 - 4.0 / 1.05
        val ethChange = 2000.0 - 2000.0 / 1.25
        assertEquals(xorChange + ethChange, totals.change, 1e-9)
        assertTrue(totals.changeKnown)
        val fiat = 4.0 + 2000.0 + 0.1
        assertEquals((xorChange + ethChange) / (fiat - xorChange - ethChange), totals.changeFraction!!, 1e-12)
        assertNull(PortfolioCalculator.totals(listOf(holding(MAIN, pswap, "100"))).changeFraction)
    }

    @Test
    fun `all wallets total is the sum of each wallet's own total with its own hidden choices`() {
        val snap = snapshot(
            listOf(
                holding(MAIN, eth, "1"),
                holding(SAVINGS, dai, "100", hidden = true),
                holding(TRADING, dai, "100"),
                holding(TRADING, kxor, "5"),
            )
        )
        val all = PortfolioCalculator.scopeTotals(snap, snap.wallets.map { it.id })
        val perWallet = snap.wallets.map { w ->
            PortfolioCalculator.totals(snap.holdings.filter { it.walletId == w.id })
        }
        assertEquals(perWallet.sumOf { it.fiat }, all.fiat, 1e-9)
        assertEquals(2100.0, all.fiat, 1e-9)
        assertEquals(1, all.hidden)
        assertEquals(1, all.unpriced)
        assertEquals(2000.0, PortfolioCalculator.scopeTotals(snap, listOf(MAIN)).fiat, 1e-9)
    }

    @Test
    fun `All wallets appears with two or more wallets and is their default`() {
        assertEquals(PortfolioScope.SELECTED, PortfolioCalculator.effectiveScope(null, 1))
        assertEquals(PortfolioScope.SELECTED, PortfolioCalculator.effectiveScope(PortfolioScope.ALL, 1))
        assertEquals(PortfolioScope.ALL, PortfolioCalculator.effectiveScope(null, 2))
        assertEquals(PortfolioScope.SELECTED, PortfolioCalculator.effectiveScope(PortfolioScope.SELECTED, 3))
        assertEquals(listOf(PortfolioLens.TOKENS, PortfolioLens.NETWORKS), PortfolioCalculator.availableLenses(PortfolioScope.SELECTED))
        assertEquals(PortfolioLens.entries.toList(), PortfolioCalculator.availableLenses(PortfolioScope.ALL))
        assertEquals(PortfolioLens.TOKENS, PortfolioCalculator.effectiveLens(PortfolioLens.WALLETS, PortfolioScope.SELECTED))
        assertEquals(PortfolioLens.TOKENS, PortfolioCalculator.effectiveLens(null, PortfolioScope.ALL))
        assertEquals(PortfolioLens.WALLETS, PortfolioCalculator.effectiveLens(PortfolioLens.WALLETS, PortfolioScope.ALL))
        val snap = snapshot(emptyList(), selected = SAVINGS)
        assertEquals(listOf(SAVINGS), PortfolioCalculator.scopeWalletIds(snap, PortfolioScope.SELECTED))
        assertEquals(listOf(MAIN, SAVINGS, TRADING), PortfolioCalculator.scopeWalletIds(snap, PortfolioScope.ALL))
    }

    @Test
    fun `tokens lens groups one token across networks and wallets and rows add up to the header`() {
        val snap = snapshot(
            listOf(
                holding(MAIN, xor, "1000"),
                holding(TRADING, xor, "500"),
                holding(MAIN, xor, "250", network = WalletNetworkId.MINAMOTO),
                holding(MAIN, xor, "999", network = WalletNetworkId.TAIRA),
                holding(SAVINGS, eth, "1"),
                holding(SAVINGS, kxor, "7"),
                holding(SAVINGS, dai, "3", hidden = true),
            )
        )
        val ids = snap.wallets.map { it.id }
        val groups = PortfolioCalculator.groups(snap, ids, PortfolioLens.TOKENS)
        val order = listOf(PortfolioSlots.ETH_ID, AssetHolder.XOR_ASSET_ID, PortfolioFixtures.KXOR)
        assertEquals(order, groups.map { it.key })
        val xorGroup = groups.single { it.key == AssetHolder.XOR_ASSET_ID }
        assertEquals(listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA), xorGroup.networks)
        assertEquals(listOf(MAIN, TRADING), xorGroup.walletIds)
        // The token's amount is its visible mainnet amount; Taira is listed but not counted.
        assertEquals(0, BigDecimal("1750").compareTo(xorGroup.amount))
        assertEquals(4, xorGroup.holdings.size)
        assertEquals(WalletNetworkId.TAIRA, xorGroup.holdings.last().network)
        assertEquals(PortfolioCalculator.scopeTotals(snap, ids).fiat, groups.sumOf { it.countedFiat }, 1e-9)
        assertEquals(1.0, groups.sumOf { it.share }, 1e-9)
        assertTrue(groups.none { group -> group.holdings.any { it.hidden } })
        assertTrue(groups.none { it.key == DAI })
    }

    @Test
    fun `a testnet-only token keeps its testnet amount and sorts after counted tokens`() {
        val snap =
            snapshot(listOf(holding(MAIN, xor, "999", network = WalletNetworkId.TAIRA), holding(MAIN, valToken, "10")))
        val groups = PortfolioCalculator.groups(snap, listOf(MAIN), PortfolioLens.TOKENS)
        assertEquals(PortfolioSlots.VAL_ID, groups.first().key)
        val test = groups.last()
        assertTrue(test.testnetOnly)
        assertEquals(0, BigDecimal("999").compareTo(test.amount))
        assertEquals(0.0, test.countedFiat, 0.0)
    }

    @Test
    fun `networks lens groups by network and never counts Taira`() {
        val snap = snapshot(
            listOf(
                holding(MAIN, xor, "1000"),
                holding(MAIN, eth, "1"),
                holding(MAIN, xor, "500", network = WalletNetworkId.MINAMOTO),
                holding(TRADING, xor, "100", network = WalletNetworkId.TAIRA),
            )
        )
        val groups = PortfolioCalculator.groups(snap, snap.wallets.map { it.id }, PortfolioLens.NETWORKS)
        assertEquals(listOf("sora2", "minamoto", "taira"), groups.map { it.key })
        assertEquals(2004.0, groups[0].countedFiat, 1e-9)
        assertEquals(2.0, groups[1].countedFiat, 1e-9)
        assertEquals(0.0, groups[2].countedFiat, 0.0)
        assertTrue(groups[2].testnetOnly)
        assertEquals(1.0, groups.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `wallets lens lists every wallet in scope, empty ones too`() {
        val snap = snapshot(listOf(holding(SAVINGS, eth, "1"), holding(MAIN, xor, "1000")))
        val groups = PortfolioCalculator.groups(snap, snap.wallets.map { it.id }, PortfolioLens.WALLETS)
        assertEquals(listOf(SAVINGS, MAIN, TRADING), groups.map { it.key })
        assertTrue(groups.last().holdings.isEmpty())
        assertEquals(0.0, groups.last().share, 0.0)
    }

    @Test
    fun `fan has at most five slots plus Other and always 100 rays`() {
        val tokens = (1..7).map { PortfolioFixtures.token("token-$it", "T$it", 1.0) }
        val snap =
            snapshot(tokens.mapIndexed { i, t -> holding(MAIN, t, (100 - i * 10).toString()) })
        val groups = PortfolioCalculator.groups(snap, listOf(MAIN), PortfolioLens.TOKENS)
        val fan = PortfolioCalculator.fan(groups)
        assertEquals(6, fan.size)
        val keys = listOf("token-1", "token-2", "token-3", "token-4", "token-5", null)
        assertEquals(keys, fan.map { it.key })
        assertEquals(100, fan.sumOf { it.rays })
        assertEquals(listOf(21, 19, 16, 14, 12, 18), fan.map { it.rays })
        // Other folds everything after the top five: 50 + 40.
        assertEquals(90.0, fan.last().countedFiat, 1e-9)
        assertTrue(PortfolioCalculator.fan(PortfolioCalculator.groups(snapshot(emptyList()), listOf(MAIN), PortfolioLens.TOKENS)).isEmpty())
    }

    @Test
    fun `fan without Other when five or fewer groups are counted`() {
        val snap =
            snapshot(listOf(holding(MAIN, xor, "1000"), holding(MAIN, xstusd, "4"), holding(MAIN, kxor, "9")))
        val fan = PortfolioCalculator.fan(PortfolioCalculator.groups(snap, listOf(MAIN), PortfolioLens.TOKENS))
        assertEquals(listOf(AssetHolder.XOR_ASSET_ID, PortfolioSlots.XSTUSD_ID), fan.map { it.key })
        assertEquals(listOf(50, 50), fan.map { it.rays })
    }

    @Test
    fun `freshness states and the worst wallet`() {
        val now = 10_000_000L
        val connected = snapshot(
            emptyList(),
            sync = mapOf(
                MAIN to WalletSync(refreshedAtMillis = now - 5_000, live = true),
                SAVINGS to WalletSync(refreshedAtMillis = now - 31_000),
                TRADING to WalletSync(refreshedAtMillis = now - 3 * 3_600_000L),
            ),
        )
        val freshness = { id: String -> PortfolioFreshness.of(connected.sync[id], true, now) }
        assertEquals(FreshnessKind.LIVE, freshness(MAIN).kind)
        assertEquals(FreshnessKind.LIVE, freshness(SAVINGS).kind)
        assertEquals(FreshnessKind.SAVED, freshness(TRADING).kind)
        assertEquals(FreshnessKind.SAVED, PortfolioFreshness.of(null, true, now).kind)
        assertNull(PortfolioFreshness.of(null, true, now).updatedAtMillis)
        val worst = PortfolioCalculator.worstFreshness(connected, connected.wallets.map { it.id }, now)!!
        assertEquals(FreshnessKind.SAVED, worst.kind)
        assertEquals(now - 3 * 3_600_000L, worst.updatedAtMillis)
        // Among live wallets the oldest age is shown.
        val live = PortfolioCalculator.worstFreshness(connected, listOf(MAIN, SAVINGS), now)!!
        assertEquals(FreshnessKind.LIVE, live.kind)
        assertEquals(now - 31_000, live.updatedAtMillis)

        val partial = connected.copy(sync = connected.sync + (SAVINGS to WalletSync(now, setOf(WalletNetworkId.MINAMOTO))))
        val worstPartial = PortfolioCalculator.worstFreshness(partial, partial.wallets.map { it.id }, now)!!
        assertEquals(FreshnessKind.PARTIAL, worstPartial.kind)
        assertEquals(setOf(WalletNetworkId.MINAMOTO), worstPartial.unreachable)

        val offline = connected.copy(connected = false)
        assertEquals(FreshnessKind.OFFLINE, PortfolioCalculator.worstFreshness(offline, listOf(MAIN), now)!!.kind)
    }

    @Test
    fun `a read stamped just after the dashboard's clock ticked is live, one from the clock's future is not`() {
        val now = 10_000_000L
        val justAfter = WalletSync(refreshedAtMillis = now + 400)
        assertEquals(FreshnessKind.LIVE, PortfolioFreshness.of(justAfter, true, now).kind)
        val clockWentBack = WalletSync(refreshedAtMillis = now + 3_600_000L)
        assertEquals(FreshnessKind.SAVED, PortfolioFreshness.of(clockWentBack, true, now).kind)
    }

    @Test
    fun `wallets are re-read at most once a minute`() {
        assertTrue(PortfolioFreshness.isDue(null, 1_000))
        assertTrue(!PortfolioFreshness.isDue(1_000, 30_000))
        assertTrue(PortfolioFreshness.isDue(1_000, 61_000))
        assertTrue(PortfolioFreshness.isDue(90_000, 1_000)) // the clock went back
    }

    @Test
    fun `where it lives lists every visible holding of one token, testnet last`() {
        val snap = snapshot(
            listOf(
                holding(MAIN, xor, "1000"),
                holding(MAIN, xor, "999", network = WalletNetworkId.TAIRA),
                holding(TRADING, xor, "3000", network = WalletNetworkId.MINAMOTO),
                holding(SAVINGS, xor, "1", hidden = true),
                holding(SAVINGS, eth, "1"),
            )
        )
        val ids = snap.wallets.map { it.id }
        val locations = PortfolioCalculator.tokenLocations(snap, ids, AssetHolder.XOR_ASSET_ID)!!
        val order = listOf(WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2, WalletNetworkId.TAIRA)
        assertEquals(order, locations.holdings.map { it.network })
        assertEquals(0, BigDecimal("4000").compareTo(locations.countedAmount))
        assertEquals(16.0, locations.countedFiat, 1e-9)
        assertEquals(16.0 / 2016.0, locations.share, 1e-12)
        assertEquals(listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA), locations.networks)
        assertNull(PortfolioCalculator.tokenLocations(snap, listOf(MAIN), PortfolioSlots.ETH_ID))
    }

    @Test
    fun `wallet summaries carry each wallet's own total and freshness`() {
        val now = 5_000_000L
        val snap = snapshot(
            listOf(holding(MAIN, eth, "1"), holding(SAVINGS, xor, "1000", network = WalletNetworkId.MINAMOTO)),
            sync = mapOf(MAIN to WalletSync(now, live = true), SAVINGS to WalletSync(now - 10_000)),
        )
        val summaries = PortfolioCalculator.walletSummaries(snap, now)
        assertEquals(listOf(MAIN, SAVINGS, TRADING), summaries.map { it.wallet.id })
        assertEquals(2000.0, summaries[0].totals.fiat, 1e-9)
        assertEquals(listOf(WalletNetworkId.MINAMOTO), summaries[1].networks)
        assertEquals(FreshnessKind.SAVED, summaries[2].freshness!!.kind)
    }
}
