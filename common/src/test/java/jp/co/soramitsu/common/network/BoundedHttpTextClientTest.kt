package jp.co.soramitsu.common.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedHttpTextClientTest {

    @Test
    fun `streaming UTF8 reader stops after exactly one byte beyond its bound`() {
        assertEquals(
            "a🙂",
            readBoundedStrictUtf8(
                ByteArrayInputStream("a🙂".toByteArray(Charsets.UTF_8)),
                maximumBytes = 5,
            ),
        )
        assertEquals(
            "BOUNDED_HTTP_RESPONSE_TOO_LARGE",
            assertThrows(BoundedHttpTextException::class.java) {
                readBoundedStrictUtf8(
                    ByteArrayInputStream("a🙂".toByteArray(Charsets.UTF_8)),
                    maximumBytes = 4,
                )
            }.safeCode,
        )
    }

    @Test
    fun `streaming UTF8 reader rejects malformed bytes`() {
        assertEquals(
            "BOUNDED_HTTP_UTF8_INVALID",
            assertThrows(BoundedHttpTextException::class.java) {
                readBoundedStrictUtf8(
                    ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)),
                    maximumBytes = 2,
                )
            }.safeCode,
        )
    }

    @Test
    fun `client admits three HTTPS redirects and disables transport redirects`() = runBlocking {
        val openedUrls = mutableListOf<String>()
        val connections = mutableListOf<FakeHttpConnection>()
        val result = readBoundedHttpsUtf8(
            rawUrl = "https://runtime.example/types.json",
            maximumBytes = 32,
            connectionOpener = BoundedHttpConnectionOpener { url ->
                openedUrls += url.toExternalForm()
                FakeHttpConnection(
                    url = url,
                    status = if (openedUrls.size <= 3) 302 else 200,
                    headers = if (openedUrls.size <= 3) {
                        mapOf("Location" to "/types-${openedUrls.size}.json")
                    } else {
                        emptyMap()
                    },
                    body = "reviewed-types".toByteArray(),
                ).also(connections::add)
            },
        )

        assertEquals("reviewed-types", result)
        assertEquals(
            listOf(
                "https://runtime.example/types.json",
                "https://runtime.example/types-1.json",
                "https://runtime.example/types-2.json",
                "https://runtime.example/types-3.json",
            ),
            openedUrls,
        )
        connections.forEach { connection ->
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertEquals(
                "no-cache, no-store, max-age=0",
                connection.requestHeaders["Cache-Control"],
            )
            assertEquals("no-cache", connection.requestHeaders["Pragma"])
            assertEquals("gzip", connection.requestHeaders["Accept-Encoding"])
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun `client rejects a fourth redirect without opening its target`() = runBlocking {
        var opened = 0
        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://runtime.example/types.json",
                maximumBytes = 32,
                connectionOpener = BoundedHttpConnectionOpener { url ->
                    opened += 1
                    FakeHttpConnection(
                        url = url,
                        status = 302,
                        headers = mapOf("Location" to "/redirect-$opened"),
                    )
                },
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_REDIRECT_LIMIT", error.safeCode)
        assertEquals(4, opened)
    }

    @Test
    fun `client rejects redirect downgrade before opening HTTP target`() = runBlocking {
        var opened = 0
        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://runtime.example/types.json",
                maximumBytes = 32,
                connectionOpener = BoundedHttpConnectionOpener { url ->
                    opened += 1
                    FakeHttpConnection(
                        url = url,
                        status = 302,
                        headers = mapOf("Location" to "http://runtime.example/types.json"),
                    )
                },
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_URL_INVALID", error.safeCode)
        assertEquals(1, opened)
    }

    @Test
    fun `client rejects declared and streamed oversize bodies`() = runBlocking {
        val declaredConnection = FakeHttpConnection(
            url = URL("https://runtime.example/declared"),
            status = 200,
            declaredLength = 6,
            body = "ignored".toByteArray(),
        )
        val declaredError = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = declaredConnection.url.toExternalForm(),
                maximumBytes = 5,
                connectionOpener = BoundedHttpConnectionOpener { declaredConnection },
            )
        }.exceptionOrNull() as BoundedHttpTextException
        assertEquals("BOUNDED_HTTP_RESPONSE_TOO_LARGE", declaredError.safeCode)
        assertFalse(declaredConnection.inputOpened)

        val streamedError = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://runtime.example/streamed",
                maximumBytes = 5,
                connectionOpener = BoundedHttpConnectionOpener { url ->
                    FakeHttpConnection(url, status = 200, body = "123456".toByteArray())
                },
            )
        }.exceptionOrNull() as BoundedHttpTextException
        assertEquals("BOUNDED_HTTP_RESPONSE_TOO_LARGE", streamedError.safeCode)
    }

    @Test
    fun `client rejects encoded bodies and non success statuses without reading them`() = runBlocking {
        val encoded = FakeHttpConnection(
            url = URL("https://runtime.example/encoded"),
            status = 200,
            headers = mapOf("Content-Encoding" to "br"),
            body = "compressed".toByteArray(),
        )
        val encodingError = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = encoded.url.toExternalForm(),
                maximumBytes = 32,
                connectionOpener = BoundedHttpConnectionOpener { encoded },
            )
        }.exceptionOrNull() as BoundedHttpTextException
        assertEquals("BOUNDED_HTTP_ENCODING_UNSUPPORTED", encodingError.safeCode)
        assertFalse(encoded.inputOpened)

        val unavailable = FakeHttpConnection(
            url = URL("https://runtime.example/unavailable"),
            status = 503,
            body = "upstream details must stay unread".toByteArray(),
        )
        val statusError = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = unavailable.url.toExternalForm(),
                maximumBytes = 64,
                connectionOpener = BoundedHttpConnectionOpener { unavailable },
            )
        }.exceptionOrNull() as BoundedHttpTextException
        assertEquals("BOUNDED_HTTP_STATUS_503", statusError.safeCode)
        assertFalse(unavailable.inputOpened)
    }

    @Test
    fun `JSON POST writes one fixed body and admits only JSON response media`() = runBlocking {
        val connection = FakeHttpConnection(
            url = URL("https://pi.example/graphql"),
            status = 200,
            headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
            body = "{\"data\":{}}".toByteArray(),
        )

        val response = readBoundedHttpsUtf8(
            rawUrl = connection.url.toExternalForm(),
            maximumBytes = 64,
            connectionOpener = BoundedHttpConnectionOpener { connection },
            jsonRequestBody = "{\"query\":\"query Health { _health }\"}",
            requireJsonResponse = true,
        )

        assertEquals("{\"data\":{}}", response)
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.doOutput)
        assertFalse(connection.useCaches)
        assertEquals(
            "no-cache, no-store, max-age=0",
            connection.requestHeaders["Cache-Control"],
        )
        assertEquals("no-cache", connection.requestHeaders["Pragma"])
        assertEquals("application/json", connection.requestHeaders["Accept"])
        assertEquals(
            "application/json; charset=utf-8",
            connection.requestHeaders["Content-Type"],
        )
        assertEquals(
            "{\"query\":\"query Health { _health }\"}",
            connection.postedBody.toString(Charsets.UTF_8.name()),
        )
        assertTrue(connection.disconnected)
    }

    @Test
    fun `client classifies transport runtime defects as cache ineligible`() = runBlocking {
        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://runtime.example/types.json",
                maximumBytes = 32,
                connectionOpener = BoundedHttpConnectionOpener {
                    throw IllegalStateException("broken connection factory")
                },
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_RUNTIME", error.safeCode)
    }

    @Test
    fun `JSON POST rejects every redirect without replaying the body`() = runBlocking {
        var opened = 0
        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://pi.example/graphql",
                maximumBytes = 64,
                connectionOpener = BoundedHttpConnectionOpener { url ->
                    opened += 1
                    FakeHttpConnection(
                        url = url,
                        status = 307,
                        headers = mapOf("Location" to "https://other.example/graphql"),
                    )
                },
                jsonRequestBody = "{\"query\":\"query Health { _health }\"}",
                requireJsonResponse = true,
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_REDIRECT_REJECTED", error.safeCode)
        assertEquals(1, opened)
    }

    @Test
    fun `JSON POST requires exact HTTP 200 before reading the response`() = runBlocking {
        listOf(201, 202, 204, 206).forEach { status ->
            val connection = FakeHttpConnection(
                url = URL("https://pi.example/graphql"),
                status = status,
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"data\":{}}".toByteArray(),
            )

            val error = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = connection.url.toExternalForm(),
                    maximumBytes = 64,
                    connectionOpener = BoundedHttpConnectionOpener { connection },
                    jsonRequestBody = "{\"query\":\"query Health { _health }\"}",
                    requireJsonResponse = true,
                )
            }.exceptionOrNull() as BoundedHttpTextException

            assertEquals("BOUNDED_HTTP_STATUS_$status", error.safeCode)
            assertFalse(connection.inputOpened)
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun `JSON POST rejects missing or non JSON response media before body read`() = runBlocking {
        listOf(null, "text/plain", "text/html; charset=utf-8").forEach { contentType ->
            val connection = FakeHttpConnection(
                url = URL("https://pi.example/graphql"),
                status = 200,
                headers = contentType?.let { mapOf("Content-Type" to it) }.orEmpty(),
                body = "must-not-be-read".toByteArray(),
            )
            val error = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = connection.url.toExternalForm(),
                    maximumBytes = 64,
                    connectionOpener = BoundedHttpConnectionOpener { connection },
                    jsonRequestBody = "{}",
                    requireJsonResponse = true,
                )
            }.exceptionOrNull() as BoundedHttpTextException

            assertEquals("BOUNDED_HTTP_CONTENT_TYPE_INVALID", error.safeCode)
            assertFalse(connection.inputOpened)
        }
    }

    @Test
    fun `JSON POST rejects oversize or malformed UTF8 request before opening transport`() =
        runBlocking {
            var opened = 0
            val opener = BoundedHttpConnectionOpener { url ->
                opened += 1
                FakeHttpConnection(url, status = 200)
            }
            val oversize = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = "https://pi.example/graphql",
                    maximumBytes = 64,
                    connectionOpener = opener,
                    jsonRequestBody = "a".repeat(256 * 1_024 + 1),
                    requireJsonResponse = true,
                )
            }.exceptionOrNull() as BoundedHttpTextException
            assertEquals("BOUNDED_HTTP_REQUEST_TOO_LARGE", oversize.safeCode)

            val malformed = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = "https://pi.example/graphql",
                    maximumBytes = 64,
                    connectionOpener = opener,
                    jsonRequestBody = "\uD800",
                    requireJsonResponse = true,
                )
            }.exceptionOrNull() as BoundedHttpTextException
            assertEquals("BOUNDED_HTTP_REQUEST_UTF8_INVALID", malformed.safeCode)
            assertEquals(0, opened)
        }

    @Test
    fun `client cancellation interrupts a cooperative blocking response and publishes no body`() =
        runBlocking {
            val readEntered = CountDownLatch(1)
            val releaseRead = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            val connection = FakeHttpConnection(
                url = URL("https://runtime.example/blocking"),
                status = 200,
                input = object : InputStream() {
                    override fun read(): Int = throw IOException("single-byte read is unsupported")

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readEntered.countDown()
                        return try {
                            releaseRead.await()
                            -1
                        } catch (_: InterruptedException) {
                            interrupted.countDown()
                            throw InterruptedException("interrupted")
                        }
                    }
                },
            )
            var published: String? = null
            var terminalError: Throwable? = null
            val request = launch(start = CoroutineStart.UNDISPATCHED) {
                terminalError = runCatching {
                    published = readBoundedHttpsUtf8(
                        rawUrl = connection.url.toExternalForm(),
                        maximumBytes = 32,
                        connectionOpener = BoundedHttpConnectionOpener { connection },
                    )
                }.exceptionOrNull()
            }
            assertTrue(readEntered.await(5, TimeUnit.SECONDS))
            request.cancel()
            val wasInterrupted = withContext(Dispatchers.IO) {
                interrupted.await(5, TimeUnit.SECONDS)
            }
            releaseRead.countDown()
            request.cancelAndJoin()

            assertTrue(wasInterrupted)
            assertTrue(terminalError is CancellationException)
            assertTrue(published == null)
            assertTrue(connection.disconnected)
        }

    @Test
    fun `client cancellation disconnects a response that ignores thread interruption`() =
        runBlocking {
            val readEntered = CountDownLatch(1)
            val transportClosed = CountDownLatch(1)
            val connection = FakeHttpConnection(
                url = URL("https://runtime.example/noncooperative"),
                status = 200,
                input = object : InputStream() {
                    override fun read(): Int = throw IOException("single-byte read is unsupported")

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readEntered.countDown()
                        while (transportClosed.count > 0L) {
                            try {
                                Thread.sleep(5)
                            } catch (_: InterruptedException) {
                                // Deliberately ignore Thread.interrupt to model a socket provider
                                // which only releases its read when disconnect closes the transport.
                            }
                        }
                        return -1
                    }
                },
                onDisconnect = transportClosed::countDown,
            )
            var published: String? = null
            var terminalError: Throwable? = null
            val request = launch(start = CoroutineStart.UNDISPATCHED) {
                terminalError = runCatching {
                    published = readBoundedHttpsUtf8(
                        rawUrl = connection.url.toExternalForm(),
                        maximumBytes = 32,
                        connectionOpener = BoundedHttpConnectionOpener { connection },
                    )
                }.exceptionOrNull()
            }
            assertTrue(readEntered.await(5, TimeUnit.SECONDS))
            request.cancel()
            val wasDisconnected = withContext(Dispatchers.IO) {
                transportClosed.await(5, TimeUnit.SECONDS)
            }
            request.cancelAndJoin()

            assertTrue(wasDisconnected)
            assertTrue(terminalError is CancellationException)
            assertTrue(published == null)
            assertTrue(connection.disconnected)
        }

    @Test
    fun `absolute deadline closes a slow drip response and rejects its partial body`() = runBlocking {
        val transportClosed = AtomicBoolean(false)
        val connection = FakeHttpConnection(
            url = URL("https://runtime.example/slow-drip"),
            status = 200,
            input = object : InputStream() {
                override fun read(): Int = throw IOException("single-byte read is unsupported")

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (transportClosed.get()) return -1
                    try {
                        Thread.sleep(5)
                    } catch (_: InterruptedException) {
                        // Deadline qualification is about disconnect, not cooperative interruption.
                    }
                    if (transportClosed.get()) return -1
                    buffer[offset] = 'a'.code.toByte()
                    return 1
                }
            },
            onDisconnect = { transportClosed.set(true) },
        )

        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = connection.url.toExternalForm(),
                maximumBytes = 1_024,
                connectionOpener = BoundedHttpConnectionOpener { connection },
                absoluteTimeoutMillis = 40,
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_DEADLINE", error.safeCode)
        assertTrue(transportClosed.get())
        assertTrue(connection.disconnected)
    }

    @Test
    fun `client asks for gzip and decodes a gzip body under the same byte bound`() = runBlocking {
        val text = "{\"result\":\"0x" + "ab".repeat(2_000) + "\"}"
        val connection = FakeHttpConnection(
            url = URL("https://runtime.example/rpc"),
            status = 200,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Content-Encoding" to "gzip",
            ),
            body = gzip(text),
        )
        assertTrue(gzip(text).size < text.length / 4)

        val response = readBoundedHttpsUtf8(
            rawUrl = connection.url.toExternalForm(),
            maximumBytes = text.length,
            connectionOpener = BoundedHttpConnectionOpener { connection },
            jsonRequestBody = "{}",
            requireJsonResponse = true,
        )

        assertEquals(text, response)
        assertEquals("gzip", connection.requestHeaders["Accept-Encoding"])
    }

    @Test
    fun `gzip body that decodes past the bound is rejected however small it is on the wire`() =
        runBlocking {
            val text = "a".repeat(1_000_000)
            val compressed = gzip(text)
            assertTrue(compressed.size < 2_000)
            val error = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = "https://runtime.example/bomb",
                    maximumBytes = 4_096,
                    connectionOpener = BoundedHttpConnectionOpener { url ->
                        FakeHttpConnection(
                            url = url,
                            status = 200,
                            headers = mapOf("Content-Encoding" to "gzip"),
                            declaredLength = compressed.size.toLong(),
                            body = compressed,
                        )
                    },
                )
            }.exceptionOrNull() as BoundedHttpTextException

            assertEquals("BOUNDED_HTTP_RESPONSE_TOO_LARGE", error.safeCode)
        }

    @Test
    fun `corrupt or truncated gzip body is a malformed response`() = runBlocking {
        val whole = gzip("{\"data\":{}}")
        listOf(
            "not gzip at all".toByteArray(),
            whole.copyOf(whole.size - 6),
        ).forEach { body ->
            val error = runCatching {
                readBoundedHttpsUtf8(
                    rawUrl = "https://runtime.example/corrupt",
                    maximumBytes = 4_096,
                    connectionOpener = BoundedHttpConnectionOpener { url ->
                        FakeHttpConnection(
                            url = url,
                            status = 200,
                            headers = mapOf("Content-Encoding" to "gzip"),
                            body = body,
                        )
                    },
                )
            }.exceptionOrNull() as BoundedHttpTextException

            assertEquals("BOUNDED_HTTP_ENCODING_INVALID", error.safeCode)
        }
    }

    @Test
    fun `POST that fails before its body is handed over is reported as not sent`() = runBlocking {
        val connectFailure = FakeHttpConnection(
            url = URL("https://node.example/"),
            status = 200,
            failOnOutputOpen = ConnectException("refused"),
        )
        val notSent = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = connectFailure.url.toExternalForm(),
                maximumBytes = 64,
                connectionOpener = BoundedHttpConnectionOpener { connectFailure },
                jsonRequestBody = "{\"method\":\"author_submitExtrinsic\"}",
                requireJsonResponse = true,
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_IO", notSent.safeCode)
        assertTrue(notSent.requestNotSent)
        assertEquals(0, connectFailure.postedBody.size())

        val openerFailure = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://node.example/",
                maximumBytes = 64,
                connectionOpener = BoundedHttpConnectionOpener { throw IOException("no route") },
                jsonRequestBody = "{}",
                requireJsonResponse = true,
            )
        }.exceptionOrNull() as BoundedHttpTextException
        assertTrue(openerFailure.requestNotSent)
    }

    @Test
    fun `POST that fails after its body was handed over is never reported as not sent`() =
        runBlocking {
            val failingWrite = object : OutputStream() {
                override fun write(b: Int) = throw IOException("connection reset")
                override fun write(b: ByteArray, off: Int, len: Int) =
                    throw IOException("connection reset")
            }
            val cases = listOf(
                // The write itself failed: part of the body may be on the wire.
                FakeHttpConnection(
                    url = URL("https://node.example/"),
                    status = 200,
                    output = failingWrite,
                ),
                // The body was written and the answer was lost.
                FakeHttpConnection(
                    url = URL("https://node.example/"),
                    status = 200,
                    failOnResponse = IOException("timeout"),
                ),
                // The node answered with an HTTP error.
                FakeHttpConnection(url = URL("https://node.example/"), status = 502),
            )
            cases.forEach { connection ->
                val error = runCatching {
                    readBoundedHttpsUtf8(
                        rawUrl = connection.url.toExternalForm(),
                        maximumBytes = 64,
                        connectionOpener = BoundedHttpConnectionOpener { connection },
                        jsonRequestBody = "{}",
                        requireJsonResponse = true,
                    )
                }.exceptionOrNull() as BoundedHttpTextException

                assertFalse(error.safeCode, error.requestNotSent)
            }
        }

    @Test
    fun `GET failures never claim that a request was not sent`() = runBlocking {
        val error = runCatching {
            readBoundedHttpsUtf8(
                rawUrl = "https://runtime.example/types.json",
                maximumBytes = 32,
                connectionOpener = BoundedHttpConnectionOpener { throw IOException("offline") },
            )
        }.exceptionOrNull() as BoundedHttpTextException

        assertEquals("BOUNDED_HTTP_IO", error.safeCode)
        assertFalse(error.requestNotSent)
    }

    @Test
    fun `response carries the server date only for an exact RFC 1123 header`() = runBlocking {
        suspend fun dateFor(header: String?): Long? = readBoundedHttpsResponse(
            rawUrl = "https://pi.example/graphql",
            maximumBytes = 64,
            connectionOpener = BoundedHttpConnectionOpener { url ->
                FakeHttpConnection(
                    url = url,
                    status = 200,
                    headers = listOfNotNull(
                        "Content-Type" to "application/json",
                        header?.let { "Date" to it },
                    ).toMap(),
                    body = "{}".toByteArray(),
                )
            },
            jsonRequestBody = "{}",
            requireJsonResponse = true,
        ).serverDateEpochMillis

        assertEquals(1_791_737_791_000L, dateFor("Sun, 11 Oct 2026 16:56:31 GMT"))
        assertNull(dateFor(null))
        assertNull(dateFor("yesterday"))
        assertNull(dateFor("2026-10-11T16:56:31Z"))
        assertNull(dateFor("Sun, 11 Oct 2026 16:56:31 GMT" + " ".repeat(80)))
    }

    @Test
    fun `connect timeout is configurable and never above the whole deadline`() = runBlocking {
        val connection = FakeHttpConnection(
            url = URL("https://node.example/"),
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{}".toByteArray(),
        )

        readBoundedHttpsUtf8(
            rawUrl = connection.url.toExternalForm(),
            maximumBytes = 64,
            connectionOpener = BoundedHttpConnectionOpener { connection },
            jsonRequestBody = "{}",
            requireJsonResponse = true,
            connectTimeoutMillis = 8_000,
        )

        assertEquals(8_000, connection.connectTimeout)
        assertEquals(30_000, connection.readTimeout)
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    private class FakeHttpConnection(
        url: URL,
        private val status: Int,
        private val headers: Map<String, String> = emptyMap(),
        private val declaredLength: Long = -1,
        body: ByteArray = ByteArray(0),
        private val input: InputStream = ByteArrayInputStream(body),
        private val onDisconnect: () -> Unit = {},
        private val output: OutputStream? = null,
        private val failOnOutputOpen: IOException? = null,
        private val failOnResponse: IOException? = null,
    ) : HttpURLConnection(url) {
        val requestHeaders = linkedMapOf<String, String>()
        val postedBody = ByteArrayOutputStream()
        var disconnected = false
        var inputOpened = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
            onDisconnect()
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            failOnResponse?.let { throw it }
            return status
        }

        override fun getHeaderField(name: String?): String? = headers.entries
            .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value

        override fun getContentLengthLong(): Long = declaredLength

        override fun getInputStream(): InputStream {
            inputOpened = true
            return input
        }

        override fun getOutputStream(): OutputStream {
            failOnOutputOpen?.let { throw it }
            return output ?: postedBody
        }

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }
    }
}
