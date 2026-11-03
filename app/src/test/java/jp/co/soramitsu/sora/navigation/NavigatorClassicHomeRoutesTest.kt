package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.sora.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Where the classic Wallet home's Expand and the legacy claim prompt lead. */
class NavigatorClassicHomeRoutesTest {

    private val controller = mockk<NavController>(relaxed = true)
    private val navigator = Navigator().also { it.attachNavController(controller) }

    @Test
    fun `the assets card opens the full asset list`() {
        navigator.showAssetSettings()

        verify(exactly = 1) { controller.navigate(R.id.navAction_to_FullAssetListFragment) }
    }

    @Test
    fun `the claim screen opens on top of the home and keeps the home below it`() {
        val options = slot<NavOptions>()

        navigator.showClaim()

        verify(exactly = 1) { controller.navigate(R.id.claimFragment, null, capture(options)) }
        assertEquals(R.id.cardsHubFragment, options.captured.popUpToId)
        assertFalse(options.captured.isPopUpToInclusive())
    }
}
