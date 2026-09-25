package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import org.junit.Assert.*
import org.junit.Test

class ProbabilityHistoryTest {
    @Test fun `timestamps determine spacing rather than sample order`() {
        val points = probabilityHistory(listOf(1_783_209_700L to 0.5, 1_783_209_600L to 0.2,
            1_783_209_610_000L to 0.3))
        assertEquals(1_783_209_600_000L, points.first().timestampMillis)
        assertEquals(0.1f, probabilityX(points[1], points), 0.0001f)
        assertEquals(1f, probabilityX(points.last(), points), 0.0001f)
    }
    @Test fun `invalid undated duplicate or nonfinite points are excluded`() {
        val points = probabilityHistory(listOf(null to 0.1, 0L to 0.1, 100L to Double.NaN,
            200L to 1.2, 300L to 0.2, 300_000L to 0.3, 300L to 0.7))
        assertEquals(2, points.size)
        assertEquals(0.2, points.first().probability, 0.001)
    }
    @Test fun `range filtering preserves actual dates`() {
        val points = listOf(ProbabilityPoint(1000, 0.2), ProbabilityPoint(2000, 0.4), ProbabilityPoint(10000, 0.8))
        assertEquals(listOf(points.last()), probabilityWindow(points, 1000))
        assertEquals(points, probabilityWindow(points, null))
        assertEquals(0f, probabilityX(points.first(), listOf(points.first())), 0f)
    }
}
