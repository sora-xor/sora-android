package jp.co.soramitsu.common.data.network.sse

import jp.co.soramitsu.common.domain.Serializer
import okhttp3.OkHttpClient
import okhttp3.Request

class SseClientImpl(
    private val client: OkHttpClient,
    private val serializer: Serializer
) : SseClient {

    override fun newServerSentEvent(request: Request, listener: ServerSentEvent.Listener): ServerSentEvent {
        return ServerSentEventImpl(client, request, listener, serializer)
    }
}