/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.switchaccount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SwitchAccountViewModel @Inject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MainRouter,
    private val avatarGenerator: AccountAvatarGenerator,
    private val clipboardManager: ClipboardManager,
) : BaseViewModel() {

    private val _soraAccounts = MutableLiveData<List<SwitchAccountItem>>(emptyList())
    val soraAccounts: LiveData<List<SwitchAccountItem>> = _soraAccounts

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    init {
        interactor.flowCurSoraAccount()
            .combine(interactor.flowSoraAccountsList()) { a, b -> a to b }
            .catch {
                onError(it)
            }
            .map { data ->
                data.second.map {
                    SwitchAccountItem(
                        avatarGenerator.createAvatar(it.substrateAddress, 35),
                        it.substrateAddress,
                        it.substrateAddress == data.first.substrateAddress
                    )
                }
            }
            .onEach {
                _soraAccounts.value = it
            }
            .launchIn(viewModelScope)
    }

    fun onAccountItemClick(item: SwitchAccountItem) {
        viewModelScope.launch {
            tryCatch {
                interactor.setCurSoraAccount(item.accountAddress)
                router.popBackStack()
            }
        }
    }

    fun onAccountItemLongClick(item: SwitchAccountItem) {
        clipboardManager.addToClipboard("address", item.accountAddress)
        _copiedAddressEvent.trigger()
    }

    fun onBackClick() {
        router.popBackStack()
    }
}
