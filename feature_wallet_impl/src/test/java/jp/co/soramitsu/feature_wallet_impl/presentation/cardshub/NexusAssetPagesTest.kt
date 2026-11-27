package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPendingTransaction
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPreparedSend
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendResult
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the SORA2-styled XOR pages of SORA Nexus and Taira show, before anything is drawn. */
class NexusAssetPagesTest {

    private val owner = "owner-address"
    private val peer = "peer-address"
    private val numbers = NumbersFormatter()
    private val nbsp = ' '

    private fun balance(
        pending: List<NexusPendingTransaction> = emptyList(),
        confirmed: List<NexusTransferHistoryItem> = emptyList(),
    ) = NexusPortfolioBalance(
        walletId = "wallet",
        networkId = WalletNetworkId.TAIRA,
        networkName = "Taira Testnet",
        isTestnet = true,
        address = owner,
        xorQuantity = "25000",
        assetDefinitionId = "xor",
        explorerBaseUrl = "https://example.invalid",
        sendAvailable = true,
        pendingTransactions = pending,
        confirmedTransfers = confirmed,
        historyErrorCode = null,
        errorCode = null,
    )

    private fun pending(hash: String?, state: String = "SUBMITTED", ambiguous: Boolean = false) =
        NexusPendingTransaction("local-$hash", hash, "xor", "1.5", peer, state, ambiguous, createdAtMillis = 42L)

    @Test fun `XOR amounts are written as SORA2 writes them, without rounding`() {
        assertEquals("25${nbsp}000 XOR", numbers.nexusXor("25000"))
        assertEquals("0.000000001 XOR", numbers.nexusXor("0.000000001"))
        assertEquals("1${nbsp}234.567890123 XOR", numbers.nexusXor("1234.567890123"))
        // What is not a number is shown as it came.
        assertEquals("n/a XOR", numbers.nexusXor("n/a"))
        assertTrue("2.5".isPositiveQuantity())
        assertFalse("0".isPositiveQuantity())
        assertFalse("".isPositiveQuantity())
    }

    @Test fun `the amount field's value becomes the draft's amount, and a negative one none`() {
        assertEquals("12.50", BigDecimal("12.50").toNexusAmount())
        assertEquals("0", BigDecimal.ZERO.toNexusAmount())
        assertEquals("", BigDecimal("-3").toNexusAmount())
    }

    @Test fun `an amount is above the balance only when both are known numbers`() {
        assertTrue(isAboveBalance("1", "0"))
        assertTrue(isAboveBalance("25000.000000001", "25000"))
        assertFalse(isAboveBalance("25000", "25000"))
        assertFalse(isAboveBalance("12.5", "25000"))
        // A balance not read yet, or nothing typed: the network's own check decides.
        assertFalse(isAboveBalance("1", null))
        assertFalse(isAboveBalance("", "0"))
        assertFalse(isAboveBalance("1", "n/a"))
    }

    @Test fun `statuses read as the SORA2 rows read them`() {
        assertEquals(TransactionStatus.COMMITTED, nexusTransactionStatus("CONFIRMED"))
        assertEquals(TransactionStatus.REJECTED, nexusTransactionStatus("REJECTED"))
        assertEquals(TransactionStatus.PENDING, nexusTransactionStatus("SUBMITTED"))
        assertEquals(TransactionStatus.PENDING, nexusTransactionStatus("CONFIRMED", ambiguous = true))
        assertEquals(TransactionStatus.PENDING, nexusTransactionStatus("SIGNED"))
    }

