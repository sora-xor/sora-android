package jp.co.soramitsu.sora.irohaconnect

import com.neovisionaries.ws.client.*
import io.mockk.*
import java.net.URI
import jp.co.soramitsu.common.irohaconnect.IrohaConnectLaunch
import jp.co.soramitsu.common.irohaconnect.IrohaConnectWire
import org.junit.*
import org.junit.Assert.*

class IrohaConnectTransportContractTest {
    private val socket = mockk<WebSocket>(relaxed = true)
    private val launch = mockk<IrohaConnectLaunch> {
        every { webSocketUri } returns URI("wss://relay.invalid/v1/connect/ws")
        every { tokenProtocol } returns "iroha-connect.fixture"
    }
    private val listener = mockk<IrohaConnectTransportListener>(relaxed = true)
    private val adapter = slot<WebSocketListener>()
    private lateinit var transport: IrohaConnectTransport

    @Before fun setup() {
        mockkConstructor(WebSocketFactory::class)
        every { anyConstructed<WebSocketFactory>().setConnectionTimeout(any()) } answers { self as WebSocketFactory }
        every { anyConstructed<WebSocketFactory>().createSocket(any<URI>()) } returns socket
        every { socket.addProtocol(any()) } returns socket
        every { socket.setMaxPayloadSize(any()) } returns socket
        every { socket.setFrameQueueSize(any()) } returns socket
        every { socket.setMissingCloseFrameAllowed(any()) } returns socket
        every { socket.addListener(capture(adapter)) } returns socket
        every { socket.agreedProtocol } returns "iroha-connect.fixture"
        every { socket.isOpen } returns true
    }

    @After fun cleanup() = unmockkConstructor(WebSocketFactory::class)
    private fun connect() { transport = IrohaConnectTransportFactory().connect(launch, listener) }

    @Test fun `connection enforces token subprotocol payload bound and bounded outgoing queue`() {
        connect()
        verify { socket.addProtocol("iroha-connect.fixture") }
        verify { socket.setMaxPayloadSize(IrohaConnectWire.MAX_FRAME_BYTES) }
        verify { socket.setFrameQueueSize(16) }
        verify { socket.setMissingCloseFrameAllowed(false) }
        verify(exactly = 1) { socket.connectAsynchronously() }
        adapter.captured.onConnected(socket, emptyMap())
        verify(exactly = 1) { listener.onConnected() }
    }

    @Test fun `wrong negotiated protocol fails once and blocks outgoing traffic`() {
        connect()
        every { socket.agreedProtocol } returns "different-protocol"
        adapter.captured.onConnected(socket, emptyMap())
        adapter.captured.onTextMessage(socket, "unexpected")
        verify(exactly = 1) { listener.onFailure() }
        verify(exactly = 0) { listener.onConnected() }
        verify { socket.disconnect(1002, "IrohaConnect protocol error") }
        assertThrows(IllegalStateException::class.java) { transport.send(byteArrayOf(1)) }
    }

    @Test fun `incoming and outgoing buffers are copied across the transport boundary`() {
        connect()
        val inbound = byteArrayOf(1, 2, 3)
        val delivered = slot<ByteArray>()
        every { listener.onBinaryMessage(capture(delivered)) } just Runs
        adapter.captured.onBinaryMessage(socket, inbound)
        inbound[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), delivered.captured)
        val outbound = byteArrayOf(4, 5)
        val sent = slot<ByteArray>()
        every { socket.sendBinary(capture(sent)) } returns socket
        transport.send(outbound)
        outbound[0] = 8
        assertArrayEquals(byteArrayOf(4, 5), sent.captured)
    }

    @Test fun `invalid payload sizes and unavailable socket never send frames`() {
        connect()
        assertThrows(IllegalArgumentException::class.java) { transport.send(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { transport.send(ByteArray(IrohaConnectWire.MAX_FRAME_BYTES + 1)) }
        every { socket.isOpen } returns false
        assertThrows(IllegalStateException::class.java) { transport.send(byteArrayOf(1)) }
        verify(exactly = 0) { socket.sendBinary(any<ByteArray>()) }
    }

    @Test fun `local close is idempotent and suppresses late binary and disconnected callbacks`() {
        connect()
        transport.close(); transport.close()
        adapter.captured.onBinaryMessage(socket, byteArrayOf(1))
        adapter.captured.onDisconnected(socket, null, null, false)
        verify(exactly = 1) { socket.sendClose(1000, "Wallet closed the session") }
        verify(exactly = 1) { socket.disconnect(1000, "Wallet closed the session") }
        verify(exactly = 1) { socket.clearListeners() }
        verify(exactly = 0) { listener.onBinaryMessage(any()) }
        verify(exactly = 0) { listener.onClosed(any()) }
    }

    @Test fun `remote close reason is bounded and delivered once`() {
        connect()
        val close = WebSocketFrame.createCloseFrame(1000, "x".repeat(300))
        adapter.captured.onDisconnected(socket, close, null, true)
        adapter.captured.onDisconnected(socket, close, null, true)
        verify(exactly = 1) { listener.onClosed("x".repeat(160)) }
        assertThrows(IllegalStateException::class.java) { transport.send(byteArrayOf(1)) }
    }

    @Test fun `remote close without reason yields an empty reason`() {
        connect()
        adapter.captured.onDisconnected(socket, null, null, true)
        verify { listener.onClosed("") }
    }

    @Test fun `every websocket error path fails without exposing a close as success`() {
        val error = WebSocketException(WebSocketError.SOCKET_CONNECT_ERROR, "offline")
        val failures: List<(WebSocketListener) -> Unit> = listOf(
            { it.onConnectError(socket, error) },
            { it.onError(socket, error) },
            { it.onMessageError(socket, error, emptyList()) },
            { it.onTextMessage(socket, "unexpected") },
        )
        for (fail in failures) {
            clearMocks(listener, answers = false)
            connect(); fail(adapter.captured)
            adapter.captured.onBinaryMessage(socket, byteArrayOf(1))
            adapter.captured.onDisconnected(socket, null, null, true)
            verify(exactly = 1) { listener.onFailure() }
            verify(exactly = 0) { listener.onBinaryMessage(any()) }
            verify(exactly = 0) { listener.onClosed(any()) }
        }
    }

    @Test fun `synchronous connection errors report failure even if disconnect also throws`() {
        every { socket.connectAsynchronously() } throws IllegalStateException("executor closed")
        every { socket.disconnect(any<Int>(), any<String>()) } throws IllegalStateException("already closed")
        connect()
        verify(exactly = 1) { listener.onFailure() }
        verify { socket.clearListeners() }
    }

    @Test fun `local close still clears callbacks when socket operations throw`() {
        connect()
        every { socket.sendClose(any<Int>(), any<String>()) } throws IllegalStateException("closed")
        every { socket.disconnect(any<Int>(), any<String>()) } throws IllegalStateException("closed")
        transport.close()
        verify(exactly = 1) { socket.clearListeners() }
    }
}
