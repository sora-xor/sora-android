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

package jp.co.soramitsu.feature_multiaccount_impl.export.protection

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class ExportProtectionViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var interactor: MultiaccountInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var exportProtectionViewModel: ExportProtectionViewModel

    private val type = ExportProtectionViewModel.Type.PASSPHRASE

    private fun material(words: Int) = WalletBackupMaterial(
        canSign = true,
        phraseWords = List(words) { "word$it" },
        seedHex = "",
        storedPhraseDoesNotMatch = false,
        storedSeedDoesNotMatch = false,
    )

    private fun viewModel(type: ExportProtectionViewModel.Type = this.type) =
        ExportProtectionViewModel(router, interactor, resourceManager, type, "address", listOf("address"))

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.export_protection_passphrase_description_neutral))
            .willReturn("the passphrase, if one matches")
        given(resourceManager.getString(R.string.export_protection_passphrase_description_words))
            .willReturn("the %1\$s-word passphrase")
        given(interactor.getBackupMaterial("address")).willReturn(material(12))
        exportProtectionViewModel = viewModel()
    }

    @Test
    fun `the warning names the number of words this account's phrase really has`() = runTest {
        // It said "24-word" for every wallet.
        listOf(12, 15, 18, 21, 24).forEach { words ->
            given(interactor.getBackupMaterial("address")).willReturn(material(words))

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                "the $words-word passphrase",
                viewModel.exportProtectionScreenState.value?.descriptionText,
            )
        }
    }

    @Test
    fun `no number is named when no stored phrase matches the account`() = runTest {
        given(interactor.getBackupMaterial("address")).willReturn(material(0))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            "the passphrase, if one matches",
            viewModel.exportProtectionScreenState.value?.descriptionText,
        )
    }

    @Test
    fun `no number is named when the phrase cannot be read right now`() = runTest {
        given(interactor.getBackupMaterial("address")).willThrow(IllegalStateException("KEYSTORE_BUSY"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            "the passphrase, if one matches",
            viewModel.exportProtectionScreenState.value?.descriptionText,
        )
    }

    @Test
    fun `the seed and JSON warnings name no word count and read no phrase`() = runTest {
        val seed = viewModel(ExportProtectionViewModel.Type.SEED)
        val json = viewModel(ExportProtectionViewModel.Type.JSON)
        advanceUntilIdle()

        assertEquals(null, seed.exportProtectionScreenState.value?.descriptionText)
        assertEquals(null, json.exportProtectionScreenState.value?.descriptionText)
    }

    @Test
    fun onItemClickedCalled() = runTest {
        exportProtectionViewModel.onItemClicked(0)

        exportProtectionViewModel.exportProtectionScreenState.value?.let {
            assertTrue(it.selectableItemList.first().isSelected)
            assertFalse(it.selectableItemList[1].isSelected)
            assertFalse(it.selectableItemList[2].isSelected)
            assertFalse(it.isButtonEnabled)
        }

        exportProtectionViewModel.onItemClicked(1)

        exportProtectionViewModel.exportProtectionScreenState.value?.let {
            assertTrue(it.selectableItemList.first().isSelected)
            assertTrue(it.selectableItemList[1].isSelected)
            assertFalse(it.selectableItemList[2].isSelected)
            assertFalse(it.isButtonEnabled)
        }

        exportProtectionViewModel.onItemClicked(2)

        exportProtectionViewModel.exportProtectionScreenState.value?.let {
            assertTrue(it.selectableItemList.first().isSelected)
            assertTrue(it.selectableItemList[1].isSelected)
            assertTrue(it.selectableItemList[2].isSelected)
            assertTrue(it.isButtonEnabled)
        }

        exportProtectionViewModel.onItemClicked(0)

        exportProtectionViewModel.exportProtectionScreenState.value?.let {
            assertFalse(it.selectableItemList.first().isSelected)
            assertTrue(it.selectableItemList[1].isSelected)
            assertTrue(it.selectableItemList[2].isSelected)
            assertFalse(it.isButtonEnabled)
        }
    }

    @Test
    fun backPressed() {
        exportProtectionViewModel.backButtonPressed()
        verify(router).popBackStack()
    }
}
