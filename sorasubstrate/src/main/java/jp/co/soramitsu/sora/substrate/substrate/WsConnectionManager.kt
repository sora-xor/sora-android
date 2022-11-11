/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import android.annotation.SuppressLint
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import jp.co.soramitsu.fearless_utils.wsrpc.socket.StateObserver
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@SuppressLint("CheckResult")
class WsConnectionManager(
    private val socket: SocketService,
    private val appStateProvider: AppStateProvider,
    private val coroutineManager: CoroutineManager,
) : ConnectionManager {

    private lateinit var address: String

    override fun setAddress(address: String) {
        this.address = address
    }

    override fun observeAppState() {
        appStateProvider.observeState()
            .distinctUntilChanged()
            .onEach {
                when (it) {
                    AppStateProvider.AppEvent.ON_CREATE -> {
                        if (this::address.isInitialized) {
                            start(address)
                        }
                    }
                    AppStateProvider.AppEvent.ON_RESUME -> {
                        resume()
                    }
                    AppStateProvider.AppEvent.ON_PAUSE -> {
                        pause()
                    }
                    AppStateProvider.AppEvent.ON_DESTROY -> {
                        stop()
                    }
                }
            }
            .launchIn(coroutineManager.applicationScope)
    }

    override fun connectionState(): Flow<Boolean> {
        return socket.networkStateFlow()
            .map { state ->
                state is SocketStateMachine.State.Connected
            }
            .distinctUntilChanged()
    }

    override fun networkState(): Flow<SocketStateMachine.State> {
        return socket.networkStateFlow()
    }

    override fun start(url: String) {
        address = url
        socket.start(url, true)
    }

    override fun isStarted(): Boolean = socket.started()

    override fun switchUrl(url: String) {
        address = url
        socket.switchUrl(url)
    }

    override fun stop() = socket.stop()

    override fun pause() = socket.pause()

    override fun resume() = socket.resume()

    override fun addStateObserver(stateObserver: StateObserver) {
        socket.addStateObserver(stateObserver)
    }

    override fun removeStateObserver(stateObserver: StateObserver) {
        socket.removeStateObserver(stateObserver)
    }
}
