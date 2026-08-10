package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PolkamarktMutationAccountValidatorTest {

    @Test
    fun `confirmed account remains valid while selection is unchanged`() {
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId = "cnConfirmed",
            actualAccountId = "cnConfirmed",
        )
    }

    @Test
    fun `confirmed account is rejected after wallet selection changes`() {
        val error = runCatching {
            PolkamarktMutationAccountValidator.requireExpected(
                expectedAccountId = "cnConfirmed",
                actualAccountId = "cnNewSelection",
            )
        }.exceptionOrNull()

        assertEquals("POLKAMARKT_ACCOUNT_CHANGED", error?.message)
    }

    @Test
    fun `missing confirmed account is rejected`() {
        val error = runCatching {
            PolkamarktMutationAccountValidator.requireExpected(
                expectedAccountId = "",
                actualAccountId = "cnCurrent",
            )
        }.exceptionOrNull()

        assertEquals("POLKAMARKT_ACCOUNT_MISSING", error?.message)
    }
}
