package jp.co.soramitsu.common.presentation.compose.components

import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The classic toolbar presets the restored 3.8.6 screens start from. */
class ToolbarPresetsTest {

    @Test
    fun `a screen without a toolbar keeps the back arrow for when it is shown`() {
        val state = initNoToolbar()

        assertFalse(state.basic.visibility)
        assertEquals("", state.basic.title)
        assertEquals(R.drawable.ic_arrow_left, state.basic.navIcon)
        assertTrue(state.type is SoramitsuToolbarType.SmallCentered)
    }

    @Test
    fun `a title-only toolbar has no way back`() {
        val state = initSmallTitleOnly("Assets")

        assertTrue(state.basic.visibility)
        assertEquals("Assets", state.basic.title)
        assertNull(state.basic.navIcon)
        assertTrue(state.type is SoramitsuToolbarType.Small)
    }

    @Test
    fun `small and medium titled toolbars both go back`() {
        val small = initSmallTitle2(R.string.common_edit)
        val medium = initMediumTitle2("More")

        assertEquals(R.string.common_edit, small.basic.title)
        assertEquals(R.drawable.ic_arrow_left, small.basic.navIcon)
        assertTrue(small.type is SoramitsuToolbarType.Small)
        assertEquals("More", medium.basic.title)
        assertTrue(medium.type is SoramitsuToolbarType.Medium)
    }
}
