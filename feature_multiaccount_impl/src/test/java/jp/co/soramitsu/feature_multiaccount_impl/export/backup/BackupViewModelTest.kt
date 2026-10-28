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

package jp.co.soramitsu.feature_multiaccount_impl.export.backup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.util.SensitiveClipboard
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R as commonR
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
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
    private lateinit var clipboardManager: SensitiveClipboard

    private lateinit var viewModel: BackupViewModel

    private val mnemo = "mne mo nic"
    private val seed = "seed"

    private fun setUp(
        type: ExportProtectionViewModel.Type,
        material: WalletBackupMaterial = WalletBackupMaterial(
            canSign = true,
            phraseWords = mnemo.split(" "),
            seedHex = seed,
            storedPhraseDoesNotMatch = false,
            storedSeedDoesNotMatch = false,
        ),
    ) = runTest {
        // One judgement of the stored secrets per screen: the Raw seed screen asks for the
        // material that carries the seed, the phrase screen for the plain material.
        if (type == ExportProtectionViewModel.Type.SEED) {
            given(interactor.getSeedBackupMaterial("address")).willReturn(material)
        } else {
            given(interactor.getBackupMaterial("address")).willReturn(material)
        }
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
    fun theRawSeedScreenJudgesTheStoredSecretsOnce() = runTest {
        setUp(ExportProtectionViewModel.Type.SEED)

        assertEquals(
            BackupScreenState(seedString = "0x$seed"),
            viewModel.backupScreenState.getOrAwaitValue(),
        )
        // One call that carries the seed; no second or third judgement for the same screen.
        verify(interactor).getSeedBackupMaterial("address")
        org.mockito.Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun thePassphraseScreenJudgesTheStoredSecretsOnce() = runTest {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)

        viewModel.backupScreenState.getOrAwaitValue()
        verify(interactor).getBackupMaterial("address")
        org.mockito.Mockito.verifyNoMoreInteractions(interactor)
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
    fun nothingIsShownWhenNoStoredSecretMatchesTheAccount() = runTest {
        val noMatch = WalletBackupMaterial(
            canSign = true,
            phraseWords = emptyList(),
            seedHex = "",
            storedPhraseDoesNotMatch = true,
            storedSeedDoesNotMatch = false,
        )
        listOf(ExportProtectionViewModel.Type.PASSPHRASE, ExportProtectionViewModel.Type.SEED).forEach { type ->
            setUp(type, noMatch)
            // Said plainly, with the JSON export as the way to back the account up.
            assertEquals(
                BackupScreenState(noMatchingSecret = true, canExportJson = true),
                viewModel.backupScreenState.getOrAwaitValue(),
            )
            viewModel.backupPressed()
            viewModel.exportJsonPressed()
        }
        org.mockito.Mockito.verifyNoInteractions(clipboardManager)
        verify(router, org.mockito.Mockito.times(2)).showExportJSONProtection(listOf("address"))
    }

    @Test
    fun aReadOnlyAccountWithoutAMatchingSecretIsNotOfferedAnExportThatCannotWork() = runTest {
        val readOnly = WalletBackupMaterial(
            canSign = false,
            phraseWords = emptyList(),
            seedHex = "",
            storedPhraseDoesNotMatch = false,
            storedSeedDoesNotMatch = false,
        )
        setUp(ExportProtectionViewModel.Type.PASSPHRASE, readOnly)
        assertEquals(
            BackupScreenState(noMatchingSecret = true, canExportJson = false),
            viewModel.backupScreenState.getOrAwaitValue(),
        )
        viewModel.exportJsonPressed()
        org.mockito.Mockito.verifyNoInteractions(router)
    }

    @Test
    fun backupPressedWithSeed() {
        setUp(ExportProtectionViewModel.Type.SEED)
        viewModel.backupPressed()
        verify(clipboardManager).copy("0x$seed")
        viewModel.copiedToast.getOrAwaitValue()
    }

    @Test
    fun backupPressedWithPassphrase() {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)
        viewModel.backupPressed()
        verify(clipboardManager).copy(mnemo)
        viewModel.copiedToast.getOrAwaitValue()
    }
}
