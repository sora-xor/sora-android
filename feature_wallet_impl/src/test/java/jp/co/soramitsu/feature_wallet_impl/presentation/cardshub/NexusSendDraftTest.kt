package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class NexusSendDraftTest {
    @Test fun `denied or cancelled scan preserves draft and only denied permission adds recovery guidance`() {
        val draft = NexusSendUiState(visible = true, recipient = "saved-address", amount = "1.25")
        val updated = draft.afterRecipientScan(null, cameraPermissionDenied = true)
        assertEquals(draft.recipient, updated.recipient)
        assertEquals(draft.amount, updated.amount)
        assertTrue(updated.scannerPermissionDenied)
        assertEquals(draft, draft.afterRecipientScan(null))
        val submitted = draft.copy(submissionNeedsCheck = true)
        assertEquals(submitted, submitted.afterRecipientScan(null, cameraPermissionDenied = true))
        assertEquals("scanned-address", updated.afterRecipientScan("scanned-address").recipient)
    }

    @Test fun `stale confirmation and uncertain outcomes cannot resubmit`() {
        val review = NexusSendUiState(visible = true, prepared = mockk())
        assertTrue(review.canConfirmSend())
        assertFalse(review.copy(result = mockk()).canConfirmSend())
        assertFalse(review.copy(submissionNeedsCheck = true).canConfirmSend())
        assertFalse(review.copy(submitting = true).canConfirmSend())
        val draft = review.copy(recipient = "address", amount = "1.25")
        val failed = draft.afterSubmissionFailure(jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusNotSubmittedException(
            jp.co.soramitsu.common.nexus.NexusToriiException("NEXUS_NETWORK_IO")))
        assertFalse(failed.submissionNeedsCheck)
        assertNull(failed.prepared)
        assertEquals("address", failed.recipient)
        assertEquals("1.25", failed.amount)
        assertTrue(draft.afterSubmissionFailure(IllegalStateException("unknown outcome")).submissionNeedsCheck)
        assertTrue(draft.afterSubmissionFailure(jp.co.soramitsu.common.nexus.NexusToriiException("NEXUS_STATUS_IO")).submissionNeedsCheck)
    }

    @Test fun `scan results belong only to the unchanged editable wallet network and draft`() {
        val network = mockk<jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance>()
        io.mockk.every { network.walletId } returns "wallet-a"
        io.mockk.every { network.networkId } returns jp.co.soramitsu.common.nexus.WalletNetworkId.TAIRA
        io.mockk.every { network.address } returns "sender-a"
        val state = NexusSendUiState(visible = true, network = network)
        val scan = NexusRecipientScan(3, "wallet-a", network.networkId, "sender-a")
        assertTrue(state.acceptsScan(scan, 3))
        assertFalse(state.acceptsScan(scan, 4))
        assertFalse(state.acceptsScan(scan.copy(walletId = "wallet-b"), 3))
        assertFalse(state.acceptsScan(scan.copy(address = "sender-b"), 3))
        assertFalse(state.acceptsScan(scan.copy(networkId = jp.co.soramitsu.common.nexus.WalletNetworkId.SORA2), 3))
        for (blocked in listOf(state.copy(preparing = true), state.copy(submitting = true),
            state.copy(prepared = mockk()), state.copy(result = mockk()), state.copy(submissionNeedsCheck = true),
            state.copy(visible = false), state.copy(network = null))) assertFalse(blocked.acceptsScan(scan, 3))
    }

    @Test fun `editing review retains recipient and amount but invalidates quote`() {
        val reviewed = NexusSendUiState(visible = true, recipient = "recipient", amount = "1.25",
            prepared = mockk(), errorCode = "NEXUS_STALE_QUOTE")
        val draft = requireNotNull(reviewed.editableDraft())
        assertEquals(reviewed.recipient, draft.recipient)
        assertEquals(reviewed.amount, draft.amount)
        assertNull(draft.prepared)
        assertNull(draft.errorCode)
    }
    @Test fun `submitted busy or closed drafts cannot become editable`() {
        val state = NexusSendUiState(visible = true)
        assertNull(state.copy(preparing = true).editableDraft())
        assertNull(state.copy(submitting = true).editableDraft())
        assertNull(state.copy(result = mockk()).editableDraft())
        assertNull(state.copy(submissionNeedsCheck = true).editableDraft())
        assertNull(state.copy(visible = false).editableDraft())
    }
}
