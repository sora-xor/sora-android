package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPreferencesTest {

    private val store = mockk<SoraPreferences>()
    private val preferences = PortfolioPreferences(store)

    @Test
    fun `scope and lens are remembered per device`() = runTest {
        coEvery { store.getString(PortfolioPreferences.SCOPE_KEY) } returns "all"
        coEvery { store.getString(PortfolioPreferences.LENS_KEY) } returns "networks"
        coEvery { store.putString(any(), any()) } just runs
        assertEquals(PortfolioScope.ALL, preferences.scope())
        assertEquals(PortfolioLens.NETWORKS, preferences.lens())
        preferences.setScope(PortfolioScope.SELECTED)
        preferences.setLens(PortfolioLens.WALLETS)
        coVerify { store.putString("portfolio.scope.v1", "selected") }
        coVerify { store.putString("portfolio.lens.v1", "wallets") }
    }

    @Test
    fun `nothing stored means the defaults apply`() = runTest {
        coEvery { store.getString(any()) } returns ""
        assertNull(preferences.scope())
        assertNull(preferences.lens())
        assertTrue(preferences.slotMaps().isEmpty())
    }

    @Test
    fun `slot maps round-trip and an unreadable value falls back to the seed`() {
        val maps =
            mapOf("all|tokens" to mapOf("xor" to 0, "val" to 1), "wallet:w|networks" to mapOf("sora2" to 0))
        assertEquals(maps, PortfolioPreferences.decodeSlotMaps(PortfolioPreferences.encodeSlotMaps(maps)))
        assertTrue(PortfolioPreferences.decodeSlotMaps("{not json").isEmpty())
        assertTrue(PortfolioPreferences.decodeSlotMaps("").isEmpty())
    }

    @Test
    fun `dashboard keys never hold wallet state`() {
        listOf(PortfolioPreferences.SCOPE_KEY, PortfolioPreferences.LENS_KEY, PortfolioPreferences.SLOTS_KEY).forEach {
            assertFalse(it, WalletPreferenceKeys.isWalletStateKey(it))
        }
    }
}
