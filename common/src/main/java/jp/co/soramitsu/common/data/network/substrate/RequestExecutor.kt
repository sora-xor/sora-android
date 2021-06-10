package jp.co.soramitsu.common.data.network.substrate

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import io.reactivex.Single
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class SocketSingleRequestExecutor(
    private val jsonMapper: Gson,
    private val wsFactory: WebSocketFactory,
    private val resourceManager: ResourceManager
) {
    fun <R> executeRequest(
        request: RpcRequest,
        url: String,
        mapper: ResponseMapper<R>
    ): Single<R> {
        return executeRequest(request, url)
            .map { mapper.map(it, jsonMapper) }
    }

    private fun executeRequest(
        request: RpcRequest,
        url: String
    ): Single<RpcResponse> {
        val webSocket: WebSocket = wsFactory.createSocket(url)

        return Single.create<RpcResponse> { emitter ->
            webSocket.addListener(object : WebSocketAdapter() {
                override fun onTextMessage(websocket: WebSocket, text: String) {
                    val response = jsonMapper.fromJson(text, RpcResponse::class.java)
                    emitter.onSuccess(response)
                    webSocket.disconnect()
                }

                override fun onError(websocket: WebSocket, cause: WebSocketException) {
                    emitter.tryOnError(cause)
                }
            })
            webSocket.connect()
            webSocket.sendText(jsonMapper.toJson(request))
        }.doOnDispose { webSocket.disconnect() }
            .onErrorResumeNext {
                Single.error(SoraException.networkError(resourceManager, it))
            }
    }
}

fun <T> SocketService.singleRequest(
    request: RuntimeRequest,
    gson: Gson,
    mapper: ResponseMapper<T>
): Single<T> =
    singleRequest(request).map { mapper.map(it, gson) }

fun SocketService.singleRequest(request: RuntimeRequest): Single<RpcResponse> =
    Single.create { singleEmitter ->
        val cancellable = this.executeRequest(
            runtimeRequest = request,
            callback = object : SocketService.ResponseListener<RpcResponse> {
                override fun onError(throwable: Throwable) {
                    singleEmitter.onError(throwable)
                }

                override fun onNext(response: RpcResponse) {
                    singleEmitter.onSuccess(response)
                }
            }
        )
        singleEmitter.setCancellable { cancellable.cancel() }
    }
