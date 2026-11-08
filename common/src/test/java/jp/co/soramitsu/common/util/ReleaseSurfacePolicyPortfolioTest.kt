package jp.co.soramitsu.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSurfacePolicyPortfolioTest {

    @Test
    fun `the wallet home is the portfolio in every flavor`() {
        assertTrue(ReleaseSurfacePolicy.PORTFOLIO_VIEW_IN_PRODUCTION)
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false))
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }

    @Test
    fun `one constant brings the classic home back`() {
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = true, inProduction = false))
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = false, inProduction = false))
    }
}
