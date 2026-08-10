package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktPendingTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktClaimConfirmationPolicyTest {

    @Test
    fun `selected confirmation is isolated from a later batch review`() {
        val selectedClaim = claim(marketId = 7, payout = 70)
        val confirmed = PolkamarktClaimConfirmationPolicy.selected(
            accountId = ACCOUNT,
            marketId = 7,
            claimable = selectedClaim,
            finalizedBlockHash = SELECTED_HASH,
            creatorFees = false,
        )

        PolkamarktClaimConfirmationPolicy.requireCurrent(
            confirmed = confirmed,
            accountId = ACCOUNT,
            selectedMarketId = 7,
            selectedClaimable = selectedClaim,
            selectedFinalizedBlockHash = SELECTED_HASH,
            reviewedClaims = mapOf(8L to claim(marketId = 8, payout = 80)),
            reviewedMarketIds = setOf(8),
            claimReviewFinalizedBlockHash = BATCH_HASH,
        )
    }

    @Test
    fun `batch confirmation is isolated from a later selected detail review`() {
        val reviewedClaims = mapOf(
            7L to claim(marketId = 7, payout = 70),
            8L to claim(marketId = 8, payout = 80),
        )
        val confirmed = PolkamarktClaimConfirmationPolicy.reviewedBatch(
            accountId = ACCOUNT,
            marketIds = listOf(7, 8),
            claims = reviewedClaims,
            reviewedMarketIds = setOf(7, 8),
            finalizedBlockHash = BATCH_HASH,
        )

        PolkamarktClaimConfirmationPolicy.requireCurrent(
            confirmed = confirmed,
            accountId = ACCOUNT,
            selectedMarketId = 99,
            selectedClaimable = claim(marketId = 99, payout = 990),
            selectedFinalizedBlockHash = SELECTED_HASH,
            reviewedClaims = reviewedClaims,
            reviewedMarketIds = setOf(7, 8),
            claimReviewFinalizedBlockHash = BATCH_HASH,
        )
        assertEquals(BigInteger.valueOf(150), confirmed.totalAmount)
    }

    @Test
    fun `confirmation rejects any reviewed quantity or checkpoint change`() {
        val reviewed = claim(marketId = 7, payout = 70)
        val confirmed = PolkamarktClaimConfirmationPolicy.reviewedPosition(
            accountId = ACCOUNT,
            marketId = 7,
            claims = mapOf(7L to reviewed),
            reviewedMarketIds = setOf(7),
            finalizedBlockHash = BATCH_HASH,
            creatorFees = false,
        )

        val amountError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimConfirmationPolicy.requireCurrent(
                confirmed = confirmed,
                accountId = ACCOUNT,
                selectedMarketId = null,
                selectedClaimable = null,
                selectedFinalizedBlockHash = null,
                reviewedClaims = mapOf(
                    7L to reviewed.copy(
                        claimablePayout = BigInteger.valueOf(71),
                    ),
                ),
                reviewedMarketIds = setOf(7),
                claimReviewFinalizedBlockHash = BATCH_HASH,
            )
        }
        assertEquals("POLKAMARKT_CLAIM_CONFIRMATION_STALE", amountError.message)

        assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimConfirmationPolicy.requireCurrent(
                confirmed = confirmed,
                accountId = ACCOUNT,
                selectedMarketId = null,
                selectedClaimable = null,
                selectedFinalizedBlockHash = null,
                reviewedClaims = mapOf(7L to reviewed),
                reviewedMarketIds = setOf(7),
                claimReviewFinalizedBlockHash = SELECTED_HASH,
            )
        }
    }

    @Test
    fun `mutation gate rejects duplicate synchronous entry and can be released`() {
        val gate = PolkamarktMutationUseGate()

        assertTrue(gate.tryAcquire())
        assertTrue(gate.isAcquired())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.isAcquired())
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `batch confirmation requires canonical unique market order`() {
        val claims = mapOf(
            7L to claim(marketId = 7, payout = 70),
            8L to claim(marketId = 8, payout = 80),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimConfirmationPolicy.reviewedBatch(
                accountId = ACCOUNT,
                marketIds = listOf(8, 7),
                claims = claims,
                reviewedMarketIds = setOf(7, 8),
                finalizedBlockHash = BATCH_HASH,
            )
        }
        assertEquals("POLKAMARKT_CLAIM_BATCH_NOT_CANONICAL", error.message)
    }

    @Test
    fun `quote admission stays closed from in flight mutation through ambiguous recovery`() {
        val gate = PolkamarktMutationUseGate()
        assertTrue(gate.tryAcquire())
        assertFalse(
            PolkamarktMutationAdmissionPolicy.canPrepareQuote(
                mutationInFlight = gate.isAcquired(),
                pendingRecoveryRequired = false,
            )
        )
        gate.release()

        val pending = PolkamarktPendingTransaction(
            localId = "local",
            transactionHash = "0x" + "ab".repeat(32),
            operation = "BUY",
            amount = "100",
            state = "UNKNOWN",
            submissionIsAmbiguous = true,
        )
        val recoveryRequired = PolkamarktMutationAdmissionPolicy
            .requiresPendingRecovery(listOf(pending))
        assertTrue(recoveryRequired)
        assertTrue(
            PolkamarktMutationAdmissionPolicy.requiresPendingRecovery(
                listOf(pending.copy(state = "SIGNED", submissionIsAmbiguous = false))
            )
        )
        assertFalse(
            PolkamarktMutationAdmissionPolicy.canPrepareQuote(
                mutationInFlight = false,
                pendingRecoveryRequired = recoveryRequired,
            )
        )
    }

    @Test
    fun `missing claim checkpoint uses the stable domain error`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimConfirmationPolicy.selected(
                accountId = ACCOUNT,
                marketId = 7,
                claimable = claim(marketId = 7, payout = 70),
                finalizedBlockHash = null,
                creatorFees = false,
            )
        }

        assertEquals("POLKAMARKT_CLAIM_CHECKPOINT_INVALID", error.message)
    }

    private fun claim(
        marketId: Long,
        payout: Long,
    ) = PolkamarktClaimableDto(
        marketId = marketId,
        account = ACCOUNT,
        status = "Resolved",
        resolutionOutcome = "Yes",
        yesShares = BigInteger.valueOf(10),
        noShares = BigInteger.ZERO,
        netCollateralPaid = BigInteger.valueOf(5),
        traderPayout = BigInteger.valueOf(payout),
        claimablePayout = BigInteger.valueOf(payout),
        creatorFees = BigInteger.valueOf(3),
        isCreator = true,
    )

    private companion object {
        const val ACCOUNT = "cnWallet"
        const val SELECTED_HASH =
            "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val BATCH_HASH =
            "0x2222222222222222222222222222222222222222222222222222222222222222"
    }
}
