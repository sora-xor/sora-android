package jp.co.soramitsu.sora.irohaconnect

import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import jp.co.soramitsu.common.irohaconnect.IrohaConnectLaunch
import jp.co.soramitsu.common.irohaconnect.IrohaConnectWire

interface IrohaConnectTransport : AutoCloseable {
    fun send(bytes: ByteArray)
}

interface IrohaConnectTransportListener {
    fun onConnected()
    fun onBinaryMessage(bytes: ByteArray)
    fun onClosed(reason: String)
    fun onFailure()
}

class IrohaConnectTransportFactory @Inject constructor() {
    fun connect(
        launch: IrohaConnectLaunch,
        listener: IrohaConnectTransportListener,
    ): IrohaConnectTransport = NvIrohaConnectTransport(launch, listener).also { it.connect() }
}

private class NvIrohaConnectTransport(
    private val launch: IrohaConnectLaunch,
    private val listener: IrohaConnectTransportListener,
) : IrohaConnectTransport {
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val socket: WebSocket = WebSocketFactory()
        .setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS)
        .createSocket(launch.webSocketUri)
        .addProtocol(launch.tokenProtocol)
        .setMaxPayloadSize(IrohaConnectWire.MAX_FRAME_BYTES)
        .setFrameQueueSize(MAX_OUTGOING_FRAMES)
        .setMissingCloseFrameAllowed(false)
        .addListener(object : WebSocketAdapter() {
            override fun onConnected(
                websocket: WebSocket,
                headers: Map<String, List<String>>,
            ) {
                if (websocket.agreedProtocol != launch.tokenProtocol) {
                    fail(websocket)
                    return
                }
                listener.onConnected()
            }

            override fun onBinaryMessage(websocket: WebSocket, binary: ByteArray) {
                if (!closed.get() && !failed.get()) {
                    listener.onBinaryMessage(binary.copyOf())
                }
            }

            override fun onTextMessage(websocket: WebSocket, text: String) {
                fail(websocket)
            }

            override fun onConnectError(websocket: WebSocket, exception: WebSocketException) {
                fail(websocket)
            }

            override fun onError(websocket: WebSocket, cause: WebSocketException) {
                fail(websocket)
            }

            override fun onMessageError(
                websocket: WebSocket,
                cause: WebSocketException,
                frames: List<WebSocketFrame>,
            ) {
                fail(websocket)
            }

            override fun onDisconnected(
                websocket: WebSocket,
                serverCloseFrame: WebSocketFrame?,
                clientCloseFrame: WebSocketFrame?,
                closedByServer: Boolean,
            ) {
                if (closed.compareAndSet(false, true) && !failed.get()) {
                    listener.onClosed(
                        serverCloseFrame?.closeReason
                            ?.take(MAX_CLOSE_REASON_CHARACTERS)
                            .orEmpty(),
                    )
                }
            }
        })

    fun connect() {
        try {
            socket.connectAsynchronously()
        } catch (_: Exception) {
            fail(socket)
        }
    }

    override fun send(bytes: ByteArray) {
        if (closed.get() || failed.get() || !socket.isOpen) {
            throw IllegalStateException("IrohaConnect transport is not connected")
        }
        require(bytes.isNotEmpty() && bytes.size <= IrohaConnectWire.MAX_FRAME_BYTES)
        socket.sendBinary(bytes.copyOf())
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.sendClose(NORMAL_CLOSE_CODE, "Wallet closed the session") }
            runCatching { socket.disconnect(NORMAL_CLOSE_CODE, "Wallet closed the session") }
            socket.clearListeners()
        }
    }

    private fun fail(websocket: WebSocket) {
        if (failed.compareAndSet(false, true)) {
            closed.set(true)
            runCatching { websocket.disconnect(PROTOCOL_ERROR_CLOSE_CODE, "IrohaConnect protocol error") }
            websocket.clearListeners()
            listener.onFailure()
        }
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 15_000
        const val MAX_OUTGOING_FRAMES = 16
        const val MAX_CLOSE_REASON_CHARACTERS = 160
        const val NORMAL_CLOSE_CODE = 1000
        const val PROTOCOL_ERROR_CLOSE_CODE = 1002
    }
}
