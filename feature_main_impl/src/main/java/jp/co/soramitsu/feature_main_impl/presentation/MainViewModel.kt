/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel(
    healthChecker: HealthChecker,
    private val walletInteractor: WalletInteractor,
    private val runtimeManager: RuntimeManager,
) : BaseViewModel() {

    private var job: Job? = null
    private val _showInviteErrorTimeIsUpLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorTimeIsUpLiveData: LiveData<Event<Unit>> = _showInviteErrorTimeIsUpLiveData

    private val _showInviteErrorAlreadyAppliedLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorAlreadyAppliedLiveData: LiveData<Event<Unit>> =
        _showInviteErrorAlreadyAppliedLiveData

    private val _badConnectionVisibilityLiveData = MutableLiveData<Boolean>()
    val badConnectionVisibilityLiveData: LiveData<Boolean> = _badConnectionVisibilityLiveData

    private val _invitationCodeAppliedSuccessful = MutableLiveData<Event<Unit>>()
    val invitationCodeAppliedSuccessful: LiveData<Event<Unit>> = _invitationCodeAppliedSuccessful

    init {
        viewModelScope.launch {
            tryCatch {
                runtimeManager.start()

                healthChecker.observeHealthState()
                    .collectLatest {
                        if (it) {
                            subscribeToAccountStorage()
                        }
                        _badConnectionVisibilityLiveData.setValueIfNew(!it)
                    }
            }
        }
    }

    private fun subscribeToAccountStorage() {
        viewModelScope.launch {
            job?.cancel()
            job = walletInteractor.observeCurAccountStorage()
                .catch {
                    onError(it)
                }
                .onEach {
                    walletInteractor.updateBalancesVisibleAssets()
                }
                .launchIn(viewModelScope)
        }
    }
}
