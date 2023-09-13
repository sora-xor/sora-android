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
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_select_node_api.NodeManager
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
        viewModelScope.launch {
            assetsInteractor.flowCurSoraAccount()
                .catch { onError(it) }
                .collectLatest {
                    assetsInteractor.updateWhitelistBalances()
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
        viewModelScope.launch {
            withContext(coroutineManager.io) {
                assetsInteractor.getTokensList().map { it.id }.also { tokens ->
                    blockExplorerManager.getTokensLiquidity(tokens)
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
