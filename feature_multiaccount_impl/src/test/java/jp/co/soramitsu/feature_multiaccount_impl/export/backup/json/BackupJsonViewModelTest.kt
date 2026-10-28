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
    private lateinit var resourceManager: jp.co.soramitsu.androidfoundation.resource.ResourceManager

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
        viewModel = BackupJsonViewModel(interactor, router, resourceManager, addresses)
    }

    @Test
    fun init() = runTest {
        viewModel.backupJsonScreenState.value?.let {
            assertEquals(initialState, it)
        }
    }

    @Test
    fun `an account that cannot be exported shows why instead of failing`() = runTest {
        val refused = jp.co.soramitsu.common.domain.SoraException.businessError(
            jp.co.soramitsu.common.domain.ResponseCode.ACCOUNT_KEYS_NOT_EXPORTABLE
        )
        org.mockito.kotlin.whenever(interactor.getJsonFileUri(addresses, "password"))
            .thenThrow(refused)
        viewModel.passwordInputChanged(TextFieldValue("password"))
        viewModel.confirmationInputChanged(TextFieldValue("password"))

        viewModel.downloadJsonClicked()
        testScheduler.advanceUntilIdle()

        // No file, and the message for an account whose stored keys cannot be used.
        assertEquals(null, viewModel.jsonTextLiveData.value)
        assertEquals(
            R.string.export_json_keys_unusable_title to R.string.export_json_keys_unusable,
            viewModel.errorFromResourceLiveData.value,
        )
    }

    @Test
    fun `accounts that cannot be exported with the others are named`() = runTest {
        val longAddress = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        org.mockito.kotlin.whenever(interactor.getJsonFileUri(addresses, "password")).thenAnswer {
            throw MultiaccountInteractor.AccountsNotExportableException(
                listOf(
                    jp.co.soramitsu.common.account.SoraAccount(longAddress, "Savings"),
                    jp.co.soramitsu.common.account.SoraAccount("address2", ""),
                )
            )
        }
        org.mockito.kotlin.whenever(
            resourceManager.getString(R.string.export_json_keys_unusable_named_title)
        ).thenReturn("Some accounts can't be exported")
        org.mockito.kotlin.whenever(
            resourceManager.getString(R.string.export_json_keys_unusable_named)
        ).thenReturn("These accounts can't be exported:\n%1\$s\nNo file was made.")
        viewModel.passwordInputChanged(TextFieldValue("password"))
        viewModel.confirmationInputChanged(TextFieldValue("password"))

        viewModel.downloadJsonClicked()
        testScheduler.advanceUntilIdle()

        // No file. The message names each account that has to be left out: its name and
        // enough of its address to tell it apart, or the address alone when it has no name.
        assertEquals(null, viewModel.jsonTextLiveData.value)
        assertEquals(
            "Some accounts can't be exported" to
                "These accounts can't be exported:\nSavings (cnTon6cV…wdUoRf4m)\naddress2\nNo file was made.",
            viewModel.alertDialogLiveData.value,
        )
        assertEquals(false, viewModel.backupJsonScreenState.value?.loading)
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
