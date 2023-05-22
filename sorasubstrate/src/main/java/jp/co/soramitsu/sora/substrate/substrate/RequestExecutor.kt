/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
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
