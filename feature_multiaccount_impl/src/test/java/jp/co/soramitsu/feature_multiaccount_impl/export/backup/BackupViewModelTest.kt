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

package jp.co.soramitsu.feature_multiaccount_impl.export.backup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R as commonR
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
    private lateinit var clipboardManager: BasicClipboardManager

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
        verify(clipboardManager).addToClipboard("0x$seed")
        viewModel.copiedToast.getOrAwaitValue()
    }

    @Test
    fun backupPressedWithPassphrase() {
        setUp(ExportProtectionViewModel.Type.PASSPHRASE)
        viewModel.backupPressed()
        verify(clipboardManager).addToClipboard(mnemo)
        viewModel.copiedToast.getOrAwaitValue()
    }
}
