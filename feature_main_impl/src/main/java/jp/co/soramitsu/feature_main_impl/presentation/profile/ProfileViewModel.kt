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

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    interactor: MainInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val router: MainRouter,
    private val referralRouter: ReferralRouter,
    private val selectNodeRouter: SelectNodeRouter,
    private val productionFeatureManager: ProductionFeatureManager,
    nodeManager: NodeManager,
) : BaseViewModel() {

    private val _state = MutableStateFlow(
        initialProfileScreenState,
    )
    internal val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            tryCatch {
                val features = productionFeatureManager.getState()
                // Await the feature lookup before reading current presentation state. A
                // delayed result must not replace a newer node name or connection status.
                _state.update { it.copy(polkamarktVisible = features.polkamarktVisible) }
            }
        }
        interactor.flowSelectedNode().combine(nodeManager.connectionState) { node, connection ->
            node to connection
        }
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { (node, connection) ->
                delay(1000)
                _state.value = _state.value.copy(
                    nodeName = node?.name.orEmpty(),
                    nodeConnected = connection,
                )
            }
            .launchIn(viewModelScope)
    }

    fun showAccountList() {
        router.showAccountList()
    }

    fun showPolkamarkt() {
        polkaswapRouter.showPolkamarkt()
    }

    fun showSelectNode() {
        selectNodeRouter.showSelectNode()
    }

    fun showAppSettings() {
        router.showAppSettings()
    }

    fun showLogin() {
        router.showLoginSecurity()
    }

    fun showReferral() {
        referralRouter.showReferrals()
    }

    fun showAbout() {
        router.showInformation()
    }

    fun showDebugMenu() {
        router.showDebugMenu()
    }
}
