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

package jp.co.soramitsu.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
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
) {

    private var socketSession: DefaultClientWebSocketSession? = null

    private val networkClient =
        HttpClient(OkHttp) {
            expectSuccess = true

            if (logging) {
                install(Logging) {
                    level = LogLevel.ALL
                    logger = Logger.SIMPLE
                }
            }

            install(ContentNegotiation) {
                json(
                    json = json,
                    contentType = ContentType.Any
                )
            }

            install(HttpTimeout) {
                this.requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                this.connectTimeoutMillis = connectTimeoutMillis
                this.socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            }

            install(WebSockets) {
                this.pingInterval = pingInterval
                this.maxFrameSize = maxFrameSize
                this.contentConverter =
                    KotlinxWebsocketSerializationConverter(json)
            }
        }

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
