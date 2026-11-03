package jp.co.soramitsu.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSurfacePolicyPortfolioTest {

    @Test
    fun `the wallet home is the classic one in every flavor`() {
        assertFalse(ReleaseSurfacePolicy.PORTFOLIO_VIEW_IN_PRODUCTION)
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = false))
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }

    @Test
    fun `one constant brings the portfolio view back`() {
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = true, inProduction = true))
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false, inProduction = true))
    }
}
