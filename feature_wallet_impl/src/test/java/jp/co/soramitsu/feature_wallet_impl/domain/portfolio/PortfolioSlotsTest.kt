package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import jp.co.soramitsu.common.domain.AssetHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioSlotsTest {

    private val xor = AssetHolder.XOR_ASSET_ID
    private val valId = PortfolioSlots.VAL_ID
    private val pswap = PortfolioSlots.PSWAP_ID
    private val xstusd = PortfolioSlots.XSTUSD_ID
    private val eth = PortfolioSlots.ETH_ID
    private val tokenSeed = PortfolioSlots.seed(PortfolioLens.TOKENS)

    @Test
    fun `first launch uses the seed XOR VAL PSWAP XSTUSD ETH`() {
        assertEquals(mapOf(xor to 0, valId to 1, pswap to 2, xstusd to 3, eth to 4), tokenSeed)
        val slots = PortfolioSlots.assign(null, listOf(eth, xstusd, valId, xor, pswap), tokenSeed)
        // Colour follows the entity, not its rank.
        assertEquals(mapOf(eth to 4, xstusd to 3, valId to 1, xor to 0, pswap to 2), slots)
    }

    @Test
    fun `a newcomer takes the lowest free slot`() {
        val slots = PortfolioSlots.assign(null, listOf(valId, xor, "KUSD", pswap, "DAI"), tokenSeed)
        assertEquals(mapOf(valId to 1, xor to 0, pswap to 2, "KUSD" to 3, "DAI" to 4), slots)
    }

    @Test
    fun `an entity keeps its slot while it stays in the top five and frees it when it leaves`() {
        val previous = mapOf("A" to 3, "B" to 0, "C" to 4, "D" to 1, "E" to 2)
        val reordered = PortfolioSlots.assign(previous, listOf("E", "D", "C", "B", "A"), emptyMap())
        assertEquals(previous, reordered)
        val withNewcomer = PortfolioSlots.assign(previous, listOf("A", "B", "C", "D", "F", "E"), emptyMap())
        assertEquals(mapOf("A" to 3, "B" to 0, "C" to 4, "D" to 1, "F" to 2), withNewcomer)
        val back = PortfolioSlots.assign(withNewcomer, listOf("E", "A", "B", "C", "D", "F"), emptyMap())
        assertEquals(mapOf("E" to 2, "A" to 3, "B" to 0, "C" to 4, "D" to 1), back)
    }

    @Test
    fun `at most five slots, the rest is Other`() {
        val slots = PortfolioSlots.assign(null, (1..8).map { "t$it" }, emptyMap())
        assertEquals(5, slots.size)
        assertEquals((0 until PortfolioSlots.SLOT_COUNT).toSet(), slots.values.toSet())
        assertTrue("t6" !in slots && "t7" !in slots && "t8" !in slots)
    }

    @Test
    fun `invalid or duplicated stored slots are reassigned`() {
        val slots = PortfolioSlots.assign(mapOf("A" to 9, "B" to 1, "C" to 1), listOf("A", "B", "C"), emptyMap())
        assertEquals(mapOf("B" to 1, "A" to 0, "C" to 2), slots)
    }

    @Test
    fun `one map per scope and lens`() {
        assertEquals("all|tokens", PortfolioSlots.mapKey(PortfolioScope.ALL, "w", PortfolioLens.TOKENS))
        assertEquals("wallet:w|networks", PortfolioSlots.mapKey(PortfolioScope.SELECTED, "w", PortfolioLens.NETWORKS))
        assertEquals(mapOf("sora2" to 0, "minamoto" to 1), PortfolioSlots.seed(PortfolioLens.NETWORKS))
        assertTrue(PortfolioSlots.seed(PortfolioLens.WALLETS).isEmpty())
    }
}

class FanAllocationTest {

    @Test
    fun `rays always add up to 100 with largest remainders`() {
        // The debug sample's token lens: XSTUSD, XOR, ETH, VAL, XST and Other (PSWAP).
        val rays = FanAllocation.largestRemainder(listOf(3600.55, 3150.0, 2774.392, 1080.0, 735.0, 680.0))
        assertEquals(listOf(30, 26, 23, 9, 6, 6), rays)
        assertEquals(listOf(34, 33, 33), FanAllocation.largestRemainder(listOf(1.0, 1.0, 1.0)))
        assertEquals(listOf(100, 0), FanAllocation.largestRemainder(listOf(99.5, 0.5)))
        val random = java.util.Random(7)
        repeat(200) {
            val values = List(1 + random.nextInt(6)) { random.nextDouble() * 1000 }
            assertEquals(100, FanAllocation.largestRemainder(values).sum())
        }
    }

    @Test
    fun `nothing counted gives no rays and bad values count as zero`() {
        assertEquals(listOf(0, 0), FanAllocation.largestRemainder(listOf(0.0, 0.0)))
        assertEquals(listOf(0, 100), FanAllocation.largestRemainder(listOf(-5.0, 2.0)))
        assertEquals(listOf(0, 100), FanAllocation.largestRemainder(listOf(Double.NaN, 2.0)))
        assertTrue(FanAllocation.largestRemainder(emptyList()).isEmpty())
    }
}
