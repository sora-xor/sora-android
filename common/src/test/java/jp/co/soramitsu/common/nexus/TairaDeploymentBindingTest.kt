package jp.co.soramitsu.common.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TairaTestnetContractTest {
    @Test
    fun `first release exposes one canonical public contract`() {
        assertEquals(
            "taira:${TairaTestnetContract.CONTRACT_SHA256}:",
            TairaTestnetContract.PENDING_JOURNAL_PREFIX,
        )
        assertTrue(
            "${TairaTestnetContract.PENDING_JOURNAL_PREFIX}pending"
                .startsWith(TairaTestnetContract.PENDING_JOURNAL_PREFIX)
        )
    }

    @Test
    fun `taira is always admitted with the immutable contract`() {
        assertSame(NexusNetworks.taira, NexusNetworks.require(WalletNetworkId.TAIRA))
        assertEquals(TairaTestnetContract.CHAIN_ID, WalletNetworkChainIdentity.TAIRA)
        assertEquals(TairaTestnetContract.CHAIN_ID, NexusNetworks.taira.chainId)
        assertEquals(TairaTestnetContract.TORII_ROOT, NexusNetworks.taira.toriiBaseUrl)
        assertEquals(TairaTestnetContract.TORII_ROOT, NexusNetworks.taira.explorerBaseUrl)
        assertEquals(TairaTestnetContract.MCP_ENDPOINT, NexusToriiRoutes.mcp(NexusNetworks.taira))
    }

    @Test
    fun `forged old epoch or alternate root cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusNetwork(
                derivationProfile = NexusDerivationProfiles.taira,
                displayName = "forged",
                chainId = "809574f5-fee7-5e69-bfcf-52451e42d50f",
                toriiBaseUrl = TairaTestnetContract.TORII_ROOT,
                explorerBaseUrl = TairaTestnetContract.TORII_ROOT,
                isTestnet = true,
                enabledByDefault = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusNetwork(
                derivationProfile = NexusDerivationProfiles.taira,
                displayName = "forged",
                chainId = TairaTestnetContract.CHAIN_ID,
                toriiBaseUrl = "https://alternate.example.org",
                explorerBaseUrl = "https://alternate.example.org",
                isTestnet = true,
                enabledByDefault = true,
            )
        }
    }
}
