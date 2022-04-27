/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@FlowPreview
class PoolsManagerImpl(
    private val polkaswapInteractor: PolkaswapInteractor
) : PoolsManager {

    private lateinit var scope: CoroutineScope

    private var wasBound: Boolean = false
    private var observersCount: Int = 0

    private val loadingState = MutableStateFlow(false)

    private fun start() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            loadingState.value = true
            polkaswapInteractor.updatePools()
            loadingState.value = false
        }
        polkaswapInteractor.subscribePoolsChanges()
            .distinctUntilChanged()
            .debounce(500)
            .onEach {
                loadingState.value = true
                polkaswapInteractor.updatePools()
                loadingState.value = false
            }
            .launchIn(scope)
    }

    override fun isLoading(): Flow<Boolean> = loadingState.asStateFlow()

    override fun bind() {
        if (!wasBound) {
            wasBound = true
            start()
        }
        observersCount++
    }

    override fun unbind() {
        observersCount--
        if (wasBound && observersCount == 0) {
            cleanUp()
        }
    }

    private fun cleanUp() {
        wasBound = false
        scope.cancel()
    }
}
