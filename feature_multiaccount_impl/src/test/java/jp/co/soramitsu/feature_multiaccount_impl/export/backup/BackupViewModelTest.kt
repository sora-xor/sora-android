/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.backup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ext.addHexPrefix
import jp.co.soramitsu.common.R as commonR
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class BackupViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var interactor: MultiaccountInteractor

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var viewModel: BackupViewModel

    private val mnemo = "mne mo nic"
    private val seed = "seed"

    private fun setUp(type: ExportProtectionViewModel.Type) = runTest {
        given(interactor.getMnemonic("address")).willReturn(mnemo)
        given(interactor.getSeed("address")).willReturn(seed)
        viewModel =
            BackupViewModel(interactor, resourceManager, router, clipboardManager, type, "address")
    }

    @Test
    fun initWithSeed() = runTest {
        setUp(ExportProtectionViewModel.Type.SEED)

        val state = viewModel.backupScreenState.getOrAwaitValue()
        assertEquals(state, BackupScreenState(seedString = "0x$seed"))

        val toolbarstate = viewModel.toolbarState.getOrAwaitValue()
        assertTrue(
            toolbarstate.type is SoramitsuToolbarType.Medium
        )

        assertEquals(
            commonR.string.common_raw_seed,
            toolbarstate.basic.title,
        )
    }

    @Test
    fun initWithPassphrase() = runTest {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)

        viewModel.backupScreenState.value?.let {
            assertEquals(it, BackupScreenState(mnemonicWords = listOf("mne", "mo", "nic")))
        }

        val s = viewModel.toolbarState.getOrAwaitValue()
        assertTrue(
            s.type is SoramitsuToolbarType.Medium
        )

        assertEquals(
            commonR.string.common_passphrase_title,
            s.basic.title,
        )
    }

    @Test
    fun backupPressedWithSeed() {
        setUp(ExportProtectionViewModel.Type.SEED)
        viewModel.backupPressed()
        verify(clipboardManager).addToClipboard("Seed", seed.addHexPrefix())
        viewModel.copyEvent.getOrAwaitValue()
    }

    @Test
    fun backupPressedWithPassphrase() {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)
        viewModel.backupPressed()
        verify(clipboardManager).addToClipboard("Mnemonic", mnemo)
        viewModel.copyEvent.getOrAwaitValue()
    }
}