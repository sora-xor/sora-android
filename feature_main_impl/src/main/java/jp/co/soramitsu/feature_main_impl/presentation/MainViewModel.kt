/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    nodeManager: NodeManager,
    private val walletInteractor: WalletInteractor,
    private val pinCodeInteractor: PinCodeInteractor,
) : BaseViewModel() {

    private val _showInviteErrorTimeIsUpLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorTimeIsUpLiveData: LiveData<Event<Unit>> = _showInviteErrorTimeIsUpLiveData

    private val _showInviteErrorAlreadyAppliedLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorAlreadyAppliedLiveData: LiveData<Event<Unit>> =
        _showInviteErrorAlreadyAppliedLiveData

    private val _badConnectionVisibilityLiveData = MutableLiveData<Boolean>()
    val badConnectionVisibilityLiveData: LiveData<Boolean> = _badConnectionVisibilityLiveData

    private val _invitationCodeAppliedSuccessful = MutableLiveData<Event<Unit>>()
    val invitationCodeAppliedSuccessful: LiveData<Event<Unit>> = _invitationCodeAppliedSuccessful

    private val _isPincodeUpdateNeeded = MutableLiveData<Boolean>()
    val isPincodeUpdateNeeded: LiveData<Boolean> = _isPincodeUpdateNeeded

    init {
        viewModelScope.launch {
            tryCatch {
                nodeManager.connectionState()
                    .collectLatest {
                        _badConnectionVisibilityLiveData.setValueIfNew(!it)
                    }
            }
        }
        viewModelScope.launch {
            walletInteractor.flowCurSoraAccount()
                .catch { onError(it) }
                .distinctUntilChangedBy { it.substrateAddress }
                .collectLatest {
                    walletInteractor.updateWhitelistBalances()
                }
        }

        walletInteractor.observeCurAccountStorage()
            .catch {
                onError(it)
            }
            .onEach {
                walletInteractor.updateBalancesActiveAssets()
            }
            .launchIn(viewModelScope)
    }

    fun showPinFragment() {
        viewModelScope.launch {
            _isPincodeUpdateNeeded.value = pinCodeInteractor.isPincodeUpdateNeeded()
        }
    }
}
