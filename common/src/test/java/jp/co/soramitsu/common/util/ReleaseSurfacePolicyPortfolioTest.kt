package jp.co.soramitsu.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSurfacePolicyPortfolioTest {

    @Test
    fun `the portfolio view is on in every flavor, production included`() {
        assertTrue(ReleaseSurfacePolicy.PORTFOLIO_VIEW_IN_PRODUCTION)
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false))
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }

    @Test
    fun `one constant takes the portfolio view out of production`() {
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = true, inProduction = false))
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false, inProduction = false))
    }

    @Test
    fun `the portfolio view does not follow the other surfaces`() {
        // Even a production build that hides the Nexus surfaces (the public store line) shows it.
        val hidden = ReleaseSurfacePolicy.surfaces(true, productionHidesSurfaces = true)
        assertFalse(hidden.nexusPortfolio)
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }
}
