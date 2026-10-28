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

package jp.co.soramitsu.sora.substrate.substrate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.networkStateFlow
import jp.co.soramitsu.xsubstrate.wsrpc.state.SocketStateMachine
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

    private val mutationTransport = Sora2MutationTransportCoordinator()

    private val socketState =
        MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.State.Disconnected)

    private val connectivityManagerState =
        networkStateListener.subscribe(context).stateIn(
            coroutineManager.applicationScope,
            SharingStarted.Eagerly,
            NetworkStateListener.State.DISCONNECTED
        )

    override fun setAddress(address: String) {
        mutationTransport.setAddress(address)
    }

    override fun observeAppState() {
        appStateProvider.observeState()
            .distinctUntilChanged()
            .onEach {
                when (it) {
                    AppStateProvider.AppEvent.ON_CREATE -> {
                        mutationTransport.requestedAddress()
                            ?.let { socket.start(it, true) }
                    }

                    AppStateProvider.AppEvent.ON_RESUME -> {
                        resumeOrStartSocket(
                            socket = socket,
                            requestedAddress = mutationTransport.requestedAddress(),
                        )
                    }

                    AppStateProvider.AppEvent.ON_PAUSE -> {
                        socket.pause()
                    }

                    AppStateProvider.AppEvent.ON_DESTROY -> {
                        mutationTransport.observeConnectedAddress(null)
                        socket.stop()
                    }
                }
            }
            .launchIn(coroutineManager.applicationScope)

        socket.networkStateFlow()
            .onEach {
                mutationTransport.observeConnectedAddress(
                    (it as? SocketStateMachine.State.Connected)?.url
                )
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
        mutationTransport.requestSwitch(url)?.let(socket::switchUrl)
    }

    override fun requireReviewedSora2MutationTransport() {
        mutationTransport.requireReviewedTransport()
    }

    override fun acquireReviewedSora2MutationTransport(): Sora2MutationTransportLease {
        val lease = mutationTransport.acquire()
        return Sora2MutationTransportLease {
            lease.closeAndRunDeferredSwitch(socket::switchUrl)
        }
    }
}

/**
 * A connection manager can now be constructed after the process has already reached ON_RESUME.
 * In that case there was no observer for ON_CREATE, so resume() alone cannot start a disconnected
 * socket. Keep the ordinary resume path for an existing socket and bootstrap only when necessary.
 */
internal fun resumeOrStartSocket(
    socket: SocketService,
    requestedAddress: String?,
) {
    if (socket.started()) {
        socket.resume()
    } else {
        requestedAddress?.let { socket.start(it, true) }
    }
}
