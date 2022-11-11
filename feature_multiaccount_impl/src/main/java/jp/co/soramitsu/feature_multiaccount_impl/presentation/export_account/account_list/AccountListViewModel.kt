/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val defaultToolbarState = ToolbarState(
        type = ToolbarType.SMALL,
        title = resourceManager.getString(R.string.settings_accounts)
    )

    private var toolbarChooserEnabled = false

    init {
        _toolbarState.value = defaultToolbarState

        interactor.flowSoraAccountsList()
            .catch { onError(it) }
            .onEach {
                val currentAddress = interactor.getCurrentSoraAccount().substrateAddress

                val accountList = it.map {
                    ExportAccountData(
                        currentAddress == it.substrateAddress,
                        false,
                        avatarGenerator.createAvatar(it.substrateAddress, 40),
                        it.substrateAddress,
                        it.accountName
                    )
                }

                _accountListScreenState.value =
                    AccountListScreenState(toolbarChooserEnabled, accountList)
                setToolbarState(toolbarChooserEnabled, 0)
            }.launchIn(viewModelScope)
    }

    fun onAccountOptionsClicked(address: String) {
        router.showAccountDetails(address)
    }

    fun onAccountClicked(address: String) {
        viewModelScope.launch {
            tryCatch {
                interactor.setCurSoraAccount(address)
                router.popBackStack()
            }
        }
    }

    fun onAccountLongClicked(address: String) {
        clipboardManager.addToClipboard("address", address)
        _copiedAddressEvent.trigger()
    }

    fun onAccountSelected(address: String) {
        accountListScreenState.value?.let {
            val newList = it.accountList.map { accounts ->
                if (accounts.address == address) {
                    accounts.copy(isSelected = !accounts.isSelected)
                } else {
                    accounts
                }
            }

            val selectedCount = newList.count { it.isSelected }
            toolbarChooserEnabled = selectedCount > 0

            _accountListScreenState.value = it.copy(chooserActivated = toolbarChooserEnabled, accountList = newList)
            setToolbarState(toolbarChooserEnabled, selectedCount)
        }
    }

    fun onAddAccountClicked() {
        _showOnboardingFlowEvent.value = Unit
    }

    fun onToolbarNavigation() {
        _accountListScreenState.value?.let {
            if (toolbarChooserEnabled) {
                toolbarChooserEnabled = false
            } else {
                router.popBackStack()
            }

            val newList = it.accountList.map { accounts ->
                accounts.copy(isSelected = false)
            }

            _accountListScreenState.value =
                it.copy(chooserActivated = toolbarChooserEnabled, accountList = newList)
            setToolbarState(toolbarChooserEnabled, 0)
        }
    }

    private fun setToolbarState(isChooserEnabled: Boolean, count: Int) {
        if (isChooserEnabled) {
            _toolbarState.value = defaultToolbarState.copy(
                navIcon = R.drawable.ic_cross_red_16,
                title = count.toString(),
                action = resourceManager.getString(R.string.common_backup),
            )
        } else {
            _toolbarState.value = defaultToolbarState
        }
    }

    override fun onToolbarAction() {
        _accountListScreenState.value?.let {
            val addresses = it.accountList
                .filter { it.isSelected }
                .map { it.address }

            if (addresses.isNotEmpty()) {
                router.showExportJSONProtection(addresses)
            }
        }
    }
}
