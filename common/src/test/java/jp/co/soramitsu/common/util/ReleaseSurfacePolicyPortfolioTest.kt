package jp.co.soramitsu.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSurfacePolicyPortfolioTest {

    @Test
    fun `the portfolio view is on outside production and off in production builds`() {
        assertFalse(ReleaseSurfacePolicy.PORTFOLIO_VIEW_IN_PRODUCTION)
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false))
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }

    @Test
    fun `one constant ships the portfolio view to production`() {
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = true, inProduction = true))
        assertTrue(ReleaseSurfacePolicy.portfolioView(productionFlavor = false, inProduction = true))
    }

    @Test
    fun `the portfolio view does not follow the other surfaces`() {
        // Production may offer the Nexus surfaces on this line; the portfolio view stays off there.
        val production = ReleaseSurfacePolicy.surfaces(true, productionHidesSurfaces = false)
        assertTrue(production.nexusPortfolio)
        assertFalse(ReleaseSurfacePolicy.portfolioView(productionFlavor = true))
    }
}
