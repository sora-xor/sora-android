/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
