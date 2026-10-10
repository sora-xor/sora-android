package jp.co.soramitsu.common.presentation.compose.neon.safemode

import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design spec 9.1-5: the 10 s and 60 s rules; it never fails on its own. */
class ProgressPromptTest {

    @After
    fun tearDown() {
        WalletRecoveryCapabilityGate.enterNormal()
    }

    @Test
    fun `quiet below ten seconds, still working from ten, help from sixty`() {
        assertEquals(ProgressPrompt.NONE, progressPrompt(0))
        assertEquals(ProgressPrompt.NONE, progressPrompt(9_999))
        assertEquals(ProgressPrompt.STILL_WORKING, progressPrompt(10_000))
        assertEquals(ProgressPrompt.STILL_WORKING, progressPrompt(59_999))
        assertEquals(ProgressPrompt.OFFER_HELP, progressPrompt(60_000))
        assertEquals(ProgressPrompt.OFFER_HELP, progressPrompt(10 * 60_000))
    }

    @Test
    fun `keep waiting hides the help for another minute`() {
        assertEquals(ProgressPrompt.STILL_WORKING, progressPrompt(70_000, keepWaitingAtMillis = 65_000))
        assertEquals(ProgressPrompt.STILL_WORKING, progressPrompt(124_999, keepWaitingAtMillis = 65_000))
        assertEquals(ProgressPrompt.OFFER_HELP, progressPrompt(125_000, keepWaitingAtMillis = 65_000))
    }

    @Test
    fun `read-only display follows the signing gate exactly`() {
        val transitions = listOf<() -> Unit>(
            { WalletRecoveryCapabilityGate.enterNormal() },
            { WalletRecoveryCapabilityGate.enterBlocked() },
            { WalletRecoveryCapabilityGate.enterInspectedRecovery() },
            {
                WalletRecoveryCapabilityGate.enterBlocked()
                WalletRecoveryCapabilityGate.enterLegacyReadOnly()
            },
            {
                WalletRecoveryCapabilityGate.enterBlocked()
                WalletRecoveryCapabilityGate.authorizeMigrationRetry()
            },
        )
        val seen = mutableSetOf<WalletRecoveryCapabilityGate.Mode>()
        transitions.forEach { enter ->
            enter()
            val mode = WalletRecoveryCapabilityGate.mode()
            seen += mode
            assertEquals(mode, WalletRecoveryCapabilityGate.modeFlow.value)
            val signingRefused = runCatching { WalletRecoveryCapabilityGate.requireUserMutationAllowed() }.isFailure
            assertEquals(signingRefused, WalletRecoveryCapabilityGate.isUserMutationBlocked())
        }
        assertEquals(WalletRecoveryCapabilityGate.Mode.entries.toSet(), seen)
        WalletRecoveryCapabilityGate.enterNormal()
        assertFalse(WalletRecoveryCapabilityGate.isUserMutationBlocked())
        assertTrue(WalletRecoveryCapabilityGate.isUserMutationBlocked(WalletRecoveryCapabilityGate.Mode.BLOCKED))
    }
}
