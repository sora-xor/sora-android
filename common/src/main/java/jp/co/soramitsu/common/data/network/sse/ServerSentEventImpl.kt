/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.sse

import jp.co.soramitsu.common.domain.Serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class ServerSentEventImpl(
    private val client: OkHttpClient,
    private val request: Request,
    private val listener: ServerSentEvent.Listener,
    private val serializer: Serializer
) : ServerSentEvent {

    companion object {
        private const val COLON_DIVIDER = ':'
        private const val KEY_DATA = "data"
        private const val KEY_ID = "id"
        private const val KEY_EVENT = "event"
    }

    private var call: Call? = null

    init {
        call = client.newCall(request)
        enqueue()
    }

    private fun enqueue() {
        call!!.enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    openSse(response, 0)
                } else {
                    val throwable = IOException("${response.code()} ${response.message()}")
                    listener.onResponseError(this@ServerSentEventImpl, throwable, response)
                    listener.onClosed(this@ServerSentEventImpl)
                    close()
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                listener.onError(this@ServerSentEventImpl, exception)
                listener.onClosed(this@ServerSentEventImpl)
                close()
            }
        })
    }

    private fun openSse(response: Response, timeoutMillis: Long) {
        response.body()?.use { body ->
            val dataSource = body.source()
            val sseReader = Reader(dataSource).apply {
                setTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            }
            listener.onOpen(this, response)
            while (sseReader.canRead()) {
                sseReader.read()
            }
        }
    }

    override fun request(): Request {
        return request
    }

    override fun close() {
        if (call != null && !call!!.isCanceled) {
            call!!.cancel()
        }
    }

    private inner class Reader(
        private val source: BufferedSource
    ) {

        private val data = StringBuilder()
        private var eventType: ServerSentEvent.Type? = null
        private var eventId: String = ""

        private var canRead: Boolean = true

        fun canRead(): Boolean = canRead

        fun read() {
            if (call == null) {
                canRead = false
                return
            }
            if (call!!.isCanceled) {
                canRead = false
                return
            }

            try {
                val line = source.readUtf8LineStrict()
                processLine(line)
            } catch (exception: IOException) {
                listener.onError(this@ServerSentEventImpl, exception)
                listener.onClosed(this@ServerSentEventImpl)
                close()
                canRead = false
            }
        }

        fun setTimeout(timeout: Long, unit: TimeUnit) {
            source.timeout()?.timeout(timeout, unit)
        }

        private fun processLine(line: String) {
            if (line.isEmpty()) {
                dispatchEvent()
                return
            }
            val colonIndex = line.indexOf(COLON_DIVIDER)

            if (colonIndex == 0) {
                return
            }

            if (colonIndex == -1 || colonIndex + 1 >= line.length) {
                processField(line, "")
                return
            }

            val key = line.substring(0, colonIndex)
            val value = line.substring(colonIndex + 1)

            processField(key, value)
        }

        private fun processField(key: String, value: String) {
            when (key) {
                KEY_DATA -> {
                    data.append(value).append('\n')
                }
                KEY_ID -> {
                    eventId = value
                }
                KEY_EVENT -> {
                    val eventTypeRemote = serializer.deserialize(value, ServerSentEventRemote::class.java)
                    eventType = mapEventTypeRemoteToEventType(eventTypeRemote.type)
                }
            }
        }

        private fun mapEventTypeRemoteToEventType(serverSentEventTypeRemote: ServerSentEventRemote.Type): ServerSentEvent.Type {
            return when (serverSentEventTypeRemote) {
                ServerSentEventRemote.Type.EVENT -> ServerSentEvent.Type.EVENT
                ServerSentEventRemote.Type.SIGNAL -> ServerSentEvent.Type.SIGNAL
            }
        }

        private fun dispatchEvent() {
            if (data.isEmpty()) {
                return
            }
            val dataString = data.toString()
            listener.onMessage(this@ServerSentEventImpl, eventId, eventType, dataString)

            data.setLength(0)
            eventType = null
            eventId = ""
        }
    }
}
