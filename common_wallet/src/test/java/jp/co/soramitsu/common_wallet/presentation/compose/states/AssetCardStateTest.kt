package jp.co.soramitsu.common_wallet.presentation.compose.states

import java.math.BigDecimal
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.test_data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The asset rows of the home screen show the liquid amount. An account whose XOR is locked must
 * also see that it still holds it (audit F4, LIVE-2).
 */
class AssetCardStateTest {

    private val formatter = NumbersFormatter()

    @Test
    fun `an account whose XOR is all locked sees 0 XOR and the frozen amount in the same row`() {
        val allLocked = TestAssets.xorAsset().copy(
            balance = TestAssets.lockedBalance(
                transferable = BigDecimal.ZERO,
                locked = BigDecimal("110"),
            )
        )

        val row = mapAssetsToCardState(listOf(allLocked), formatter).single()

        assertEquals("0 XOR", row.assetAmount)
        // Before the fix the row had nothing but "0 XOR": the locked funds looked lost.
        assertEquals("110 XOR", row.frozenAmount)
    }

    @Test
    fun `reserved and locked XOR are named together, as on the asset page`() {
        val partlyLocked = TestAssets.xorAsset().copy(
            balance = TestAssets.lockedBalance(
                transferable = BigDecimal("12"),
                locked = BigDecimal("3.5"),
                reserved = BigDecimal("2"),
            )
        )

        val row = mapAssetsToCardState(listOf(partlyLocked), formatter).single()

        assertEquals("12 XOR", row.assetAmount)
        assertEquals("5.5 XOR", row.frozenAmount)
    }

    @Test
    fun `an asset that is all liquid has no frozen amount`() {
        val rows = mapAssetsToCardState(
            listOf(TestAssets.xorAsset(BigDecimal("12")), TestAssets.valAsset(BigDecimal.ZERO)),
            formatter,
        )

        assertNull(rows[0].frozenAmount)
        assertNull(rows[1].frozenAmount)
    }
}
