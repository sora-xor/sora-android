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

package jp.co.soramitsu.network

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import jp.co.soramitsu.xnetworking.networkclient.NetworkClientConfig
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import jp.co.soramitsu.xnetworking.networkclient.WebSocketClientConfig
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface WebSocketListener {

    suspend fun onResponse(response: WebSocketResponse)

    fun onSocketClosed()

    fun onConnected()
}

class WebSocket(
    private val url: String,
    private var listener: WebSocketListener,
    private val json: Json,
    connectTimeoutMillis: Long = 10_000,
    pingInterval: Long = 20,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    logging: Boolean = false,
    provider: SoramitsuHttpClientProvider
) {

    private var socketSession: DefaultClientWebSocketSession? = null

    private val networkClient = provider.provide(
        NetworkClientConfig(
            logging = logging,
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS,
            json = json,
            webSocketClientConfig = WebSocketClientConfig(
                pingInterval = pingInterval,
                maxFrameSize = maxFrameSize
            )
        )
    )

    private suspend fun DefaultClientWebSocketSession.listenIncomingMessages() {
        try {
            incoming.receiveAsFlow()
                .filter { it is Frame.Text }
                .collect { frame ->
                    frame as Frame.Text
                    val text = frame.readText()

                    this@WebSocket.listener.onResponse(response = WebSocketResponse(json = text))
                }
        } catch (e: Exception) {
            // println("Error while receiving: ${e.message}")
        }
    }

    suspend fun disconnect() {
        socketSession?.close()
    }

    suspend fun sendRequest(request: WebSocketRequest) {
        networkClient.webSocket(url) {
            try {
                socketSession = this
                listener.onConnected()

                launch {
                    socketSession?.send(Frame.Text(request.json))
                }

                listenIncomingMessages()
            } catch (e: Exception) {
                // println("Error while connecting")
            } finally {
                listener.onSocketClosed()
            }
        }
    }
}
