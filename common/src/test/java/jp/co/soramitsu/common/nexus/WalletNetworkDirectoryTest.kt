package jp.co.soramitsu.common.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One wallet, networks as labels: names, tags and order come from one directory, and one
 * constant makes SORA Nexus the main network once it is live ([MainNetworkPolicy.PREFERRED]).
 */
class WalletNetworkDirectoryTest {
    /** This build: SORA2 and Taira are live, SORA Nexus production is not yet. */
    private val today = WalletNetworkDirectory.current

    /** After SORA Nexus production launches. */
    private val nexusLive = WalletNetworkDirectory { true }

    private val mnemonicWallet = setOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA)
    private val addresses = mapOf(
        WalletNetworkId.TAIRA to "taira-address",
        WalletNetworkId.MINAMOTO to "nexus-address",
        WalletNetworkId.SORA2 to "sora2-address",
    )

    @Test
    fun `SORA2 is the main network today`() {
        assertEquals(WalletNetworkId.SORA2, MainNetworkPolicy.PREFERRED)
        assertEquals(WalletNetworkId.SORA2, today.mainNetwork(mnemonicWallet))
        assertFalse(today.info(WalletNetworkId.MINAMOTO).isLive)
        assertTrue(today.info(WalletNetworkId.TAIRA).isLive)
    }

    @Test
    fun `SORA Nexus becomes main only once it is live`() {
        // Flipping the constant early cannot move users to a network that is not live.
        assertEquals(WalletNetworkId.SORA2, today.mainNetwork(mnemonicWallet, WalletNetworkId.MINAMOTO))
        assertEquals(WalletNetworkId.MINAMOTO, nexusLive.mainNetwork(mnemonicWallet, WalletNetworkId.MINAMOTO))
    }

    @Test
    fun `main network falls back to SORA2`() {
        // A testnet is never the main network.
        assertEquals(WalletNetworkId.SORA2, nexusLive.mainNetwork(mnemonicWallet, WalletNetworkId.TAIRA))
        // A raw-seed wallet has no SORA Nexus account.
        assertEquals(
            WalletNetworkId.SORA2,
            nexusLive.mainNetwork(setOf(WalletNetworkId.SORA2), WalletNetworkId.MINAMOTO),
        )
    }

    @Test
    fun `order puts the main network first and testnets last`() {
        val networks = listOf(WalletNetworkId.TAIRA, WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2, WalletNetworkId.TAIRA)
        assertEquals(
            listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA),
            today.ordered(networks, WalletNetworkId.SORA2),
        )
        assertEquals(
            listOf(WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2, WalletNetworkId.TAIRA),
            nexusLive.ordered(networks, WalletNetworkId.MINAMOTO),
        )
    }

    @Test
    fun `tags and row labels`() {
        assertEquals(WalletNetworkTag.MAIN, today.tag(WalletNetworkId.SORA2, WalletNetworkId.SORA2))
        assertEquals(WalletNetworkTag.COMING_SOON, today.tag(WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2))
        assertEquals(WalletNetworkTag.TESTNET, today.tag(WalletNetworkId.TAIRA, WalletNetworkId.SORA2))
        assertNull(today.rowLabel(WalletNetworkId.SORA2, WalletNetworkId.SORA2))
        assertEquals("Taira", today.rowLabel(WalletNetworkId.TAIRA, WalletNetworkId.SORA2))
        assertEquals("SORA Nexus", today.rowLabel(WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2))

        // With SORA Nexus as the main network, SORA2 rows carry their label.
        assertEquals(WalletNetworkTag.MAIN, nexusLive.tag(WalletNetworkId.MINAMOTO, WalletNetworkId.MINAMOTO))
        assertNull(nexusLive.tag(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO))
        assertNull(nexusLive.rowLabel(WalletNetworkId.MINAMOTO, WalletNetworkId.MINAMOTO))
        assertEquals("SORA2", nexusLive.rowLabel(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO))
    }

    @Test
    fun `receive offers every address main first`() {
        val choices = WalletNetworkChoices.make(
            action = WalletNetworkAction.RECEIVE,
            addresses = addresses,
            balances = mapOf(WalletNetworkId.SORA2 to "120.5", WalletNetworkId.TAIRA to "50"),
            canSend = { false },
            directory = today,
        )
        assertEquals(listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA), choices.map { it.id })
        assertEquals(listOf(WalletNetworkTag.MAIN, WalletNetworkTag.COMING_SOON, WalletNetworkTag.TESTNET), choices.map { it.tag })
        assertTrue(choices.all { it.isAvailable })
        assertEquals("120.5", choices.first().balance)
        assertEquals("sora2-address", choices.first().address)
        // SORA2 and Taira are both live: the wallet asks.
        assertNull(WalletNetworkChoices.onlyChoice(choices))
    }

    @Test
    fun `receive goes straight to the only live network`() {
        // Taira switched off: SORA Nexus keeps its home row, Receive opens SORA2.
        val choices = WalletNetworkChoices.make(
            action = WalletNetworkAction.RECEIVE,
            addresses = addresses - WalletNetworkId.TAIRA,
            balances = emptyMap(),
            canSend = { true },
            directory = today,
        )
        assertEquals(WalletNetworkId.SORA2, WalletNetworkChoices.onlyChoice(choices)?.id)
    }

    @Test
    fun `send says why a network cannot be used`() {
        val paused = WalletNetworkChoices.make(
            action = WalletNetworkAction.SEND,
            addresses = addresses,
            balances = emptyMap(),
            canSend = { it == WalletNetworkId.SORA2 },
            directory = today,
        )
        assertEquals(
            listOf(null, WalletNetworkUnavailable.NOT_LIVE, WalletNetworkUnavailable.PAUSED),
            paused.map { it.unavailable },
        )
        assertEquals(WalletNetworkId.SORA2, WalletNetworkChoices.onlyChoice(paused)?.id)

        val open = WalletNetworkChoices.make(
            action = WalletNetworkAction.SEND,
            addresses = addresses,
            balances = emptyMap(),
            canSend = { true },
            directory = today,
        )
        assertEquals(listOf(true, false, true), open.map { it.isAvailable })
        assertNull(WalletNetworkChoices.onlyChoice(open))
    }

    @Test
    fun `SORA Nexus leads the choices once it is main`() {
        val choices = WalletNetworkChoices.make(
            action = WalletNetworkAction.SEND,
            addresses = addresses,
            balances = emptyMap(),
            canSend = { true },
            directory = nexusLive,
            preferred = WalletNetworkId.MINAMOTO,
        )
        assertEquals(listOf(WalletNetworkId.MINAMOTO, WalletNetworkId.SORA2, WalletNetworkId.TAIRA), choices.map { it.id })
        assertEquals(listOf(WalletNetworkTag.MAIN, null, WalletNetworkTag.TESTNET), choices.map { it.tag })
        assertTrue(choices.all { it.isAvailable })
    }
}
