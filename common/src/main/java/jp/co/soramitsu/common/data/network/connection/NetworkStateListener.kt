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

package jp.co.soramitsu.common.data.network.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import jp.co.soramitsu.androidfoundation.format.safeCast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

class NetworkStateListener {

    enum class State {
        CONNECTED,
        DISCONNECTED
    }

    private val stateSubject = MutableStateFlow<State?>(null)
    private var connectivityManager: ConnectivityManager? = null
    private val connections = mutableSetOf<String>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            stateSubject.value = State.CONNECTED
            connections.add(network.toString())
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            connections.remove(network.toString())
            if (connections.isEmpty()) {
                stateSubject.value = State.DISCONNECTED
            }
        }
    }

    fun subscribe(context: Context): Flow<State> {
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE).safeCast<ConnectivityManager>()
                ?.apply {
                    val networkRequestBuilder = NetworkRequest.Builder().build()
                    registerNetworkCallback(networkRequestBuilder, networkCallback)
                }
        return stateSubject.asStateFlow().filterNotNull().distinctUntilChanged()
    }

    fun release() {
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        connectivityManager = null
    }
}
