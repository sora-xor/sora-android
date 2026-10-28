package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import jp.co.soramitsu.common.nexus.WalletNetworkDirectory
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.nexus.WalletNetworkTag
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.networkName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioNetworksTest {

    @Test
    fun `the portfolio orders networks the way the wallet's network directory does`() {
        val directory = WalletNetworkDirectory.current
        val all = WalletNetworkId.entries
        assertEquals(directory.ordered(all, directory.mainNetwork(all.toSet())), PORTFOLIO_NETWORKS)
        assertEquals(WalletNetworkId.SORA2, PORTFOLIO_NETWORKS.first())
        assertEquals(WalletNetworkId.TAIRA, PORTFOLIO_NETWORKS.last())
    }

    @Test
    fun `only the directory's testnets are testnets in the portfolio`() {
        assertFalse(WalletNetworkId.SORA2.isTestnet)
        assertFalse(WalletNetworkId.MINAMOTO.isTestnet)
        assertTrue(WalletNetworkId.TAIRA.isTestnet)
    }

    @Test
    fun `network rows carry the directory's Main and Testnet tags, never Coming soon`() {
        assertEquals(WalletNetworkTag.MAIN, WalletNetworkId.SORA2.portfolioTag)
        assertNull(WalletNetworkId.MINAMOTO.portfolioTag)
        assertEquals(WalletNetworkTag.TESTNET, WalletNetworkId.TAIRA.portfolioTag)
    }

    @Test
    fun `network labels are the directory's names`() {
        assertEquals("SORA2", networkName(WalletNetworkId.SORA2))
        assertEquals("SORA Nexus", networkName(WalletNetworkId.MINAMOTO))
        assertEquals("Taira", networkName(WalletNetworkId.TAIRA))
    }
}