    @Test fun `recent activity lists checked transfers first, then history, each transfer once`() {
        val sent = NexusTransferHistoryItem("aa", 2L, "2.5", owner, peer)
        val received = NexusTransferHistoryItem("bb", 1L, "3.75", peer, owner)
        val events = nexusActivityEvents(
            balance(pending = listOf(pending("cc"), pending("aa"), pending(null, "PENDING")), confirmed = listOf(sent, received)),
            tokenIcon = "icon",
            formatXor = numbers::nexusXor,
        )
        assertEquals(listOf("cc", "local-null", "aa", "bb"), events.map { (it as EventUiModel.EventTxUiModel).txHash })
        val checking = events[0] as EventUiModel.EventTxUiModel.EventTransferOutUiModel
        assertEquals(peer, checking.peerAddress)
        assertEquals("1.5 XOR", checking.amountFormatted)
        assertEquals(TransactionStatus.PENDING, checking.status)
        assertEquals(42L, checking.timestamp)
        val out = events[2] as EventUiModel.EventTxUiModel.EventTransferOutUiModel
        assertEquals(peer, out.peerAddress)
        assertEquals(TransactionStatus.COMMITTED, out.status)
        val incoming = events[3] as EventUiModel.EventTxUiModel.EventTransferInUiModel
        assertEquals(peer, incoming.peerAddress)
        assertEquals("+3.75 XOR", incoming.amountFormatted)
        assertEquals("icon", incoming.tokenIcon)
    }

    @Test fun `a transfer's page shows the history's transfer, or the transfer being checked`() {
        val received = NexusTransferHistoryItem("bb", 7L, "3.75", peer, owner)
        val current = balance(pending = listOf(pending("cc", "REJECTED"), pending(null, ambiguous = true)), confirmed = listOf(received))

        val confirmed = nexusTransferDetails(current, "bb")!!
        assertTrue(confirmed.incoming)
        assertEquals(peer, confirmed.sender)
        assertEquals(owner, confirmed.recipient)
        assertEquals(TransactionStatus.COMMITTED, confirmed.status)
        assertEquals(7L, confirmed.timestampMillis)
        assertNull(confirmed.statusNote)

        val failed = nexusTransferDetails(current, "cc")!!
        assertFalse(failed.incoming)
        assertEquals(owner, failed.sender)
        assertEquals(peer, failed.recipient)
        assertEquals(TransactionStatus.REJECTED, failed.status)
        assertEquals(R.string.wallet_submission_failed, failed.statusNote)
        assertEquals(42L, failed.timestampMillis)

        val unknown = nexusTransferDetails(current, "local-null")!!
        assertEquals("", unknown.hash)
        assertEquals(R.string.wallet_submission_uncertain, unknown.statusNote)

        assertNull(nexusTransferDetails(current, "gone"))
        assertNull(nexusTransferDetails(current.copy(pendingTransactions = listOf(pending("cc").copy(createdAtMillis = 0L))), "cc")!!.timestampMillis)
    }

    @Test fun `Send's step is its form, its review, or what became of the transfer`() {
        val network = balance()
        val form = NexusSendUiState(visible = true, network = network)
        assertEquals(NexusSendStep.FORM, form.step())
        val review = form.copy(prepared = mockk<NexusPreparedSend>())
        assertEquals(NexusSendStep.REVIEW, review.step())
        assertEquals(NexusSendStep.OUTCOME, review.copy(result = NexusSendResult("id", "hash", "PENDING")).step())
        assertEquals(NexusSendStep.OUTCOME, form.copy(submissionNeedsCheck = true).step())
        // An earlier transfer still being checked keeps the form, without Review.
        val checking = form.copy(errorCode = "NEXUS_PENDING_RECOVERY_REQUIRED")
        assertEquals(NexusSendStep.FORM, checking.step())
        assertTrue(checking.needsStatusCheck())
        assertFalse(form.copy(errorCode = "NEXUS_INVALID_ADDRESS").needsStatusCheck())
    }

    @Test fun `the receive page belongs to one wallet, network and address`() {
        val network = balance()
        val page = NexusReceiveUiState(network.walletId, network.networkId, network.address)
        assertTrue(page.isFor(network))
        assertFalse(page.isFor(network.copy(walletId = "other")))
        assertFalse(page.isFor(network.copy(networkId = WalletNetworkId.MINAMOTO)))
        assertFalse(page.isFor(network.copy(address = "other")))
    }

    @Test fun `the pages' routes name the network`() {
        assertEquals("Nexus.Asset/taira", NexusRoutes.asset(WalletNetworkId.TAIRA))
        assertEquals("Nexus.Receive/minamoto", NexusRoutes.receive(WalletNetworkId.MINAMOTO))
        assertEquals("Nexus.Transfer/taira/local%3Aab", NexusRoutes.transfer(WalletNetworkId.TAIRA, "local:ab"))
        assertTrue(NexusRoutes.SEND in NexusRoutes.pushed)
    }
}
