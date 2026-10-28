package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolkamarktMutationQualificationTest {

    @Test
    fun `trade accepts an exact fresh buy confirmation`() {
        val confirmed = quote()

        PolkamarktTradeMutationValidator.requireFresh(
            confirmed = confirmed,
            fresh = confirmed,
            exactNetworkFee = confirmed.xorNetworkFee,
        )
    }

    @Test
    fun `trade rejects a stale quote before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_STALE_QUOTE",
            confirmed = confirmed,
            fresh = confirmed.copy(outputAmount = confirmed.minimumOutput - BigInteger.ONE),
        )
    }

    @Test
    fun `closed market is rejected while refreshing a mutation quote`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktMarketMutationValidator.requireOpen("Closed")
        }

        assertEquals("POLKAMARKT_MARKET_CLOSED", error.message)
    }

    @Test
    fun `trade rejects a status change before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_STATUS_CHANGED",
            confirmed = confirmed,
            fresh = confirmed.copy(market = market(status = "Resolved")),
        )
    }

    @Test
    fun `trade rejects a market fee change before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_MARKET_FEE_CHANGED",
            confirmed = confirmed,
            fresh = confirmed.copy(marketFee = confirmed.marketFee + BigInteger.ONE),
        )
    }

    @Test
    fun `trade rejects a pricing mechanism change before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_MECHANISM_CHANGED",
            confirmed = confirmed,
            fresh = confirmed.copy(authoritativeMechanism = "DPM"),
        )
    }

    @Test
    fun `trade rejects a close block change before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_CLOSE_BLOCK_CHANGED",
            confirmed = confirmed,
            fresh = confirmed.copy(
                authoritativeCloseBlock = confirmed.authoritativeCloseBlock + BigInteger.ONE
            ),
        )
    }

    @Test
    fun `trade rejects an increased network fee before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_FEE_INCREASED",
            confirmed = confirmed,
            fresh = confirmed,
            exactNetworkFee = confirmed.xorNetworkFee + BigInteger.ONE,
        )
    }

    @Test
    fun `trade rejects insufficient XOR for the refreshed fee`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_INSUFFICIENT_XOR",
            confirmed = confirmed,
            fresh = confirmed.copy(xorBalance = confirmed.xorNetworkFee - BigInteger.ONE),
        )
    }

    @Test
    fun `buy rejects insufficient KUSD before signing`() {
        val confirmed = quote()

        assertTradeFailure(
            expectedCode = "POLKAMARKT_INSUFFICIENT_KUSD",
            confirmed = confirmed,
            fresh = confirmed.copy(kusdBalance = confirmed.amountIn - BigInteger.ONE),
        )
    }

    @Test
    fun `sell does not require a KUSD balance`() {
        val confirmed = quote(side = PolkamarktTradeSide.SELL)

        PolkamarktTradeMutationValidator.requireFresh(
            confirmed = confirmed,
            fresh = confirmed.copy(kusdBalance = BigInteger.ZERO),
            exactNetworkFee = confirmed.xorNetworkFee,
        )
    }

    @Test
    fun `sell rejects a refreshed authoritative share deficit`() {
        val confirmed = quote(side = PolkamarktTradeSide.SELL)

        assertTradeFailure(
            expectedCode = "POLKAMARKT_INSUFFICIENT_SHARES",
            confirmed = confirmed,
            fresh = confirmed.copy(shareBalance = confirmed.amountIn - BigInteger.ONE),
        )
    }

    @Test
    fun `claim rejects an increased fee before signing`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireFreshFeeAndBalance(
                confirmedNetworkFee = BigInteger.TEN,
                freshNetworkFee = BigInteger.valueOf(11),
                freshXorBalance = BigInteger.valueOf(100),
            )
        }

        assertEquals("POLKAMARKT_FEE_INCREASED", error.message)
    }

    @Test
    fun `claim rejects insufficient XOR before signing`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktClaimValidator.requireFreshFeeAndBalance(
                confirmedNetworkFee = BigInteger.TEN,
                freshNetworkFee = BigInteger.TEN,
                freshXorBalance = BigInteger.valueOf(9),
            )
        }

        assertEquals("POLKAMARKT_INSUFFICIENT_XOR", error.message)
    }

    @Test
    fun `claim accepts an unchanged fee with exact XOR balance`() {
        PolkamarktClaimValidator.requireFreshFeeAndBalance(
            confirmedNetworkFee = BigInteger.TEN,
            freshNetworkFee = BigInteger.TEN,
            freshXorBalance = BigInteger.TEN,
        )
    }

    @Test
    fun `confirmed quote authorization is one shot and bounded`() {
        val gate = PolkamarktConfirmedQuoteUseGate(maximumIdentities = 2)
        val first = "11".repeat(32)
        val second = "22".repeat(32)
        val third = "33".repeat(32)

        gate.consume(first)
        val reused = assertThrows(IllegalStateException::class.java) {
            gate.consume(first)
        }
        assertEquals("POLKAMARKT_QUOTE_ALREADY_USED", reused.message)

        gate.consume(second)
        val exhausted = assertThrows(IllegalStateException::class.java) {
            gate.consume(third)
        }
        assertEquals("POLKAMARKT_QUOTE_USE_GATE_EXHAUSTED", exhausted.message)
    }

    private fun assertTradeFailure(
        expectedCode: String,
        confirmed: PolkamarktTradeQuote,
        fresh: PolkamarktTradeQuote,
        exactNetworkFee: BigInteger = confirmed.xorNetworkFee,
    ) {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktTradeMutationValidator.requireFresh(
                confirmed = confirmed,
                fresh = fresh,
                exactNetworkFee = exactNetworkFee,
            )
        }

        assertEquals(expectedCode, error.message)
    }

    private fun quote(
        side: PolkamarktTradeSide = PolkamarktTradeSide.BUY,
    ) = PolkamarktTradeQuote(
        identity = "confirmed-quote",
        confirmationNonce = "11111111-1111-4111-8111-111111111111",
        accountId = "cnWallet",
        market = market(status = "Open"),
        side = side,
        outcome = PolkamarktOutcome.YES,
        amountIn = BigInteger.valueOf(100),
        outputAmount = BigInteger.valueOf(200),
        minimumOutput = BigInteger.valueOf(180),
        marketFee = BigInteger.valueOf(3),
        xorNetworkFee = BigInteger.TEN,
        observedFinalizedBlock = "0x${"11".repeat(32)}",
        authoritativeMechanism = "LMSR",
        authoritativeCloseBlock = BigInteger.valueOf(1_000),
        kusdBalance = BigInteger.valueOf(100),
        xorBalance = BigInteger.TEN,
        shareBalance = if (side == PolkamarktTradeSide.SELL) {
            BigInteger.valueOf(100)
        } else {
            null
        },
    )

    private fun market(status: String) = PolkamarktMarket(
        id = "market-7",
        marketId = 7,
        title = "Will qualification pass?",
        category = null,
        tags = null,
        description = null,
        rulesUri = null,
        resolutionSource = null,
        closeBlock = 1_000,
        status = status,
        mechanism = "LMSR",
        creator = null,
        collateralAsset = null,
        creatorFees = null,
        liquidityUsd = null,
        volumeUsd = null,
        chartProbability = null,
        chartPriceYes = null,
        chartPriceNo = null,
        virtualDepth = null,
        dpmCollateral = null,
        realYesShares = null,
        realNoShares = null,
        marginalYesPriceBps = null,
        marginalNoPriceBps = null,
        impliedYesProbabilityBps = null,
        impliedNoProbabilityBps = null,
        collateral = null,
        yesShares = null,
        noShares = null,
        resolutionOutcome = null,
        resolutionEvidenceUri = null,
        governanceUrl = null,
        updatedAtBlock = 900,
        timestamp = null,
    )
}
