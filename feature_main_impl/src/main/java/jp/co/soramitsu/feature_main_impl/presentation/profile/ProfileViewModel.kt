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
