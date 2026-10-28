package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.sora.R
import org.junit.Test

class NavigatorLanguageTest {
    @Test
    fun `change language opens the in-app chooser on Android 13 and newer too`() {
        mockkObject(BuildUtils)
        try {
            every { BuildUtils.sdkAtLeast(33) } returns true
            val controller = mockk<NavController>(relaxed = true)
            val navigator = Navigator().also { it.attachNavController(controller) }

            navigator.showSelectLanguage()

            verify(exactly = 1) { controller.navigate(R.id.selectLanguageFragment) }
            verify(exactly = 0) { controller.context }
        } finally {
            unmockkObject(BuildUtils)
        }
    }
}
