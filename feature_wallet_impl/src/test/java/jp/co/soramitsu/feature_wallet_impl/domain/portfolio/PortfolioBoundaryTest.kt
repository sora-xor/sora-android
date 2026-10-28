package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PortfolioBoundaryTest {
    @Test
    fun `finite large holdings still allocate all one hundred rays`() {
        assertEquals(listOf(50, 50), FanAllocation.largestRemainder(listOf(Double.MAX_VALUE, Double.MAX_VALUE)))
        assertEquals(listOf(67, 33), FanAllocation.largestRemainder(listOf(Double.MAX_VALUE, Double.MAX_VALUE / 2)))
    }

    @Test
    fun `changing units leaves the allocation unchanged`() {
        val amounts = listOf(5.0, 3.0, 2.0)
        val expected = listOf(50, 30, 20)
        for (scale in listOf(1e-300, 1.0, 1e300, 3e307)) {
            assertEquals("scale $scale", expected, FanAllocation.largestRemainder(amounts.map { it * scale }))
        }
    }

    @Test
    fun `invalid and zero values never receive a ray`() {
        assertEquals(listOf(0, 0, 0, 0, 0, 100), FanAllocation.largestRemainder(
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, 1.0)))
        assertEquals(listOf(0, 0), FanAllocation.largestRemainder(listOf(Double.NaN, -1.0)))
    }

    @Test
    fun `empty inputs and nonpositive totals produce no allocation`() {
        assertTrue(FanAllocation.largestRemainder(emptyList()).isEmpty())
        for (total in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(listOf(0, 0), FanAllocation.largestRemainder(listOf(1.0, 2.0), total))
        }
    }

    @Test
    fun `remainder ties are stable and tiny finite holdings remain visible`() {
        assertEquals(listOf(34, 33, 33), FanAllocation.largestRemainder(listOf(1.0, 1.0, 1.0)))
        assertEquals(listOf(33, 67), FanAllocation.largestRemainder(listOf(Double.MIN_VALUE, Double.MIN_VALUE * 2)))
    }

    @Test
    fun `seeded allocation corpus preserves totals nonnegativity and invalid exclusions`() {
        val random = Random(20261026)
        repeat(400) {
            val values = List(random.nextInt(1, 50)) {
                when (random.nextInt(8)) {
                    0 -> 0.0
                    1 -> Double.NaN
                    2 -> -random.nextDouble()
                    else -> random.nextDouble(1.0, 10.0) * Math.pow(10.0, random.nextInt(-300, 308).toDouble())
                }
            }
            val total = random.nextInt(1, 1001)
            val result = FanAllocation.largestRemainder(values, total)
            assertEquals(values.size, result.size)
            assertTrue(result.all { it >= 0 })
            assertEquals(if (values.any { it.isFinite() && it > 0 }) total else 0, result.sum())
            values.forEachIndexed { index, value -> if (!value.isFinite() || value <= 0) assertEquals(0, result[index]) }
        }
    }

    @Test
    fun `overflowing timestamp differences never make saved data live`() {
        val ancient = WalletSync(refreshedAtMillis = Long.MIN_VALUE)
        val future = WalletSync(refreshedAtMillis = Long.MAX_VALUE)
        assertEquals(FreshnessKind.SAVED, PortfolioFreshness.of(ancient, true, Long.MAX_VALUE).kind)
        assertEquals(FreshnessKind.SAVED, PortfolioFreshness.of(future, true, Long.MIN_VALUE).kind)
    }

    @Test
    fun `overflowing elapsed time never prevents a refresh`() {
        assertTrue(PortfolioFreshness.isDue(-1, Long.MAX_VALUE))
        assertTrue(PortfolioFreshness.isDue(Long.MIN_VALUE, 0))
        assertTrue(PortfolioFreshness.isDue(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(false, PortfolioFreshness.isDue(Long.MAX_VALUE - 1, Long.MAX_VALUE))
    }
}
