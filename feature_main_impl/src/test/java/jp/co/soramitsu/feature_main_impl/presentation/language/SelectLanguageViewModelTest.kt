package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.language.model.LanguageItem
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SelectLanguageViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var selectLanguageViewModel: SelectLanguageViewModel
    private val languages = mutableListOf(
        Language("ru", R.string.common_russian, R.string.common_russian_native)
    )
    private val languageItems = mutableListOf(
        LanguageItem("ru", "Русский", "Русский", true)
    )

    @Before fun setUp() {
        given(interactor.getAvailableLanguagesWithSelected())
            .willReturn(Single.just(Pair(languages, languages.first().iso)))
        given(resourceManager.getString(anyInt())).willReturn("Русский")

        selectLanguageViewModel = SelectLanguageViewModel(interactor, router, resourceManager)
    }

    @Test fun `init successful`() {
        assertEquals(languageItems, selectLanguageViewModel.languagesLiveData.value)
    }

    @Test fun `language selected`() {
        given(interactor.changeLanguage(languages.first().iso)).willReturn(Single.just(languages.first().iso))

        selectLanguageViewModel.languageSelected(languageItems.first())

        selectLanguageViewModel.languageChangedLiveData.observeForever {
            assertEquals("ru", it.peekContent())
        }

        assertEquals("ru", selectLanguageViewModel.languageChangedLiveData.value?.peekContent())
    }

    @Test fun `back pressed`() {
        selectLanguageViewModel.onBackPressed()

        verify(router).popBackStack()
    }
}