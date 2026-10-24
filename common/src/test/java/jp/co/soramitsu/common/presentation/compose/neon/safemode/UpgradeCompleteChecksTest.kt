package jp.co.soramitsu.common.presentation.compose.neon.safemode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The upgrade sheet asks for a new export of a several-account JSON backup file only from people
 * such a file can concern.
 */
class UpgradeCompleteChecksTest {

    private fun checks(accountCount: Int) = UpgradeCompleteChecks(
        accountsOnDevice = true,
        protectedByPin = true,
        balancesLoaded = true,
        needsBackup = false,
        accountCount = accountCount,
        signingAccountCount = accountCount,
    )

    @Test
    fun `people with several accounts are asked to export a batch backup file again`() {
        assertTrue(checks(accountCount = 2).mayHoldEmptyBatchExport)
        assertTrue(checks(accountCount = 7).mayHoldEmptyBatchExport)
    }

    @Test
    fun `one account cannot have made a batch export so the note is not shown`() {
        assertFalse(checks(accountCount = 1).mayHoldEmptyBatchExport)
        // The count is not known: nothing is claimed.
        assertFalse(checks(accountCount = 0).mayHoldEmptyBatchExport)
    }
}
