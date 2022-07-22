/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.language.model.LanguageItem
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SelectLanguageViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var selectLanguageViewModel: SelectLanguageViewModel
    private val languages = mutableListOf(
        Language("ru", R.string.common_russian, R.string.common_russian_native)
    )
    private val languageItems = mutableListOf(
        LanguageItem("ru", "Русский", "Русский", true)
    )

    @Before
    fun setUp() = runTest {
        given(interactor.getAvailableLanguagesWithSelected()).willReturn(
            Pair(
                languages,
                languages.first().iso
            )
        )
        given(resourceManager.getString(anyInt())).willReturn("Русский")

        selectLanguageViewModel = SelectLanguageViewModel(interactor, router, resourceManager)
    }

    @Test
    fun `init successful`() {
        assertEquals(languageItems, selectLanguageViewModel.languagesLiveData.value)
    }

    @Test
    fun `language selected`() = runTest {
        given(interactor.changeLanguage(languages.first().iso)).willReturn(languages.first().iso)

        selectLanguageViewModel.languageSelected(languageItems.first())
        advanceUntilIdle()
        val res = selectLanguageViewModel.languageChangedLiveData.getOrAwaitValue()
        assertEquals("ru", res)
    }

    @Test
    fun `back pressed`() {
        selectLanguageViewModel.onBackPressed()

        verify(router).popBackStack()
    }
}