/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class WsConnectionManager(
    private val socket: SocketService,
    private val appStateProvider: AppStateProvider,
    private val coroutineManager: CoroutineManager,
    private val networkStateListener: NetworkStateListener,
    @ApplicationContext private val context: Context
) : ConnectionManager {

    private lateinit var address: String

    private val socketState =
        MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.State.Disconnected)

    private val connectivityManagerState =
        networkStateListener.subscribe(context).stateIn(
            coroutineManager.applicationScope,
            SharingStarted.Eagerly,
            NetworkStateListener.State.DISCONNECTED
        )

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
                            socket.start(address, true)
                        }
                    }
                    AppStateProvider.AppEvent.ON_RESUME -> {
                        socket.resume()
                    }
                    AppStateProvider.AppEvent.ON_PAUSE -> {
                        socket.pause()
                    }
                    AppStateProvider.AppEvent.ON_DESTROY -> {
                        socket.stop()
                    }
                }
            }
            .launchIn(coroutineManager.applicationScope)

        socket.networkStateFlow()
            .onEach {
                socketState.value = it
            }
            .launchIn(coroutineManager.applicationScope)
    }

    override val isConnected: Boolean
        get() = socketState.value is SocketStateMachine.State.Connected

    override val isNetworkAvailable: Boolean
        get() = connectivityManagerState.value === NetworkStateListener.State.CONNECTED

    override val connectionState: Flow<Boolean> =
        socketState.asStateFlow().map { it is SocketStateMachine.State.Connected }

    override val networkState: StateFlow<SocketStateMachine.State> =
        socketState.asStateFlow()

    override val isStarted: Boolean
        get() = socket.started()

    override fun switchUrl(url: String) {
        address = url
        socket.switchUrl(url)
    }
}
