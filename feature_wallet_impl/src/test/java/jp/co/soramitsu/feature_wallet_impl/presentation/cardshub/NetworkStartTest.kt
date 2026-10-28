package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoices
import jp.co.soramitsu.common.nexus.WalletNetworkDirectory
import jp.co.soramitsu.common.nexus.WalletNetworkId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStartTest {

    // SORA2 and Taira are live in this build; SORA Nexus only has an address.
    private val directory = WalletNetworkDirectory { it != WalletNetworkId.MINAMOTO }

    private fun choices(
        action: WalletNetworkAction,
        networks: Set<WalletNetworkId>,
        canSend: (WalletNetworkId) -> Boolean = { it != WalletNetworkId.MINAMOTO },
    ) = WalletNetworkChoices.make(
        action = action,
        addresses = networks.associateWith { "${it.wireId}-address" },
        balances = emptyMap(),
        canSend = canSend,
        directory = directory,
    )

    @Test
    fun `with no choice or only SORA2, Send and Receive stay the dashboard's own`() {
        assertEquals(NetworkStart.Dashboard, networkStart(emptyList()))
        for (action in WalletNetworkAction.entries) {
            val sora2AndNexus = choices(action, setOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO))
            assertEquals(NetworkStart.Dashboard, networkStart(sora2AndNexus))
        }
    }

    @Test
    fun `two live networks ask which one, main network first`() {
        for (action in WalletNetworkAction.entries) {
            val all = choices(action, setOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA))
            val start = networkStart(all)
            assertTrue(start is NetworkStart.Ask)
            assertEquals(
                listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA),
                (start as NetworkStart.Ask).choices.map { it.id },
            )
        }
    }

    @Test
    fun `the one usable network other than SORA2 opens straight away`() {
        val onlyTaira = choices(
            WalletNetworkAction.SEND,
            setOf(WalletNetworkId.SORA2, WalletNetworkId.TAIRA),
            canSend = { it == WalletNetworkId.TAIRA },
        )
        val start = networkStart(onlyTaira)
        assertTrue(start is NetworkStart.Open)
        assertEquals(WalletNetworkId.TAIRA, (start as NetworkStart.Open).choice.id)
    }
}
