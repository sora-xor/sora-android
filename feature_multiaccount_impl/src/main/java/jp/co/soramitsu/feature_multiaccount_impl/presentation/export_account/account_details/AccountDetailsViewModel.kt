/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AccountDetailsViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MainRouter,
    resourceManager: ResourceManager,
    @Assisted("address") private val address: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AccountDetailsViewModelFactory {
        fun create(
            @Assisted("address") address: String
        ): AccountDetailsViewModel
    }

    private val _accountDetailsScreenState = MutableLiveData<AccountDetailsScreenState>()
    val accountDetailsScreenState: LiveData<AccountDetailsScreenState> = _accountDetailsScreenState

    private val changeNameFlow = MutableStateFlow("")

    init {
        _toolbarState.value = ToolbarState(
            type = ToolbarType.SMALL,
            title = resourceManager.getString(R.string.common_account)
        )

        viewModelScope.launch {
            val account = interactor.getSoraAccount(address)
            val isMnemonicAvailable = interactor.getMnemonic(account).isNotEmpty()
            _accountDetailsScreenState.value = AccountDetailsScreenState(
                InputTextState(
                    value = TextFieldValue(account.accountName),
                    label = resourceManager.getString(R.string.common_name)
                ),
                isMnemonicAvailable
            )
        }

        viewModelScope.launch {
            changeNameFlow
                .debounce(500)
                .drop(1)
                .collectLatest {
                    if (it.toByteArray().size <= OptionsProvider.nameByteLimit) {
                        interactor.updateName(address, it)
                    }
                }
        }
    }

    fun onNameChange(textValue: TextFieldValue) {
        _accountDetailsScreenState.value?.let {
            _accountDetailsScreenState.value = it.copy(
                accountNameState = it.accountNameState.copy(value = textValue),
            )
            changeNameFlow.value = textValue.text
        }
    }

    fun onShowPassphrase() {
        _accountDetailsScreenState.value?.let {
            router.showExportPassphraseProtection(address)
        }
    }

    fun onShowRawSeed() {
        _accountDetailsScreenState.value?.let {
            router.showExportSeedProtection(address)
        }
    }

    fun onExportJson() {
        _accountDetailsScreenState.value?.let {
            router.showExportJSONProtection(mutableListOf(address))
        }
    }

    fun onLogout() {
        _accountDetailsScreenState.value?.let {
            router.showPinForLogout(address)
        }
    }
}
