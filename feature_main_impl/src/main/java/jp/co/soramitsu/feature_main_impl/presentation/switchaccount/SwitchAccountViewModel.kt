/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.switchaccount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SwitchAccountViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val avatarGenerator: AccountAvatarGenerator,
) : BaseViewModel() {

    private val _soraAccounts = MutableLiveData<List<SwitchAccountItem>>(emptyList())
    val soraAccounts: LiveData<List<SwitchAccountItem>> = _soraAccounts

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

    fun onBackClick() {
        router.popBackStack()
    }
}
