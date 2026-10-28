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

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_select_node_api.NodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    nodeManager: NodeManager,
    private val pinCodeInteractor: PinCodeInteractor,
    private val globalSubscriptionManager: GlobalSubscriptionManager,
    private val blockExplorerManager: BlockExplorerManager,
    private val coroutineManager: CoroutineManager,
    private val poolsUpdateSubscription: PoolsUpdateSubscription,
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
                // Background work: its failures show in the connection banner, not in a dialog.
                .catch { onBackgroundError(it) }
                .flowOn(coroutineManager.io)
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
        viewModelScope.launch(coroutineManager.io) {
            assetsInteractor.flowCurSoraAccount()
                .catch { onBackgroundError(it) }
                .collectLatest {
                    try {
                        assetsInteractor.updateWhitelistBalances()
                        poolsUpdateSubscription.updateBasicPools()
                        assetsInteractor.getTokensList().map { it.id }.also { tokens ->
                            blockExplorerManager.getTokensLiquidity(tokens)
                        }
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
                        _badConnectionVisibilityLiveData.postValue(true)
                    }
                }
        }
        viewModelScope.launch {
            withContext(coroutineManager.io) {
                RepeatStrategyBuilder.infinite().repeat {
                    // A periodic refresh: a failed round is recorded and tried again; it never
                    // opens a dialog.
                    try {
                        blockExplorerManager.updateFiat()
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        onBackgroundError(error)
                    }
                    delay(20000)
                }
            }
        }
    }

    fun showPinFragment() {
        viewModelScope.launch {
            // Reading the PIN decrypts it. If the wallet key is busy or missing that fails; the
            // PIN screen is still shown (it checks again and never replaces the stored PIN) and
            // the app does not crash.
            _isPincodeUpdateNeeded.value = try {
                pinCodeInteractor.isPincodeUpdateNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
                false
            }
        }
    }
}
