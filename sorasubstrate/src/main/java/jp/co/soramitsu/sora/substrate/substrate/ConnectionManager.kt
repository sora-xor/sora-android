/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ConnectionManager {

    fun setAddress(address: String)

    fun observeAppState()

    val isStarted: Boolean

    val connectionState: Flow<Boolean>

    val isConnected: Boolean

    val isNetworkAvailable: Boolean

    val networkState: StateFlow<SocketStateMachine.State>

    fun switchUrl(url: String)
}
