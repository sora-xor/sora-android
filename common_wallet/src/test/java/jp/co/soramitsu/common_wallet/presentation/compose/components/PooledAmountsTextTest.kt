package jp.co.soramitsu.common_wallet.presentation.compose.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** A pool row breaks its pooled amounts only between the two amounts, never inside one. */
class PooledAmountsTextTest {

    @Test
    fun `each amount is kept whole and the delimiter can break`() {
        assertEquals("2 XOR - 5 VAL", pooledAmountsText("2 XOR - 5 VAL"))
    }

    @Test
    fun `a grouped amount stays whole too`() {
        assertEquals("1 234.5 XOR - 0.1 ETH", pooledAmountsText("1 234.5 XOR - 0.1 ETH"))
    }
}
