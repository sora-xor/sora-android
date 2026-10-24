package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.MAIN
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.SAVINGS
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.TRADING
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.holding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.snapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.valToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.xor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioReadinessTest {

    @Test
    fun `SORA2 is swap-ready, Minamoto has no route, Taira is test`() {
        assertEquals(Readiness.READY, PortfolioReadiness.readiness(holding(MAIN, xor, "1")))
        assertEquals(Readiness.NONE, PortfolioReadiness.readiness(holding(MAIN, xor, "1", network = WalletNetworkId.MINAMOTO)))
        assertEquals(Readiness.TEST, PortfolioReadiness.readiness(holding(MAIN, xor, "1", network = WalletNetworkId.TAIRA)))
        assertEquals(Readiness.NONE, PortfolioReadiness.readiness(holding(MAIN, xor, "1"), swapsAvailable = false))
    }

    @Test
    fun `swap needs the token on SORA2 and something transferable`() {
        assertNull(PortfolioReadiness.swapBlock(holding(MAIN, xor, "10", transferable = "1")))
        assertEquals(SwapBlock.NOTHING_TRANSFERABLE, PortfolioReadiness.swapBlock(holding(MAIN, xor, "10", transferable = "0")))
        assertEquals(SwapBlock.NOT_ON_SORA2, PortfolioReadiness.swapBlock(holding(MAIN, xor, "10", network = WalletNetworkId.MINAMOTO)))
        assertEquals(SwapBlock.TESTNET, PortfolioReadiness.swapBlock(holding(MAIN, xor, "10", network = WalletNetworkId.TAIRA)))
    }

    @Test
    fun `swap targets are the popular Polkaswap tokens without the pay token`() {
        assertEquals(
            listOf(PortfolioSlots.VAL_ID, PortfolioSlots.PSWAP_ID, PortfolioSlots.XSTUSD_ID),
            PortfolioReadiness.swapTargets(AssetHolder.XOR_ASSET_ID),
        )
        assertEquals(4, PortfolioReadiness.swapTargets(PortfolioSlots.ETH_ID).size)
    }

    @Test
    fun `move goes to the user's other wallets that have an account on the same network`() {
        val sora2 = holding(MAIN, valToken, "10")
        val snap = snapshot(
            listOf(sora2),
            addresses = mapOf(
                MAIN to mapOf(WalletNetworkId.SORA2 to MAIN, WalletNetworkId.TAIRA to "main-taira"),
                SAVINGS to mapOf(WalletNetworkId.SORA2 to SAVINGS),
                TRADING to mapOf(WalletNetworkId.SORA2 to TRADING, WalletNetworkId.TAIRA to "trading-taira"),
            ),
        )
        assertEquals(listOf(SAVINGS, TRADING), PortfolioReadiness.moveTargets(snap, sora2).map { it.id })
        val taira = holding(MAIN, xor, "5", network = WalletNetworkId.TAIRA)
        assertEquals(listOf(TRADING), PortfolioReadiness.moveTargets(snap, taira).map { it.id })
        assertNull(PortfolioReadiness.moveBlock(snap, sora2))
    }

    @Test
    fun `move explains why it cannot start`() {
        val single = snapshot(emptyList(), wallets = listOf(MAIN))
        assertEquals(MoveBlock.NO_OTHER_WALLET, PortfolioReadiness.moveBlock(single, holding(MAIN, xor, "1")))
        val snap = snapshot(emptyList())
        assertEquals(
            MoveBlock.SEND_UNAVAILABLE,
            PortfolioReadiness.moveBlock(snap, holding(MAIN, xor, "1", network = WalletNetworkId.MINAMOTO, sendAvailable = false)),
        )
        assertEquals(MoveBlock.NOTHING_TRANSFERABLE, PortfolioReadiness.moveBlock(snap, holding(MAIN, xor, "1", transferable = "0")))
    }

    @Test
    fun `max is the transferable balance`() {
        assertEquals(0, java.math.BigDecimal("7").compareTo(PortfolioReadiness.maxAmount(holding(MAIN, xor, "10", transferable = "7"))))
    }
}
