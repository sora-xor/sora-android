package jp.co.soramitsu.sora.network

import jp.co.soramitsu.network.WebSocket
import jp.co.soramitsu.network.WebSocketListener
import jp.co.soramitsu.network.WebSocketRequest
import jp.co.soramitsu.network.WebSocketResponse
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.io.InputStream
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebSocketExchangeTest {
    private fun InputStream.line(): String = buildString {
        while (true) { val next = read(); check(next != -1); if (next == 10) break; if (next != 13) append(next.toChar()) }
    }
    private fun InputStream.exact(size: Int): ByteArray = ByteArray(size).also { bytes ->
        var at = 0
        while (at < size) { val count = read(bytes, at, size - at); check(count > 0); at += count }
    }
    private fun exchange(logging: Boolean, failListener: Boolean = false) = runBlocking {
        val request = "{\"id\":1,\"method\":\"fixture\"}"
        val response = "{\"id\":1,\"result\":42}"
        val received = mutableListOf<String>(); var connected = 0; var closed = 0
        val server = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        val served = executor.submit<String> {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().buffered(); val output = socket.getOutputStream()
                val headers = mutableListOf<String>()
                while (true) { val line = input.line(); if (line.isEmpty()) break; headers += line }
                val key = headers.single { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }.substringAfter(':').trim()
                val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
                output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray()); output.flush()
                val opcode = input.read(); val length = input.read(); assertEquals(1, opcode and 15); assertTrue(length and 128 != 0)
                val mask = input.exact(4); val payload = input.exact(length and 127)
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                output.write(byteArrayOf(0x82.toByte(), 2, 7, 8)) // Binary frames are not JSON responses.
                output.write(byteArrayOf(0x81.toByte(), response.length.toByte())); output.write(response.toByteArray())
                output.write(byteArrayOf(0x88.toByte(), 2, 3, 0xe8.toByte())); output.flush()
                payload.decodeToString()
            }
        }
        val listener = object : WebSocketListener {
            override fun onConnected() { connected++ }
            override fun onSocketClosed() { closed++ }
            override suspend fun onResponse(response: WebSocketResponse) {
                received += response.json
                if (failListener) error("fixture listener failed")
            }
        }
        val socket = WebSocket("ws://127.0.0.1:${server.localPort}/fixture", listener, Json, logging = logging)
        try {
            socket.disconnect() // Closing before connecting is harmless.
            withTimeout(10_000) { socket.sendRequest(WebSocketRequest(request)) }
            assertEquals(request, served.get(5, TimeUnit.SECONDS))
            assertEquals(listOf(response), received)
            assertEquals(1, connected); assertEquals(1, closed)
            socket.disconnect()
        } finally {
            WebSocket::class.java.getDeclaredField("networkClient").apply { isAccessible = true }.get(socket).let { it.javaClass.getMethod("close").invoke(it) }
            server.close(); executor.shutdownNow()
        }
    }
    @Test fun jsonRequestAndTextReplyRoundTripWhileBinaryFramesAreIgnored() = exchange(logging = false)
    @Test fun loggingAndListenerFailureStillNotifySocketClosure() = exchange(logging = true, failListener = true)
}
