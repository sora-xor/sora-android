/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.export.backup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.mockito.BDDMockito.given
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

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

    private lateinit var viewModel: BackupViewModel

    private val mnemo = "mne mo nic"
    private val seed = "seed"

    fun setUp(type: ExportProtectionViewModel.Type) = runTest {
        given(interactor.getMnemonic("address")).willReturn(mnemo)
        given(interactor.getSeed("address")).willReturn(seed)
        given(resourceManager.getString(R.string.mnemonic_title)).willReturn("Title")
        given(resourceManager.getString(R.string.backup_account_title)).willReturn("Backup Account")
        viewModel = BackupViewModel(interactor, resourceManager, router, type, "address")
    }

    @Test
    fun initWithSeed() = runTest {
        setUp(ExportProtectionViewModel.Type.SEED)

        viewModel.backupScreenState.value?.let {
            assertEquals(it, BackupScreenState(seedString = "0x$seed"))
        }

        viewModel.toolbarState.value?.let {
            assertEquals(
                it.type,
                ToolbarType.CENTER_ALIGNED
            )

            assertEquals(
                it.title,
                "Backup Account"
            )
        }
    }

    @Test
    fun initWithPassphrase() = runTest {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)

        viewModel.backupScreenState.value?.let {
            assertEquals(it, BackupScreenState(mnemonicWords = listOf("mne", "mo", "nic")))
        }

        viewModel.toolbarState.value?.let {
            assertEquals(
                it.type,
                ToolbarType.CENTER_ALIGNED
            )

            assertEquals(
                it.title,
                "Title"
            )
        }
    }

    @Test
    fun backupPressedWithSeed() {
        setUp(ExportProtectionViewModel.Type.SEED)
        viewModel.backupPressed()
        viewModel.toggleShareDialog.value?.let {
            assertEquals(it, "0x$seed")
        }
    }

    @Test
    fun backupPressedWithPassphrase() {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)
        viewModel.backupPressed()
        viewModel.toggleShareDialog.value?.let {
            assertEquals(it, mnemo)
        }
    }
}