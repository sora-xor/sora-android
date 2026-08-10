package jp.co.soramitsu.common.nexus

import jp.co.soramitsu.common.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TairaDeploymentBindingTest {

    @Test
    fun `production variant contains the exact admitted deployment projection`() {
        if (BuildConfig.FLAVOR != "production") return

        val binding = requireNotNull(TairaDeployment.binding) {
            "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED"
        }
        assertEquals(BuildConfig.TAIRA_DEPLOYMENT_MANIFEST_SHA256, binding.manifestSha256)
        assertEquals(BuildConfig.TAIRA_CURRENT_CHAIN_ID, binding.currentChainId)
        assertEquals(BuildConfig.TAIRA_CURRENT_EPOCH.toLong(), binding.currentEpoch)
        assertEquals(BuildConfig.TAIRA_CURRENT_GENESIS_SHA256, binding.currentGenesisSha256)
        assertEquals(BuildConfig.TAIRA_CURRENT_TORII_BASE_URL, binding.currentToriiBaseUrl)
        assertEquals(
            BuildConfig.TAIRA_CURRENT_PUBLIC_MCP_ENDPOINT,
            binding.currentPublicMcpEndpoint,
        )
        assertEquals(BuildConfig.TAIRA_RETIRED_CHAIN_ID, binding.retiredChainId)
        assertEquals(
            BuildConfig.TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256,
            binding.operatorKeySha256,
        )
        assertEquals(
            BuildConfig.TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256,
            binding.reviewerKeySha256,
        )
    }

    @Test
    fun `accepts either signed current epoch mapping`() {
        val forward = binding(
            currentChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_B,
            retiredChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_A,
        )
        val reverse = binding(
            currentChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_A,
            retiredChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_B,
        )

        assertEquals(WalletNetworkChainIdentity.TAIRA_EPOCH_B, forward?.currentChainId)
        assertEquals(WalletNetworkChainIdentity.TAIRA_EPOCH_A, reverse?.currentChainId)
        assertEquals(WalletNetworkChainIdentity.TAIRA_EPOCH_B, reverse?.retiredChainId)
        assertEquals("https://node-2.taira.sora.org/v1/mcp", forward?.currentPublicMcpEndpoint)
        assertEquals("https://node-2.taira.sora.org/v1/mcp", reverse?.currentPublicMcpEndpoint)
        assertEquals("taira:$MANIFEST:", forward?.pendingJournalPrefix)
        assertEquals("taira:$MANIFEST:", reverse?.pendingJournalPrefix)
        assertTrue(forward?.ownsPendingJournal("taira:$MANIFEST:journal-a") == true)
        assertTrue(reverse?.ownsPendingJournal("taira:$MANIFEST:journal-b") == true)
        assertTrue(forward?.ownsPendingJournal("journal-without-manifest-binding") == false)
    }

    @Test
    fun `partial drifted or self reviewed build projections stay unqualified`() {
        assertNull(binding(manifestSha256 = ""))
        assertNull(binding(currentChainId = "00000000-0000-0000-0000-000000000369"))
        assertNull(
            binding(
                currentChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_B,
                retiredChainId = WalletNetworkChainIdentity.TAIRA_EPOCH_B,
            )
        )
        assertNull(binding(currentEpoch = "1", retiredEpoch = "2"))
        assertNull(binding(currentI105Discriminant = "368"))
        assertNull(binding(currentToriiBaseUrl = "https://taira.sora.org"))
        assertNull(binding(currentPublicMcpEndpoint = "https://node-2.taira.sora.org/v1/mcp/"))
        assertNull(binding(reviewerKeySha256 = OPERATOR_KEY))
        assertTrue(
            TairaDeployment.binding?.let { it.manifestSha256.length == 64 } ?: true
        )
        if (TairaDeployment.binding == null) {
            assertEquals(
                "00000000-0000-0000-0000-000000000000",
                NexusNetworks.taira.chainId,
            )
            assertEquals(
                "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED",
                runCatching {
                    WalletNetworkChainIdentity.require(WalletNetworkId.TAIRA)
                }.exceptionOrNull()?.message,
            )
        }
    }

    @Suppress("LongParameterList")
    private fun binding(
        manifestSha256: String = MANIFEST,
        manifestSequenceNumber: String = "17",
        currentEpoch: String = "2",
        currentChainId: String = WalletNetworkChainIdentity.TAIRA_EPOCH_B,
        currentGenesisSha256: String = CURRENT_GENESIS,
        currentI105Discriminant: String = "369",
        currentToriiBaseUrl: String = "https://node-2.taira.sora.org",
        currentPublicMcpEndpoint: String = "https://node-2.taira.sora.org/v1/mcp",
        currentExplorerBaseUrl: String = "https://taira-explorer.sora.org",
        retiredEpoch: String = "1",
        retiredChainId: String = WalletNetworkChainIdentity.TAIRA_EPOCH_A,
        retiredGenesisSha256: String = RETIRED_GENESIS,
        operatorKeySha256: String = OPERATOR_KEY,
        reviewerKeySha256: String = REVIEWER_KEY,
    ): TairaDeploymentBinding? = TairaDeploymentBinding.from(
        manifestSha256 = manifestSha256,
        manifestSequenceNumber = manifestSequenceNumber,
        currentEpoch = currentEpoch,
        currentChainId = currentChainId,
        currentGenesisSha256 = currentGenesisSha256,
        currentI105Discriminant = currentI105Discriminant,
        currentToriiBaseUrl = currentToriiBaseUrl,
        currentPublicMcpEndpoint = currentPublicMcpEndpoint,
        currentExplorerBaseUrl = currentExplorerBaseUrl,
        retiredEpoch = retiredEpoch,
        retiredChainId = retiredChainId,
        retiredGenesisSha256 = retiredGenesisSha256,
        operatorKeySha256 = operatorKeySha256,
        reviewerKeySha256 = reviewerKeySha256,
    )

    private companion object {
        const val MANIFEST = "1b36b1d9077ff795e024beb0728d052eda83f0481cbfaf81a9e0d197497ec8fb"
        const val CURRENT_GENESIS = "2b36b1d9077ff795e024beb0728d052eda83f0481cbfaf81a9e0d197497ec8fb"
        const val RETIRED_GENESIS = "3b36b1d9077ff795e024beb0728d052eda83f0481cbfaf81a9e0d197497ec8fb"
        const val OPERATOR_KEY = "4b36b1d9077ff795e024beb0728d052eda83f0481cbfaf81a9e0d197497ec8fb"
        const val REVIEWER_KEY = "5b36b1d9077ff795e024beb0728d052eda83f0481cbfaf81a9e0d197497ec8fb"
    }
}
