package jp.co.soramitsu.feature_main_impl.presentation.profile.information

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InformationViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule()
    private val router = mockk<MainRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(R.string.common_app_version) } returns "Version" }
    private val interactor = mockk<MainInteractor> { every { getAppVersion() } returns "4.5.6 (789)" }
    @Test fun informationShowsLocalizedVersionAndItsOwnStartDestination() {
        val vm = InformationViewModel(interactor, router, resources)
        assertEquals("Version 4.5.6 (789)", vm.state.appVersion)
        assertEquals(Routes.start, vm.startScreen())
        assertEquals(R.string.settings_information_title, vm.toolbarState.value!!.basic.title)
        verify { router wasNot Called }
    }
    @Test fun legalAndSupportActionsOpenTheReviewedCanonicalDocuments() {
        val vm = InformationViewModel(interactor, router, resources)
        vm.faq(); vm.terms(); vm.privacy()
        verifySequence { router.showWebView("", Const.SORA_FAQ_PAGE); router.showWebView("", Const.SORA_TERMS_PAGE); router.showWebView("", Const.SORA_PRIVACY_PAGE) }
    }
}
