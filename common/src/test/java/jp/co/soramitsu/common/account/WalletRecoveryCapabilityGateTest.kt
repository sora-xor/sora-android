package jp.co.soramitsu.common.account

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRecoveryCapabilityGateTest {

    @After
    fun resetGate() {
        WalletRecoveryCapabilityGate.enterNormal()
    }

    @Test
    fun `legacy recovery permits database reads but rejects every user mutation`() {
        WalletRecoveryCapabilityGate.enterBlocked()
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()

        WalletRecoveryCapabilityGate.requireDatabaseAccessAllowed()
        assertTrue(WalletRecoveryCapabilityGate.requiresWalletWriteGuards())
        assertTrue(
            runCatching {
                WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            }.isFailure
        )
        assertFalse(WalletRecoveryCapabilityGate.isMigrationRetryAuthorized())
    }

    @Test
    fun `only explicit authorization enables migration writes`() {
        WalletRecoveryCapabilityGate.enterBlocked()
        assertTrue(
            runCatching {
                WalletRecoveryCapabilityGate.requireMigrationWriteAllowed()
            }.isFailure
        )

        WalletRecoveryCapabilityGate.authorizeMigrationRetry()

        WalletRecoveryCapabilityGate.requireMigrationWriteAllowed()
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertTrue(
            runCatching {
                WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            }.isFailure
        )
    }
}
