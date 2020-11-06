/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.repository

import com.google.gson.JsonParser
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import jp.co.soramitsu.common.data.network.sse.ServerSentEvent
import jp.co.soramitsu.common.data.network.sse.SseClient
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.feature_sse_api.interfaces.EventDatasource
import jp.co.soramitsu.feature_sse_api.interfaces.EventRepository
import jp.co.soramitsu.feature_sse_api.model.DepositOperationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationFailedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationStartedEvent
import jp.co.soramitsu.feature_sse_api.model.Event
import jp.co.soramitsu.feature_sse_api.model.OperationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.OperationFailedEvent
import jp.co.soramitsu.feature_sse_api.model.OperationStartedEvent
import jp.co.soramitsu.feature_sse_impl.data.mappers.DepositOperationCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegFailedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegStartedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationFailedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationStartedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.network.model.DepositOperationCompletedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationCompletedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationFailedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.EthRegistrationStartedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.EventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationCompletedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationFailedEventRemote
import jp.co.soramitsu.feature_sse_impl.data.network.model.OperationStartedEventRemote
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val eventDatasource: EventDatasource,
    private val cryptoAssistant: CryptoAssistant,
    private val sseClient: SseClient,
    private val appLinksProvider: AppLinksProvider,
    private val ethRegStartedEventMapper: EthRegStartedEventMapper,
    private val ethRegCompletedEventMapper: EthRegCompletedEventMapper,
    private val ethRegFailedEventMapper: EthRegFailedEventMapper,
    private val operationStartedEventMapper: OperationStartedEventMapper,
    private val operationFailedEventMapper: OperationFailedEventMapper,
    private val operationCompletedEventMapper: OperationCompletedEventMapper,
    private val depositOperationCompletedEventMapper: DepositOperationCompletedEventMapper,
    private val serializer: Serializer
) : EventRepository {

    companion object {
        private const val EVENT_SERIALIZED_NAME = "event"
        private const val HEADER_LAST_EVENT_ID = "Last-Event-ID"
    }

    private var serverSentEvent: ServerSentEvent? = null
    private var eventListener: ServerSentEventListener? = null

    override fun observeEvents(): Observable<Event> {
        return Observable.create { emitter ->
            var token = eventDatasource.retrieveSseToken()
            val lastEventId = eventDatasource.getLastEventId()

            if (token.isEmpty()) {
                token = generateToken()
                eventDatasource.saveSseToken(token)
            }

            val requestBuilder = Request.Builder()
            requestBuilder.url("${appLinksProvider.soraHostUrl}/notification/stream?token=$token")
            if (lastEventId.isNotEmpty()) {
                requestBuilder.addHeader(HEADER_LAST_EVENT_ID, lastEventId)
            }

            val request = requestBuilder.build()

            eventListener = ServerSentEventListener(emitter)

            serverSentEvent = sseClient.newServerSentEvent(request, eventListener!!)
        }
    }

    private inner class ServerSentEventListener(
        private val emitter: ObservableEmitter<Event>
    ) : ServerSentEvent.Listener {

        override fun onOpen(sse: ServerSentEvent, response: Response) {
        }

        override fun onMessage(sse: ServerSentEvent, id: String, eventType: ServerSentEvent.Type?, data: String) {
            when (eventType) {
                ServerSentEvent.Type.EVENT -> {
                    val event = parseEvent(data)
                    if (!emitter.isDisposed) emitter.onNext(event)
                }
                ServerSentEvent.Type.SIGNAL -> {
                }
                else -> {
                }
            }

            if (id.isNotEmpty()) {
                eventDatasource.saveLastEventId(id)
            }
        }

        override fun onResponseError(sse: ServerSentEvent, throwable: Throwable, response: Response) {
            if (!emitter.isDisposed) emitter.onError(throwable)
        }

        override fun onError(sse: ServerSentEvent, throwable: Throwable) {
            if (!emitter.isDisposed) emitter.onError(throwable)
        }

        override fun onClosed(sse: ServerSentEvent) {
            if (!emitter.isDisposed) emitter.onComplete()
        }
    }

    private fun parseEvent(data: String): Event {
        val dataJsonObject = JsonParser().parse(data).asJsonObject
        val sseTypeStr = dataJsonObject.get(EVENT_SERIALIZED_NAME).asString

        return when (EventRemote.Type.valueOf(sseTypeStr)) {
            EventRemote.Type.EthRegistrationStarted -> ethRegistrationStartedEvent(data)
            EventRemote.Type.EthRegistrationCompleted -> ethRegistrationCompletedEvent(data)
            EventRemote.Type.EthRegistrationFailed -> ethRegistrationFailedEvent(data)
            EventRemote.Type.OperationStarted -> operationStartedEvent(data)
            EventRemote.Type.OperationCompleted -> operationCompletedEvent(data)
            EventRemote.Type.OperationFailed -> operationFailedEvent(data)
            EventRemote.Type.DepositOperationCompleted -> depositOperationCompletedEvent(data)
        }
    }

    private fun ethRegistrationStartedEvent(data: String): EthRegistrationStartedEvent {
        val event = serializer.deserialize(data, EthRegistrationStartedEventRemote::class.java)
        return ethRegStartedEventMapper.map(event)
    }

    private fun ethRegistrationCompletedEvent(data: String): EthRegistrationCompletedEvent {
        val event = serializer.deserialize(data, EthRegistrationCompletedEventRemote::class.java)
        return ethRegCompletedEventMapper.map(event)
    }

    private fun ethRegistrationFailedEvent(data: String): EthRegistrationFailedEvent {
        val event = serializer.deserialize(data, EthRegistrationFailedEventRemote::class.java)
        return ethRegFailedEventMapper.map(event)
    }

    private fun operationStartedEvent(data: String): OperationStartedEvent {
        val event = serializer.deserialize(data, OperationStartedEventRemote::class.java)
        return operationStartedEventMapper.map(event)
    }

    private fun operationCompletedEvent(data: String): OperationCompletedEvent {
        val event = serializer.deserialize(data, OperationCompletedEventRemote::class.java)
        return operationCompletedEventMapper.map(event)
    }

    private fun operationFailedEvent(data: String): OperationFailedEvent {
        val event = serializer.deserialize(data, OperationFailedEventRemote::class.java)
        return operationFailedEventMapper.map(event)
    }

    private fun depositOperationCompletedEvent(data: String): DepositOperationCompletedEvent {
        val event = serializer.deserialize(data, DepositOperationCompletedEventRemote::class.java)
        return depositOperationCompletedEventMapper.map(event)
    }

    override fun release() {
        serverSentEvent?.close()
        eventListener = null
    }

    private fun generateToken(): String {
        val sb = StringBuilder()
        val random = cryptoAssistant.getSecureRandom(64).blockingGet()

        for (byte in random) {
            sb.append(String.format("%02x", byte))
        }

        return sb.toString()
    }
}