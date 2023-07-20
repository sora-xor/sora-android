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
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
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

    private lateinit var exportProtectionViewModel: ExportProtectionViewModel

    private val type = ExportProtectionViewModel.Type.PASSPHRASE

    @Before
    fun setUp() = runTest {
        exportProtectionViewModel =
            ExportProtectionViewModel(router, type, "address", listOf("address"))
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
