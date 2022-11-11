/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@FlowPreview
class PoolsManagerImpl(
    private val polkaswapInteractor: PolkaswapInteractor,
    private val coroutineManager: CoroutineManager,
) : PoolsManager {

    private val scope: CoroutineScope by lazy { coroutineManager.createSupervisorScope() }

    private var wasBound: Boolean = false
    private var observersCount: Int = 0

    private val loadingState = MutableStateFlow(false)

    private fun start() {
        scope.launch {
            loadingState.value = true
            try {
                polkaswapInteractor.updatePools()
            } catch (t: Throwable) {
                FirebaseWrapper.recordException(t)
            }
            loadingState.value = false
        }
        scope.launch {
            polkaswapInteractor.subscribePoolsChanges()
                .debounce(700)
                .catch { FirebaseWrapper.recordException(it) }
                .collectLatest {
                    loadingState.value = true
                    polkaswapInteractor.updatePools()
                    loadingState.value = false
                }
        }
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
