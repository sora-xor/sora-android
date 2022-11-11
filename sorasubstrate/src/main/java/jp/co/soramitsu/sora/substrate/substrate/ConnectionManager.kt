/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.fearless_utils.wsrpc.socket.StateObserver
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow

interface ConnectionManager {

    fun setAddress(address: String)

    fun observeAppState()

    fun start(url: String)

    fun isStarted(): Boolean

    fun stop()

    fun connectionState(): Flow<Boolean>

    fun networkState(): Flow<SocketStateMachine.State>

    fun switchUrl(url: String)

    fun resume()

    fun pause()

    fun addStateObserver(stateObserver: StateObserver)

    fun removeStateObserver(stateObserver: StateObserver)
}
