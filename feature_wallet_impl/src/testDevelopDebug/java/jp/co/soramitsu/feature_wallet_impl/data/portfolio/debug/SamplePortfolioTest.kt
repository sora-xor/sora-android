package jp.co.soramitsu.feature_wallet_impl.data.portfolio.debug

import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.Exclusion
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePortfolioTest {

    private val now = 100_000_000L

    private fun sample(mode: SampleMode, freshness: SampleFreshness): PortfolioSnapshot {
        val reads = if (mode == SampleMode.EMPTY) emptyMap() else SamplePortfolio.initialReads(freshness, now)
        return SamplePortfolio.snapshot(mode, freshness, SamplePortfolio.MAIN, reads, now) { "icon:$it" }
    }

    private fun worst(snapshot: PortfolioSnapshot): FreshnessKind? {
        val ids = snapshot.wallets.map { it.id }
        return PortfolioCalculator.worstFreshness(snapshot, ids, now)?.kind
    }

    @Test
    fun `the sample has three wallets on SORA2, Minamoto and Taira with priced and unpriced assets`() {
        val snap = sample(SampleMode.FULL, SampleFreshness.LIVE)
        assertEquals(3, snap.wallets.size)
        val networks = snap.holdings.map { it.network }.toSet()
        assertEquals(setOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA), networks)
        val exclusions = snap.holdings.mapNotNull(PortfolioCalculator::exclusion).toSet()
        assertEquals(setOf(Exclusion.HIDDEN, Exclusion.UNPRICED, Exclusion.TESTNET), exclusions)
        assertTrue(snap.holdings.any { PortfolioCalculator.countedFiat(it) != null })
        val all = PortfolioCalculator.scopeTotals(snap, snap.wallets.map { it.id })
        assertEquals(12_019.942, all.fiat, 1e-6)
        assertEquals(FreshnessKind.LIVE, worst(snap))
    }

    @Test
    fun `the sample shows every freshness state and the empty state`() {
        assertEquals(FreshnessKind.SAVED, worst(sample(SampleMode.FULL, SampleFreshness.SAVED)))
        assertEquals(FreshnessKind.PARTIAL, worst(sample(SampleMode.FULL, SampleFreshness.PARTIAL)))
        assertEquals(FreshnessKind.OFFLINE, worst(sample(SampleMode.FULL, SampleFreshness.OFFLINE)))
        val empty = sample(SampleMode.EMPTY, SampleFreshness.LIVE)
        assertEquals(1, empty.wallets.size)
        assertTrue(empty.holdings.isEmpty())
    }

    @Test
    fun `switching wallets in the sample never touches real accounts`() = runTest {
        val source = SamplePortfolioSource(SampleMode.FULL, SampleFreshness.LIVE, { it }, { now })
        source.select(SamplePortfolio.SAVINGS)
        assertEquals(SamplePortfolio.SAVINGS, source.observe().first().selectedWallet?.id)
        assertTrue(source.isSample)
    }
}
