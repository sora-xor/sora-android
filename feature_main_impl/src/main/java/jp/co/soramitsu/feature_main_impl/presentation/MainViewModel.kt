/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    nodeManager: NodeManager,
    private val walletInteractor: WalletInteractor,
    private val pinCodeInteractor: PinCodeInteractor,
    private val globalSubscriptionManager: GlobalSubscriptionManager,
    private val blockExplorerManager: BlockExplorerManager,
    private val coroutineManager: CoroutineManager,
) : BaseViewModel() {

    private val _showInviteErrorTimeIsUpLiveData = SingleLiveEvent<Unit>()
    val showInviteErrorTimeIsUpLiveData: LiveData<Unit> = _showInviteErrorTimeIsUpLiveData

    private val _showInviteErrorAlreadyAppliedLiveData = SingleLiveEvent<Unit>()
    val showInviteErrorAlreadyAppliedLiveData: LiveData<Unit> =
        _showInviteErrorAlreadyAppliedLiveData

    private val _badConnectionVisibilityLiveData = MutableLiveData<Boolean>()
    val badConnectionVisibilityLiveData: LiveData<Boolean> = _badConnectionVisibilityLiveData

    private val _invitationCodeAppliedSuccessful = SingleLiveEvent<Unit>()
    val invitationCodeAppliedSuccessful: LiveData<Unit> = _invitationCodeAppliedSuccessful

    private val _isPincodeUpdateNeeded = MutableLiveData<Boolean>()
    val isPincodeUpdateNeeded: LiveData<Boolean> = _isPincodeUpdateNeeded

    init {
        viewModelScope.launch {
            globalSubscriptionManager
                .start()
                .catch { onError(it) }
                .collect()
        }
        viewModelScope.launch {
            tryCatch {
                nodeManager.connectionState
                    .collectLatest {
                        _badConnectionVisibilityLiveData.setValueIfNew(!it)
                    }
            }
        }
        viewModelScope.launch {
            assetsInteractor.flowCurSoraAccount()
                .catch { onError(it) }
                .collectLatest {
                    assetsInteractor.updateWhitelistBalances(true)
                }
        }
        viewModelScope.launch {
            withContext(coroutineManager.io) {
                RepeatStrategyBuilder.infinite().repeat {
                    tryCatch {
                        blockExplorerManager.updateFiat()
                    }
                    delay(10000)
                }
            }
        }
    }

    fun showPinFragment() {
        viewModelScope.launch {
            _isPincodeUpdateNeeded.value = pinCodeInteractor.isPincodeUpdateNeeded()
        }
    }
}
