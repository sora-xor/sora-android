package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AboutViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var aboutViewModel: AboutViewModel

    @Before
    fun setUp() {
        aboutViewModel = AboutViewModel(interactor, router, resourceManager)
    }

    @Test
    fun `init called`() = runBlockingTest {
        val version = "1.0"
        val title = "source"

        aboutViewModel.getAppVersion()

        aboutViewModel.sourceTitleLiveData.observeForever {
            assertEquals("$title (v$version)", it)
        }
    }

    @Test
    fun `back clicked`() {
        aboutViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test
    fun `opensource item clicked`() {
        val opensourceLink = "https://github.com/sora-xor/Sora-Android"

        aboutViewModel.openSourceClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(opensourceLink, res)
    }

    @Test
    fun `terms item clicked`() {
        aboutViewModel.termsClicked()

        verify(router).showTerms()
    }

    @Test
    fun `privacy item clicked`() {
        aboutViewModel.privacyClicked()

        verify(router).showPrivacy()
    }

    @Test
    fun `contacts item clicked`() {
        val email = "support@sora.org"

        aboutViewModel.contactsClicked()

        val res = aboutViewModel.openSendEmailEvent.getOrAwaitValue()
        assertEquals(email, res)
    }
}