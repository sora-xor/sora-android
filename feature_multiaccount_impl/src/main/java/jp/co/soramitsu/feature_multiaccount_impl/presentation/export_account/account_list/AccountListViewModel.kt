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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val interactor: MultiaccountInteractor,
    private val avatarGenerator: AccountAvatarGenerator,
    private val router: MainRouter,
    private val clipboardManager: BasicClipboardManager,
) : BaseViewModel() {

    private val _accountListScreenState = MutableLiveData<AccountListScreenState>()
    val accountListScreenState: LiveData<AccountListScreenState> = _accountListScreenState

    private val _showOnboardingFlowEvent = SingleLiveEvent<Unit>()
    val showOnboardingFlowEvent: LiveData<Unit> = _showOnboardingFlowEvent

    private val _isSwitchingAccount = MutableLiveData(false)
    val isSwitchingAccount: LiveData<Boolean> = _isSwitchingAccount

    private var toolbarActionModeEnabled = false

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_accounts,
        )
        interactor.flowSoraAccountsList()
            // The labels are an addition: when they cannot be read, the list is shown without.
            .combine(interactor.flowUnavailableWalletIds().catch { emit(emptySet()) }) { accounts, readOnly ->
                accounts to readOnly
            }
            .onEach { (accounts, readOnly) ->
                // With no account left (the last one was just removed and the app is about to
                // restart) there is no selected account to ask for.
                val currentAddress = if (accounts.isEmpty()) {
                    null
                } else {
                    interactor.getCurrentSoraAccount().substrateAddress
                }

                val accountList = accounts.map { soraAccount ->
                    ExportAccountData(
                        currentAddress == soraAccount.substrateAddress,
                        false,
                        avatarGenerator.createAvatar(soraAccount.substrateAddress, 40),
                        soraAccount,
                        readOnly = soraAccount.substrateAddress in readOnly,
                    )
                }

                _accountListScreenState.value =
                    AccountListScreenState(toolbarActionModeEnabled, accountList)
                setToolbarState(toolbarActionModeEnabled, 0)
            }
            // After the work above, so that a failure inside it is reported, not thrown out of
            // the view model's scope.
            .catch { onError(it) }
            .launchIn(viewModelScope)
    }

    fun onAccountOptionsClicked(address: String) {
        if (_isSwitchingAccount.value == true) return
        router.showAccountDetails(address)
    }

    fun onAccountClicked(address: String) {
        if (_isSwitchingAccount.value == true) return
        val account = _accountListScreenState.value?.accountList
            ?.find { it.account.substrateAddress == address } ?: return
        // Set this before launching so a second tap cannot queue another account change.
        _isSwitchingAccount.value = true
        viewModelScope.launch {
            tryCatchFinally(finally = { _isSwitchingAccount.value = false }) {
                interactor.setCurSoraAccount(account.account)
                router.popBackStack()
            }
        }
    }

    fun onAccountLongClicked(address: String) {
        if (_isSwitchingAccount.value == true) return
        clipboardManager.addToClipboard(address)
        copiedToast.trigger()
    }

    fun onAccountSelected(address: String) {
        if (_isSwitchingAccount.value == true) return
        accountListScreenState.value?.let { state ->
            val newList = state.accountList.map { accounts ->
                if (accounts.account.substrateAddress == address) {
                    accounts.copy(isSelectedAction = !accounts.isSelectedAction)
                } else {
                    accounts
                }
            }

            val selectedCount = newList.count { it.isSelectedAction }
            toolbarActionModeEnabled = selectedCount > 0

            _accountListScreenState.value =
                state.copy(isActionMode = toolbarActionModeEnabled, accountList = newList)
            setToolbarState(toolbarActionModeEnabled, selectedCount)
        }
    }

    fun onAddAccountClicked() {
        if (_isSwitchingAccount.value == true) return
        _showOnboardingFlowEvent.value = Unit
    }

    override fun onNavIcon() {
        onToolbarNavigation()
    }

    override fun onBackPressed() {
        onToolbarNavigation()
    }

    private fun onToolbarNavigation() {
        if (_isSwitchingAccount.value == true) return
        _accountListScreenState.value?.let {
            if (toolbarActionModeEnabled) {
                toolbarActionModeEnabled = false
            } else {
                router.popBackStack()
            }

            val newList = it.accountList.map { accounts ->
                accounts.copy(isSelectedAction = false)
            }

            _accountListScreenState.value =
                it.copy(isActionMode = toolbarActionModeEnabled, accountList = newList)
            setToolbarState(toolbarActionModeEnabled, 0)
        }
    }

    private fun setToolbarState(isChooserEnabled: Boolean, count: Int) {
        _toolbarState.value?.let { state ->
            if (isChooserEnabled) {
                _toolbarState.value = state.copy(
                    basic = state.basic.copy(
                        navIcon = R.drawable.ic_cross_red_16,
                        title = count.toString(),
                        actionLabel = R.string.common_backup,
                    ),
                )
            } else {
                _toolbarState.value = state.copy(
                    basic = state.basic.copy(
                        title = R.string.settings_accounts,
                        navIcon = R.drawable.ic_arrow_left,
                        actionLabel = null,
                    )
                )
            }
        }
    }

    override fun onAction() {
        if (_isSwitchingAccount.value == true) return
        _accountListScreenState.value?.let { state ->
            val addresses = state.accountList
                .filter { it.isSelectedAction }
                .map { it.account.substrateAddress }

            if (addresses.isNotEmpty()) {
                router.showExportJSONProtection(addresses)
            }
        }
    }
}
