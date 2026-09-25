package jp.co.soramitsu.common.presentation

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WalletDisplayTest {
    @Test fun `send result description agrees with its finality`() {
        assertEquals(jp.co.soramitsu.common.R.string.wallet_transfer_complete, walletTransactionDescription("COMMITTED"))
        assertEquals(jp.co.soramitsu.common.R.string.wallet_transfer_pending, walletTransactionDescription("SUBMITTED"))
        assertEquals(jp.co.soramitsu.common.R.string.wallet_submission_failed, walletTransactionDescription("REJECTED"))
        assertEquals(jp.co.soramitsu.common.R.string.wallet_submission_uncertain, walletTransactionDescription("COMMITTED", true))
        assertEquals(jp.co.soramitsu.common.R.string.wallet_submission_uncertain, walletTransactionDescription("unknown"))
    }

    @Test fun `ambiguous and unknown statuses cannot look completed`() {
        assertEquals(WalletTransactionStatus.CHECKING, walletTransactionStatus("COMMITTED", true))
        assertEquals(WalletTransactionStatus.CHECKING, walletTransactionStatus("NEW_SERVER_STATE"))
        assertEquals(WalletTransactionStatus.CONFIRMED, walletTransactionStatus("finalized"))
        assertEquals(WalletTransactionStatus.FAILED, walletTransactionStatus("REJECTED"))
    }
    @Test fun `recovery and actionable validation issues are distinguished`() {
        assertEquals(WalletIssue.RECOVERY, walletIssue("NEXUS_PENDING_RECOVERY_REQUIRED"))
        assertEquals(WalletIssue.ADDRESS, walletIssue("NEXUS_INVALID_RECIPIENT"))
        assertEquals(WalletIssue.BALANCE, walletIssue("NEXUS_INSUFFICIENT_BALANCE"))
        assertEquals(WalletIssue.QUOTE, walletIssue("POLKAMARKT_STALE_QUOTE"))
        assertEquals(WalletIssue.CONNECTION, walletIssue("UNKNOWN_ERROR"))
    }
    @Test fun `date includes local time and changes with timezone`() {
        val utc = walletDateTime(1_783_209_600_000, Locale.US, ZoneId.of("UTC"))
        val brisbane = walletDateTime(1_783_209_600_000, Locale.US, ZoneId.of("Australia/Brisbane"))
        assertNotEquals(utc, brisbane)
        assertTrue(utc.contains("2026"))
        assertFalse(utc.contains("1783209600000"))
    }
}
