package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.WalletNetworkId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NexusFinalityCheckpointTest {

    @Test
    fun `checkpoint is bound to exact wallet network and chain uuid`() {
        val checkpoint = NexusFinalityCheckpoint(
            networkId = WalletNetworkId.MINAMOTO,
            chainId = NexusNetworks.minamoto.chainId,
            finalizedBlockHeight = 10,
            finalizedBlockHash = VALID_BLOCK_HASH,
        )

        assertEquals(10L, checkpoint.requireFor(NexusNetworks.minamoto))
        assertEquals(
            "NEXUS_FINALITY_NETWORK_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                checkpoint.copy(networkId = WalletNetworkId.TAIRA)
                    .requireFor(NexusNetworks.minamoto)
            }.message,
        )
        assertEquals(
            "NEXUS_FINALITY_NETWORK_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                checkpoint.copy(chainId = NexusNetworks.taira.chainId)
                    .requireFor(NexusNetworks.minamoto)
            }.message,
        )
    }

    @Test
    fun `zero finalized checkpoint is never authoritative`() {
        val checkpoint = NexusFinalityCheckpoint(
            networkId = WalletNetworkId.MINAMOTO,
            chainId = NexusNetworks.minamoto.chainId,
            finalizedBlockHeight = 0,
            finalizedBlockHash = VALID_BLOCK_HASH,
        )

        assertEquals(
            "NEXUS_FINALITY_CHECKPOINT_INVALID",
            assertThrows(IllegalStateException::class.java) {
                checkpoint.requireFor(NexusNetworks.minamoto)
            }.message,
        )
    }

    @Test
    fun `height without a canonical nonzero finalized block hash is never authoritative`() {
        val checkpoint = NexusFinalityCheckpoint(
            networkId = WalletNetworkId.MINAMOTO,
            chainId = NexusNetworks.minamoto.chainId,
            finalizedBlockHeight = 10,
            finalizedBlockHash = VALID_BLOCK_HASH,
        )

        listOf(
            "",
            "0x" + "ab".repeat(32),
            "0x" + "00".repeat(32),
            "AB".repeat(32),
            "00".repeat(32),
            "hash:" + "AB".repeat(32) + "#0000",
        ).forEach { invalidHash ->
            assertEquals(
                "NEXUS_FINALITY_CHECKPOINT_INVALID",
                assertThrows(IllegalStateException::class.java) {
                    checkpoint.copy(finalizedBlockHash = invalidHash)
                        .requireFor(NexusNetworks.minamoto)
                }.message,
            )
        }
    }

    @Test
    fun `archived pre v2 Taira chain cannot satisfy current Taira finality`() {
        val checkpoint = NexusFinalityCheckpoint(
            networkId = WalletNetworkId.TAIRA,
            chainId = "809574f5-fee7-5e69-bfcf-52451e42d50f",
            finalizedBlockHeight = 10,
            finalizedBlockHash = VALID_BLOCK_HASH,
        )

        assertEquals(
            "NEXUS_FINALITY_NETWORK_MISMATCH",
            assertThrows(IllegalStateException::class.java) {
                checkpoint.requireFor(NexusNetworks.taira)
            }.message,
        )
    }

    private companion object {
        const val VALID_BLOCK_HASH =
            "abababababababababababababababababababababababababababababababab"
    }
}
