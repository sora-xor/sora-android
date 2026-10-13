package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolkamarktClaimValidatorTest {

    @Test
    fun `claim response is bound to the exact account and market`() {
        PolkamarktClaimValidator.requireClaimable(
            claimable = validClaim,
            expectedAccountId = ACCOUNT_ID,
            expectedMarketId = MARKET_ID,
            creatorFees = false,
        )

        val accountError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireClaimable(
                claimable = validClaim.copy(account = "different-account"),
                expectedAccountId = ACCOUNT_ID,
                expectedMarketId = MARKET_ID,
                creatorFees = false,
            )
        }
        assertEquals("POLKAMARKT_CLAIM_ACCOUNT_MISMATCH", accountError.message)

        val marketError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireClaimable(
                claimable = validClaim.copy(marketId = MARKET_ID + 1),
                expectedAccountId = ACCOUNT_ID,
                expectedMarketId = MARKET_ID,
                creatorFees = false,
            )
        }
        assertEquals("POLKAMARKT_CLAIM_MARKET_MISMATCH", marketError.message)
    }

    @Test
    fun `claim status and positive payout are required`() {
        val statusError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireClaimable(
                claimable = validClaim.copy(status = "Open"),
                expectedAccountId = ACCOUNT_ID,
                expectedMarketId = MARKET_ID,
                creatorFees = false,
            )
        }
        assertEquals("POLKAMARKT_STATUS_NOT_CLAIMABLE", statusError.message)

        val payoutError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireClaimable(
                claimable = validClaim.copy(claimablePayout = BigInteger.ZERO),
                expectedAccountId = ACCOUNT_ID,
                expectedMarketId = MARKET_ID,
                creatorFees = false,
            )
        }
        assertEquals("POLKAMARKT_TRADER_PAYOUT_NOT_CLAIMABLE", payoutError.message)

        val creatorError = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireClaimable(
                claimable = validClaim.copy(
                    isCreator = false,
                    creatorFees = BigInteger.ONE,
                ),
                expectedAccountId = ACCOUNT_ID,
                expectedMarketId = MARKET_ID,
                creatorFees = true,
            )
        }
        assertEquals("POLKAMARKT_CREATOR_FEES_NOT_CLAIMABLE", creatorError.message)
    }

    @Test
    fun `trader claim batch rejects duplicate markets`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktClaimValidator.requireTraderBatch(
                listOf(MARKET_ID, MARKET_ID),
            )
        }

        assertEquals("POLKAMARKT_DUPLICATE_CLAIM_MARKET", error.message)
    }

    @Test
    fun `market identity accepts full u32 range and rejects overflow`() {
        PolkamarktClaimValidator.requireIdentity(
            claimable = validClaim.copy(marketId = PolkamarktMarketId.MAX_VALUE),
            expectedAccountId = ACCOUNT_ID,
            expectedMarketId = PolkamarktMarketId.MAX_VALUE,
        )

        val overflow = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktClaimValidator.requireTraderBatch(
                listOf(PolkamarktMarketId.MAX_VALUE + 1L),
            )
        }
        assertEquals("POLKAMARKT_INVALID_MARKET_ID", overflow.message)

        val negative = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktClaimValidator.requireTraderBatch(listOf(-1L))
        }
        assertEquals("POLKAMARKT_INVALID_MARKET_ID", negative.message)
    }

    @Test
    fun `claim authorization binds the exact reviewed runtime values`() {
        val confirmed = authorization(validClaim)

        PolkamarktClaimValidator.requireFreshClaims(
            confirmed = confirmed,
            freshClaims = mapOf(MARKET_ID to validClaim),
        )

        val changed = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireFreshClaims(
                confirmed = confirmed,
                freshClaims = mapOf(
                    MARKET_ID to validClaim.copy(
                        claimablePayout = validClaim.claimablePayout + BigInteger.ONE,
                    )
                ),
            )
        }
        assertEquals("POLKAMARKT_CLAIM_CHANGED", changed.message)
    }

    @Test
    fun `claim authorization rejects a noncanonical review checkpoint`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireAuthorization(
                PolkamarktClaimAuthorization(
                    accountId = ACCOUNT_ID,
                    marketIds = listOf(MARKET_ID),
                    creatorFees = false,
                    reviewedFinalizedBlockHash = REVIEWED_HASH.uppercase(),
                    reviewedClaims = mapOf(MARKET_ID to validClaim),
                ),
            )
        }

        assertEquals("POLKAMARKT_CLAIM_CHECKPOINT_INVALID", error.message)
    }

    private fun authorization(
        claim: PolkamarktClaimableDto,
    ) = PolkamarktClaimAuthorization(
        accountId = ACCOUNT_ID,
        marketIds = listOf(MARKET_ID),
        creatorFees = false,
        reviewedFinalizedBlockHash = REVIEWED_HASH,
        reviewedClaims = mapOf(MARKET_ID to claim),
    )

    private companion object {
        const val ACCOUNT_ID = "cnRetainedWallet"
        const val MARKET_ID = 753L
        const val REVIEWED_HASH =
            "0x1111111111111111111111111111111111111111111111111111111111111111"

        val validClaim = PolkamarktClaimableDto(
            marketId = MARKET_ID,
            account = ACCOUNT_ID,
            status = "resolved",
            resolutionOutcome = "Yes",
            yesShares = BigInteger.ONE,
            noShares = BigInteger.ZERO,
            netCollateralPaid = BigInteger.ONE,
            traderPayout = BigInteger.ONE,
            claimablePayout = BigInteger.ONE,
            creatorFees = BigInteger.ONE,
            isCreator = true,
        )
    }
}
