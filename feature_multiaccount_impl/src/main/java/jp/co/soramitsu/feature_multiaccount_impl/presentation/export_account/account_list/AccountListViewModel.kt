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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import kotlinx.coroutines.flow.catch
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

    private var toolbarActionModeEnabled = false

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_accounts,
        )
        interactor.flowSoraAccountsList()
            .catch { onError(it) }
            .onEach {
                val currentAddress = interactor.getCurrentSoraAccount().substrateAddress

                val accountList = it.map { soraAccount ->
                    ExportAccountData(
                        currentAddress == soraAccount.substrateAddress,
                        false,
                        avatarGenerator.createAvatar(soraAccount.substrateAddress, 40),
                        soraAccount,
                    )
                }

                _accountListScreenState.value =
                    AccountListScreenState(toolbarActionModeEnabled, accountList)
                setToolbarState(toolbarActionModeEnabled, 0)
            }.launchIn(viewModelScope)
    }

    fun onAccountOptionsClicked(address: String) {
        router.showAccountDetails(address)
    }

    fun onAccountClicked(address: String) {
        viewModelScope.launch {
            tryCatch {
                val account =
                    _accountListScreenState.value?.accountList?.find { it.account.substrateAddress == address }
                if (account != null) {
                    interactor.setCurSoraAccount(account.account)
                    router.popBackStack()
                }
            }
        }
    }

    fun onAccountLongClicked(address: String) {
        clipboardManager.addToClipboard(address)
        copiedToast.trigger()
    }

    fun onAccountSelected(address: String) {
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
        _showOnboardingFlowEvent.value = Unit
    }

    override fun onNavIcon() {
        onToolbarNavigation()
    }

    override fun onBackPressed() {
        onToolbarNavigation()
    }

    private fun onToolbarNavigation() {
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
