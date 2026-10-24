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

    private fun quantityWithScale(scale: Int): String =
        "0." + "0".repeat(scale - 1) + "1"
}
