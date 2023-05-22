/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
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
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _accountListScreenState = MutableLiveData<AccountListScreenState>()
    val accountListScreenState: LiveData<AccountListScreenState> = _accountListScreenState

    private val _showOnboardingFlowEvent = SingleLiveEvent<Unit>()
    val showOnboardingFlowEvent: LiveData<Unit> = _showOnboardingFlowEvent

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

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
        clipboardManager.addToClipboard("address", address)
        _copiedAddressEvent.trigger()
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
