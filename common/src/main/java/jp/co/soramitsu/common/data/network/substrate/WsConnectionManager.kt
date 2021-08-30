/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate

import android.annotation.SuppressLint
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SuppressLint("CheckResult")
class WsConnectionManager(
    private val socket: SocketService,
    appStateProvider: AppStateProvider,
    coroutineManager: CoroutineManager,
) : ConnectionManager {

    init {
        coroutineManager.applicationScope.launch {
            appStateProvider.observeState()
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        start(OptionsProvider.url)
                    } else {
                        stop()
                    }
                }
        }
    }

    override fun connectionState(): Flow<Boolean> {
        return socket.networkStateFlow().map {
            it is SocketStateMachine.State.Connected
        }.distinctUntilChanged()
    }

    override fun start(url: String) = socket.start(url)

    override fun isStarted(): Boolean = socket.started()

    override fun stop() = socket.stop()
}
