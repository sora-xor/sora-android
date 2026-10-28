package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import com.neovisionaries.ws.client.*
import io.mockk.*
import jp.co.soramitsu.sora.substrate.request.NextAccountIndexRequest
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.ResponseMapper
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class SocketSingleRequestExecutorTest {
    private val socket = mockk<WebSocket>(relaxed = true)
    private val factory = mockk<WebSocketFactory> { every { createSocket("wss://fixture.invalid") } returns socket }
    private val adapter = slot<WebSocketListener>()
    private val gson = Gson()
    private val subject = SocketSingleRequestExecutor(gson, factory, mockk())
    private val request = NextAccountIndexRequest("fixture-account")
    init {
        every { socket.addListener(capture(adapter)) } returns socket
        every { socket.connect() } returns socket
    }
    @Test fun responseIsDecodedAndSocketClosesAfterOneRequest() = runBlocking {
        val sent = slot<String>()
        every { socket.sendText(capture(sent)) } answers {
            adapter.captured.onTextMessage(socket, "{\"id\":1,\"jsonrpc\":\"2.0\",\"result\":42}"); socket
        }
        val response = withTimeout(5000) { subject.executeRequest(request, "wss://fixture.invalid") }
        assertTrue(sent.captured.contains("system_accountNextIndex"))
        assertTrue(sent.captured.contains("fixture-account"))
        assertEquals(42.0, response.result)
        verify(exactly = 1) { socket.disconnect() }
    }
    @Test fun typedMapperReceivesTheDecodedReplyAndSameGson() = runBlocking {
        every { socket.sendText(any<String>()) } answers {
            adapter.captured.onTextMessage(socket, "{\"id\":1,\"jsonrpc\":\"2.0\",\"result\":42}"); socket
        }
        val mapper = mockk<ResponseMapper<String>>()
        every { mapper.map(any(), gson) } returns "mapped reply"
        assertEquals("mapped reply", withTimeout(5000) { subject.executeRequest(request, "wss://fixture.invalid", mapper) })
        verify(exactly = 1) { mapper.map(any(), gson) }
    }
    @Test fun websocketFailureReachesTheCaller() = runBlocking {
        val failure = WebSocketException(WebSocketError.SOCKET_CONNECT_ERROR, "offline")
        every { socket.sendText(any<String>()) } answers { adapter.captured.onError(socket, failure); socket }
        assertSame(failure, runCatching { withTimeout(5000) { subject.executeRequest(request, "wss://fixture.invalid") } }.exceptionOrNull())
    }
    @Test fun cancellingRequestClearsCallbacksAndDisconnects() = runBlocking {
        val sent = CompletableDeferred<Unit>()
        every { socket.sendText(any<String>()) } answers { sent.complete(Unit); socket }
        val requestJob = launch { subject.executeRequest(request, "wss://fixture.invalid") }
        withTimeout(5000) { sent.await() }
        requestJob.cancelAndJoin()
        verify(exactly = 1) { socket.clearListeners(); socket.disconnect() }
    }
}
