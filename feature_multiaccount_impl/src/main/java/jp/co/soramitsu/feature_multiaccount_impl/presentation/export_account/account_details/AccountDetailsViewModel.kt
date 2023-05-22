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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
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
    private val clipboardManager: ClipboardManager,
    @Assisted("address") private val address: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AccountDetailsViewModelFactory {
        fun create(
            @Assisted("address") address: String
        ): AccountDetailsViewModel
    }

    private val _copyEvent = SingleLiveEvent<Unit>()
    val copyEvent: LiveData<Unit> = _copyEvent

    private val _accountDetailsScreenState = MutableLiveData(
        AccountDetailsScreenState(InputTextState(value = TextFieldValue("")), false, "")
    )
    val accountDetailsScreenState: LiveData<AccountDetailsScreenState> = _accountDetailsScreenState

    private val changeNameFlow = MutableStateFlow("")

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.account_options,
        )
        viewModelScope.launch {
            val account = interactor.getSoraAccount(address)
            val isMnemonicAvailable = interactor.getMnemonic(account).isNotEmpty()
            _accountDetailsScreenState.value = AccountDetailsScreenState(
                InputTextState(
                    value = TextFieldValue(account.accountName),
                    label = resourceManager.getString(R.string.personal_info_username_v1),
                    leadingIcon = R.drawable.ic_input_pencil_24,
                ),
                isMnemonicAvailable,
                address,
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

    fun onAddressCopy() {
        clipboardManager.addToClipboard("address", address)
        _copyEvent.trigger()
    }
}
