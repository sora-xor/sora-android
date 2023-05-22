/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var exportProtectionViewModel: ExportProtectionViewModel

    private val type = ExportProtectionViewModel.Type.PASSPHRASE

    @Before
    fun setUp() = runTest {
        exportProtectionViewModel =
            ExportProtectionViewModel(router, resourceManager, type, "address", listOf("address"))
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