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

package jp.co.soramitsu.feature_multiaccount_impl.export.backup.json

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json.BackupJsonViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class BackupJsonViewModelTest {

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
    private lateinit var uri: Uri

    private lateinit var viewModel: BackupJsonViewModel

    private val addresses = listOf("address1", "address2")

    private val initialState = BackupJsonScreenState(
        state = InputTextState(
            descriptionText = R.string.backup_password_requirments,
            label = R.string.export_json_input_label
        ),
        confirmationState = InputTextState(
            label = R.string.export_json_input_confirmation_label
        )
    )

    @Before
    fun setUp() = runTest {
        viewModel = BackupJsonViewModel(interactor, router, addresses)
    }

    @Test
    fun init() = runTest {
        viewModel.backupJsonScreenState.value?.let {
            assertEquals(initialState, it)
        }
    }

    @Test
    fun passwordInputChangedCalled() {
        val textFieldValue = TextFieldValue("password")

        val expectedState = initialState.copy(
            state = initialState.state.copy(
                value = textFieldValue,
                descriptionText = R.string.backup_password_mandatory_reqs_fulfilled,
                success = true
            ),
            confirmationState = initialState.confirmationState.copy(
                descriptionText = R.string.common_empty_string
            ),
            buttonEnabledState = false
        )

        viewModel.passwordInputChanged(textFieldValue)

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                it,
                expectedState
            )
        }
    }

    @Test
    fun confirmationInputChangedCalled() {
        val textFieldValue = TextFieldValue("password")

        viewModel.passwordInputChanged(textFieldValue)
        viewModel.confirmationInputChanged(textFieldValue)
        val expectedState = initialState.copy(
            state = initialState.state.copy(
                value = textFieldValue,
                descriptionText = R.string.backup_password_mandatory_reqs_fulfilled,
                success = true
            ),
            confirmationState = initialState.confirmationState.copy(
                value = textFieldValue,
                descriptionText = R.string.create_backup_password_matched,
                success = true
            ),
            buttonEnabledState = true
        )

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                expectedState,
                it
            )
        }
    }

    @Test
    fun confirmationInputChangedWithButtonDisabledCalled() {
        val textFieldValue = TextFieldValue("password")
        val textFieldValue2 = TextFieldValue("password2")

        val expectedState = initialState.copy(
            state = initialState.state.copy(
                value = textFieldValue,
                descriptionText = R.string.backup_password_mandatory_reqs_fulfilled,
                success = true
            ),
            confirmationState = initialState.confirmationState.copy(
                value = textFieldValue2,
                descriptionText = R.string.create_backup_password_not_matched,
                success = false,
                error = true
            ),
            buttonEnabledState = false
        )

        viewModel.passwordInputChanged(textFieldValue)
        viewModel.confirmationInputChanged(textFieldValue2)

        viewModel.backupJsonScreenState.value?.let {
            assertEquals(
                expectedState,
                it
            )
        }
    }

    @Test
    fun downloadJsonClickedCalled() = runTest {
        val textFieldValue2 = TextFieldValue("text2")
        viewModel.confirmationInputChanged(textFieldValue2)
        given(interactor.getJsonFileUri(addresses, "text2")).willReturn(uri)

        viewModel.downloadJsonClicked()

        viewModel.jsonTextLiveData.value?.let {
            assertEquals(it, uri)
        }
    }
}
