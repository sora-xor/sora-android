/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.launch

class BackupJsonViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MainRouter,
    @Assisted("addresses") private val addresses: List<String>,
    resourceManager: ResourceManager,
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
                label = resourceManager.getString(R.string.export_json_input_label)
            ),
            confirmationState = InputTextState(
                label = resourceManager.getString(R.string.export_json_input_confirmation_label)
            )
        )
    }

    fun passwordInputChanged(textFieldValue: TextFieldValue) {
        backupJsonScreenState.value?.let {
            _backupJsonScreenState.value = it.copy(
                state = it.state.copy(textFieldValue),
                buttonEnabledState = textFieldValue.text.isNotEmpty() && textFieldValue.text == it.confirmationState.value.text
            )
        }
    }

    fun confirmationInputChanged(textFieldValue: TextFieldValue) {
        backupJsonScreenState.value?.let {
            _backupJsonScreenState.value = it.copy(
                confirmationState = it.confirmationState.copy(textFieldValue),
                buttonEnabledState = textFieldValue.text.isNotEmpty() && it.state.value.text == textFieldValue.text
            )
        }
    }

    fun downloadJsonClicked() {
        viewModelScope.launch {
            _backupJsonScreenState.value?.let {
                _jsonTextLiveData.value =
                    interactor.getJsonFileUri(addresses, it.confirmationState.value.text)
            }
        }
    }

    override fun onNavIcon() {
        router.popBackStackToAccountList()
    }

    override fun onBackPressed() {
        router.popBackStackToAccountList()
    }
}
