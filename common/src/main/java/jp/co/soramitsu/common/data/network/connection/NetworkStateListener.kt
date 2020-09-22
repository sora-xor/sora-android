/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class NetworkStateListener {

    enum class State {
        CONNECTED, DISCONNECTED
    }

    private val stateSubject = PublishSubject.create<State>()
    private var connectivityManager: ConnectivityManager? = null
    private val connections = mutableSetOf<String>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            super.onAvailable(network)
            network?.let {
                stateSubject.onNext(State.CONNECTED)
                connections.add(it.toString())
            }
        }

        override fun onLost(network: Network?) {
            super.onLost(network)
            network?.let {
                connections.remove(it.toString())
                if (connections.isEmpty()) {
                    stateSubject.onNext(State.DISCONNECTED)
                }
            }
        }
    }

    fun subscribe(context: Context): Observable<State> {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequestBuilder = NetworkRequest.Builder().build()

        connectivityManager!!.registerNetworkCallback(networkRequestBuilder, networkCallback)

        return stateSubject
    }

    fun release() {
        connectivityManager?.unregisterNetworkCallback(networkCallback)
    }
}