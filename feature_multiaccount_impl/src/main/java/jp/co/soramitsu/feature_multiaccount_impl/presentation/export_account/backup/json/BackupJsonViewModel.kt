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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.isPasswordSecure
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.launch

/** Characters kept from each end of an address when an account is named in a message. */
private const val SHORT_ADDRESS_CHARS = 8

class BackupJsonViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager,
    @Assisted("addresses") private val addresses: List<String>,
) : BaseViewModel() {

    @AssistedFactory
    interface BackupJsonViewModelFactory {
        fun create(
            @Assisted("addresses") addresses: List<String>,
        ): BackupJsonViewModel
    }

    private val _backupJsonScreenState = MutableLiveData<BackupJsonScreenState>()
    val backupJsonScreenState: LiveData<BackupJsonScreenState> = _backupJsonScreenState

    private val _jsonTextLiveData = MutableLiveData<Uri>()
    val jsonTextLiveData: LiveData<Uri> = _jsonTextLiveData

    init {
        _toolbarState.value = initMediumTitle2(
            title = R.string.export_json_download_json,
        )
        _backupJsonScreenState.value = BackupJsonScreenState(
            state = InputTextState(
                descriptionText = R.string.backup_password_requirments,
                label = R.string.export_json_input_label
            ),
            confirmationState = InputTextState(
                label = R.string.export_json_input_confirmation_label
            )
        )
    }

    fun passwordInputChanged(textFieldValue: TextFieldValue) {
        backupJsonScreenState.value?.let {
            val filteredValue = textFieldValue.copy(text = textFieldValue.text.filter { it != ' ' })
            val isSecure = filteredValue.text.isPasswordSecure()

            val descriptionText = if (isSecure) {
                R.string.backup_password_mandatory_reqs_fulfilled
            } else {
                R.string.backup_password_requirments
            }
            val (confirmationDescriptionText, isError) = getPasswordConfirmationDescriptionAndErrorStatus(
                filteredValue.text,
                it.confirmationState.value.text
            )

            _backupJsonScreenState.value = it.copy(
                state = it.state.copy(
                    value = filteredValue,
                    success = isSecure,
                    descriptionText = descriptionText,
                    error = !isSecure && filteredValue.text.isNotEmpty()
                ),
                confirmationState = it.confirmationState.copy(
                    error = isError,
                    descriptionText = confirmationDescriptionText,
                    success = !isError && confirmationDescriptionText != R.string.common_empty_string
                ),
                buttonEnabledState = isSecure && textFieldValue.text == it.confirmationState.value.text
            )
        }
    }

    fun confirmationInputChanged(textFieldValue: TextFieldValue) {
        backupJsonScreenState.value?.let {
            val filteredValue =
                textFieldValue.copy(text = textFieldValue.text.filter { it != ' ' })
            val (confirmationDescText, isError) = getPasswordConfirmationDescriptionAndErrorStatus(
                it.state.value.text,
                filteredValue.text
            )

            _backupJsonScreenState.value = it.copy(
                confirmationState = it.confirmationState.copy(
                    value = filteredValue,
                    success = !isError && filteredValue.text.isNotEmpty(),
                    descriptionText = confirmationDescText,
                    error = isError
                ),
                buttonEnabledState = !isError && it.state.value.text == textFieldValue.text
            )
        }
    }

    fun downloadJsonClicked() {
        val current = _backupJsonScreenState.value ?: return
        if (current.loading) return
        _backupJsonScreenState.value = current.copy(loading = true)
        viewModelScope.launch {
            try {
                // The interactor encrypts the file off the main thread.
                _jsonTextLiveData.value =
                    interactor.getJsonFileUri(addresses, current.confirmationState.value.text)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: MultiaccountInteractor.AccountsNotExportableException) {
                // Several accounts were chosen and some cannot be exported: no file is made,
                // and the message names them, so they need not be found by trial and error.
                alertDialogLiveData.value = Pair(
                    resourceManager.getString(
                        jp.co.soramitsu.common.R.string.export_json_keys_unusable_named_title
                    ),
                    resourceManager.getString(
                        jp.co.soramitsu.common.R.string.export_json_keys_unusable_named
                    ).format(error.accounts.joinToString("\n", transform = ::accountLine)),
                )
            } catch (error: Throwable) {
                // For example an account whose stored keys cannot be used: no file is made
                // and the screen says why and what to do.
                onError(error)
            } finally {
                _backupJsonScreenState.value = _backupJsonScreenState.value?.copy(loading = false)
            }
        }
    }

    /** One account in a message: its name and enough of its address to tell it apart. */
    private fun accountLine(account: SoraAccount): String {
        val address = account.substrateAddress
        val short = if (address.length <= SHORT_ADDRESS_CHARS * 2) {
            address
        } else {
            "${address.take(SHORT_ADDRESS_CHARS)}…${address.takeLast(SHORT_ADDRESS_CHARS)}"
        }
        return if (account.accountName.isBlank()) short else "${account.accountName} ($short)"
    }

    override fun onNavIcon() {
        router.leaveBackupJson()
    }

    override fun onBackPressed() {
        router.leaveBackupJson()
    }

    private fun getPasswordConfirmationDescriptionAndErrorStatus(
        password: String,
        passwordConfirmation: String
    ): Pair<Int, Boolean> {
        return when (passwordConfirmation) {
            "" -> R.string.common_empty_string to false
            password -> R.string.create_backup_password_matched to false
            else -> R.string.create_backup_password_not_matched to true
        }
    }
}
