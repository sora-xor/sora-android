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

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.xsubstrate.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class SocketSingleRequestExecutor(
    private val jsonMapper: Gson,
    private val wsFactory: WebSocketFactory,
    private val resourceManager: ResourceManager
) {
    suspend fun <R> executeRequest(
        request: RpcRequest,
        url: String,
        mapper: ResponseMapper<R>
    ): R {
        val response = executeRequest(request, url)

        return withContext(Dispatchers.Default) {
            mapper.map(response, jsonMapper)
        }
    }

    suspend fun executeRequest(
        request: RpcRequest,
        url: String
    ): RpcResponse = withContext(Dispatchers.IO) {
        try {
            executeRequestInternal(request, url)
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun executeRequestInternal(
        request: RpcRequest,
        url: String
    ): RpcResponse = suspendCancellableCoroutine { cont ->

        val webSocket: WebSocket = wsFactory.createSocket(url)

        cont.invokeOnCancellation {
            webSocket.clearListeners()
            webSocket.disconnect()
        }

        webSocket.addListener(object : WebSocketAdapter() {
            override fun onTextMessage(websocket: WebSocket, text: String) {
                val response = jsonMapper.fromJson(text, RpcResponse::class.java)

                cont.resume(response, null)

                webSocket.disconnect()
            }

            override fun onError(websocket: WebSocket, cause: WebSocketException) {
                cont.resumeWithException(cause)
            }
        })

        webSocket.connect()

        webSocket.sendText(jsonMapper.toJson(request))
    }
}
