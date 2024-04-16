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
