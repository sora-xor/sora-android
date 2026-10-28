/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
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
                0,
            )
        )
        given(resourceManager.getString(anyInt())).willReturn("Русский")

        selectLanguageViewModel = SelectLanguageViewModel(interactor, resourceManager)
    }

    @Test
    fun `init successful`() = runTest {
        advanceUntilIdle()
        assertEquals(languageItems, selectLanguageViewModel.state.items)
    }

    @Test
    fun `language selected`() = runTest {
        given(interactor.changeLanguage(languages.first().iso)).willReturn(languages.first().iso)

        selectLanguageViewModel.languageSelected(languageItems.first())
        advanceUntilIdle()
        val res = selectLanguageViewModel.languageChangedLiveData.getOrAwaitValue()
        assertEquals("ru", res)
    }
}
