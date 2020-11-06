package jp.co.soramitsu.common.data.network.sse

import okhttp3.Request

interface SseClient {

    fun newServerSentEvent(request: Request, listener: ServerSentEvent.Listener): ServerSentEvent
}