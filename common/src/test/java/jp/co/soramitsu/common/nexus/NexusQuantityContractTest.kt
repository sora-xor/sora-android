package jp.co.soramitsu.common.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusQuantityContractTest {

    @Test
    fun `wire and input quantities share the 255 digit scale boundary`() {
        val maximumScale = quantityWithScale(NexusQuantityContract.MAX_SCALE)
        val overMaximumScale = quantityWithScale(NexusQuantityContract.MAX_SCALE + 1)

        assertTrue(NexusQuantityContract.isWireQuantity(maximumScale))
        assertFalse(NexusQuantityContract.isWireQuantity(overMaximumScale))
        assertTrue(NexusQuantityContract.isInputQuantity(maximumScale))
        assertFalse(NexusQuantityContract.isInputQuantity(overMaximumScale))
    }

    @Test
    fun `wire quantities reject normalization and exponent coercion`() {
        assertFalse(NexusQuantityContract.isWireQuantity(" 1"))
        assertFalse(NexusQuantityContract.isWireQuantity("1 "))
        assertFalse(NexusQuantityContract.isWireQuantity("1e3"))
        assertFalse(NexusQuantityContract.isWireQuantity("+1"))
    }

    @Test
    fun `draft amount accepts an unfinished decimal but the wire requires fraction digits`() {
        listOf("0", "1", "10.0", "0.000000001").forEach {
            assertTrue(it, NexusQuantityContract.isInputQuantity(it))
            assertTrue(it, NexusQuantityContract.isWireQuantity(it))
        }
        listOf("0.", "1.").forEach {
            assertTrue(it, NexusQuantityContract.isInputQuantity(it))
            assertFalse(it, NexusQuantityContract.isWireQuantity(it))
        }
    }

    @Test
    fun `both input and wire reject partial parses and non ASCII quantities`() {
        listOf(
            "", ".", ".5", "01", "00.1", "-1", "-0", "1,25", "1.2.3", "1XOR",
            "1e3", "1e+", "+1", "１", "١", "1\n", "1\r", "1\t", " 1", "1 ", "1\u0000",
        ).forEach {
            assertFalse(it, NexusQuantityContract.isInputQuantity(it))
            assertFalse(it, NexusQuantityContract.isWireQuantity(it))
        }
    }

    @Test
    fun `wire character budget applies before parsing to both input and read quantities`() {
        val limit = NexusQuantityContract.MAX_WIRE_CHARACTERS
        listOf("9".repeat(limit), "9".repeat(limit - 2) + ".1").forEach {
            assertTrue(NexusQuantityContract.isInputQuantity(it))
            assertTrue(NexusQuantityContract.isWireQuantity(it))
            assertFalse(NexusQuantityContract.isInputQuantity(it + "1"))
            assertFalse(NexusQuantityContract.isWireQuantity(it + "1"))
        }
    }

    @Test
    fun `asset scale limits count trailing zeros and never round excess precision`() {
        assertTrue(NexusQuantityContract.hasScaleAtMost("1", 0))
        assertFalse(NexusQuantityContract.hasScaleAtMost("1.0", 0))
        assertTrue(NexusQuantityContract.hasScaleAtMost("1.00", 2))
        assertFalse(NexusQuantityContract.hasScaleAtMost("1.000", 2))
        assertFalse(NexusQuantityContract.hasScaleAtMost("1.", 2))
        assertTrue(NexusQuantityContract.isTairaXorQuantity("0.000000001"))
        assertFalse(NexusQuantityContract.isTairaXorQuantity("0.0000000001"))
        assertFalse(NexusQuantityContract.isTairaXorQuantity("1.0000000000"))
    }

    private fun quantityWithScale(scale: Int): String =
        "0." + "0".repeat(scale - 1) + "1"
}
