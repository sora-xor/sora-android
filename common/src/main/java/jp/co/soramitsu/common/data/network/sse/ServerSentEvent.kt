/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.sse

import okhttp3.Request
import okhttp3.Response

interface ServerSentEvent {

    enum class Type {
        EVENT,
        SIGNAL
    }

    interface Listener {

        fun onOpen(sse: ServerSentEvent, response: Response)

        fun onMessage(sse: ServerSentEvent, id: String, eventType: Type?, data: String)

        fun onResponseError(sse: ServerSentEvent, throwable: Throwable, response: Response)

        fun onError(sse: ServerSentEvent, throwable: Throwable)

        fun onClosed(sse: ServerSentEvent)
    }

    fun request(): Request

    fun close()
}
