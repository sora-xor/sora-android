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

package jp.co.soramitsu.sora.substrate.substrate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.CoroutineManager
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
