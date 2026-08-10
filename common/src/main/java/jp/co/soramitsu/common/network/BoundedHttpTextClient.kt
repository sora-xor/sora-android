package jp.co.soramitsu.common.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

class BoundedHttpTextException(
    val safeCode: String,
) : IOException(safeCode)

internal fun readBoundedStrictUtf8(
    input: InputStream,
    maximumBytes: Int,
): String {
    require(maximumBytes in 1..BoundedHttpTextClient.MAXIMUM_ALLOWED_BYTES) {
        "BOUNDED_HTTP_LIMIT_INVALID"
    }
    val output = ByteArrayOutputStream(minOf(maximumBytes, IO_BUFFER_BYTES))
    val buffer = ByteArray(IO_BUFFER_BYTES)
    var total = 0
    while (true) {
        val readLimit = minOf(buffer.size, maximumBytes - total + 1)
        val count = input.read(buffer, 0, readLimit)
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > maximumBytes) {
            throw BoundedHttpTextException("BOUNDED_HTTP_RESPONSE_TOO_LARGE")
        }
        output.write(buffer, 0, count)
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    } catch (_: Exception) {
        throw BoundedHttpTextException("BOUNDED_HTTP_UTF8_INVALID")
    }
}

internal fun interface BoundedHttpConnectionOpener {
    fun open(url: URL): HttpURLConnection
}

internal suspend fun readBoundedHttpsUtf8(
    rawUrl: String,
    maximumBytes: Int,
    connectionOpener: BoundedHttpConnectionOpener,
    absoluteTimeoutMillis: Long = ABSOLUTE_REQUEST_TIMEOUT_MILLIS,
    jsonRequestBody: String? = null,
    requireJsonResponse: Boolean = false,
): String = coroutineScope {
    require(maximumBytes in 1..BoundedHttpTextClient.MAXIMUM_ALLOWED_BYTES) {
        "BOUNDED_HTTP_LIMIT_INVALID"
    }
    require(absoluteTimeoutMillis in 1..MAXIMUM_ABSOLUTE_REQUEST_TIMEOUT_MILLIS) {
        "BOUNDED_HTTP_DEADLINE_INVALID"
    }
    val requestBodyBytes = jsonRequestBody?.let(::requireBoundedUtf8RequestBody)
    val callerContext = currentCoroutineContext()
    val activeConnection = AtomicReference<HttpURLConnection?>(null)
    val deadlineExpired = AtomicBoolean(false)

    fun disconnectActiveConnection() {
        val connection = activeConnection.get() ?: return
        try {
            connection.disconnect()
        } catch (_: RuntimeException) {
            // Cancellation/deadline state remains authoritative even if a platform transport has
            // a broken disconnect implementation. No response from that connection is published.
        }
    }

    // Start undispatched so its finally block is installed before any blocking transport work.
    // Parent cancellation cancels this sibling and closes the active connection from another
    // coroutine even when the socket ignores Thread.interrupt. Normal completion cancels it too.
    val deadlineWatchdog = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            delay(absoluteTimeoutMillis)
            deadlineExpired.set(true)
        } finally {
            disconnectActiveConnection()
        }
    }

    try {
        val result = runInterruptible(Dispatchers.IO) {
            var currentUrl = requireBoundedHttpsUrl(rawUrl)
            var redirects = 0
            while (true) {
                if (deadlineExpired.get()) {
                    throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                }
                callerContext.ensureActive()
                val connection = try {
                    connectionOpener.open(currentUrl).apply {
                        requestMethod = if (requestBodyBytes == null) "GET" else "POST"
                        connectTimeout = minOf(
                            CONNECT_TIMEOUT_MILLIS,
                            absoluteTimeoutMillis.toInt(),
                        )
                        readTimeout = minOf(
                            READ_TIMEOUT_MILLIS,
                            absoluteTimeoutMillis.toInt(),
                        )
                        instanceFollowRedirects = false
                        // PI health and remote feature configuration are qualified as live data.
                        // Do not let URLConnection's response cache or an intermediary satisfy
                        // those reads with an unqualified stored response.
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                        setRequestProperty("Pragma", "no-cache")
                        setRequestProperty(
                            "Accept",
                            if (requireJsonResponse) {
                                "application/json"
                            } else {
                                "application/json, text/plain;q=0.9"
                            },
                        )
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("User-Agent", "SORA-Wallet")
                        if (requestBodyBytes != null) {
                            doOutput = true
                            setFixedLengthStreamingMode(requestBodyBytes.size)
                            setRequestProperty(
                                "Content-Type",
                                "application/json; charset=utf-8",
                            )
                        }
                    }
                } catch (error: BoundedHttpTextException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    if (deadlineExpired.get()) {
                        throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw BoundedHttpTextException("BOUNDED_HTTP_IO")
                } catch (_: RuntimeException) {
                    if (deadlineExpired.get()) {
                        throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    // Programming, platform-policy, and connection-state defects must not borrow
                    // the offline-cache eligibility reserved for actual IO failures.
                    throw BoundedHttpTextException("BOUNDED_HTTP_RUNTIME")
                }
                activeConnection.set(connection)
                var redirectedUrl: URL? = null
                try {
                    if (deadlineExpired.get()) {
                        throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    if (requestBodyBytes != null) {
                        connection.outputStream.use { output ->
                            output.write(requestBodyBytes)
                            output.flush()
                        }
                        if (deadlineExpired.get()) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                        }
                        callerContext.ensureActive()
                    }
                    val status = connection.responseCode
                    if (status in REDIRECT_STATUSES) {
                        if (requestBodyBytes != null) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_REDIRECT_REJECTED")
                        }
                        if (redirects >= MAX_REDIRECTS) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_REDIRECT_LIMIT")
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw BoundedHttpTextException("BOUNDED_HTTP_REDIRECT_INVALID")
                        if (location.length > MAXIMUM_URL_LENGTH) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_REDIRECT_INVALID")
                        }
                        redirectedUrl = requireBoundedHttpsUrl(
                            URL(currentUrl, location).toExternalForm(),
                        )
                    } else {
                        val statusIsAdmitted = if (requestBodyBytes == null) {
                            status in 200..299
                        } else {
                            status == HttpURLConnection.HTTP_OK
                        }
                        if (!statusIsAdmitted) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_STATUS_$status")
                        }
                        val contentEncoding = connection.getHeaderField("Content-Encoding")
                            ?.trim()
                            ?.lowercase()
                        if (contentEncoding != null && contentEncoding != "identity") {
                            throw BoundedHttpTextException("BOUNDED_HTTP_ENCODING_UNSUPPORTED")
                        }
                        if (
                            requireJsonResponse &&
                            !connection.getHeaderField("Content-Type").isJsonMediaType()
                        ) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_CONTENT_TYPE_INVALID")
                        }
                        val declaredLength = connection.contentLengthLong
                        if (declaredLength > maximumBytes.toLong()) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_RESPONSE_TOO_LARGE")
                        }
                        val admitted = connection.inputStream.use { input ->
                            readBoundedStrictUtf8(input, maximumBytes)
                        }
                        if (deadlineExpired.get()) {
                            throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                        }
                        callerContext.ensureActive()
                        return@runInterruptible admitted
                    }
                } catch (error: BoundedHttpTextException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    if (deadlineExpired.get()) {
                        throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw BoundedHttpTextException("BOUNDED_HTTP_IO")
                } catch (_: RuntimeException) {
                    if (deadlineExpired.get()) {
                        throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw BoundedHttpTextException("BOUNDED_HTTP_RUNTIME")
                } finally {
                    try {
                        connection.disconnect()
                    } catch (_: RuntimeException) {
                        // Raw transport failures are never exposed or substituted for cancellation.
                    } finally {
                        activeConnection.compareAndSet(connection, null)
                    }
                }
                currentUrl = redirectedUrl
                    ?: throw BoundedHttpTextException("BOUNDED_HTTP_REDIRECT_INVALID")
                redirects += 1
            }
            @Suppress("UNREACHABLE_CODE")
            throw BoundedHttpTextException("BOUNDED_HTTP_UNREACHABLE")
        }
        if (deadlineExpired.get()) {
            throw BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")
        }
        callerContext.ensureActive()
        result
    } finally {
        deadlineWatchdog.cancel()
    }
}

private fun requireBoundedUtf8RequestBody(content: String): ByteArray {
    var index = 0
    var encodedBytes = 0L
    while (index < content.length) {
        val character = content[index]
        val width = when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            Character.isHighSurrogate(character) -> {
                if (
                    index + 1 >= content.length ||
                    !Character.isLowSurrogate(content[index + 1])
                ) {
                    throw BoundedHttpTextException("BOUNDED_HTTP_REQUEST_UTF8_INVALID")
                }
                index += 1
                4
            }
            Character.isLowSurrogate(character) ->
                throw BoundedHttpTextException("BOUNDED_HTTP_REQUEST_UTF8_INVALID")
            else -> 3
        }
        encodedBytes += width
        if (encodedBytes > MAXIMUM_REQUEST_BODY_BYTES.toLong()) {
            throw BoundedHttpTextException("BOUNDED_HTTP_REQUEST_TOO_LARGE")
        }
        index += 1
    }
    return content.toByteArray(Charsets.UTF_8)
}

private fun String?.isJsonMediaType(): Boolean {
    val mediaType = this
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?: return false
    return mediaType == "application/json" || mediaType.endsWith("+json")
}

private fun requireBoundedHttpsUrl(rawUrl: String): URL {
    if (rawUrl.isBlank() || rawUrl.length > MAXIMUM_URL_LENGTH) {
        throw BoundedHttpTextException("BOUNDED_HTTP_URL_INVALID")
    }
    val url = try {
        URL(rawUrl)
    } catch (_: Exception) {
        throw BoundedHttpTextException("BOUNDED_HTTP_URL_INVALID")
    }
    if (
        url.protocol.lowercase() != "https" ||
        url.host.isBlank() ||
        url.userInfo != null
    ) {
        throw BoundedHttpTextException("BOUNDED_HTTP_URL_INVALID")
    }
    return url
}

/**
 * Small streaming reader for reviewed configuration artifacts. It deliberately does not expose
 * response bodies, URLs, or transport errors to logs, and never buffers more than the admitted
 * body size. Redirects remain HTTPS-only and bounded.
 */
@Singleton
class BoundedHttpTextClient @Inject constructor() {

    suspend fun getUtf8(
        rawUrl: String,
        maximumBytes: Int,
    ): String = readBoundedHttpsUtf8(
        rawUrl = rawUrl,
        maximumBytes = maximumBytes,
        connectionOpener = BoundedHttpConnectionOpener { url ->
            url.openConnection() as? HttpURLConnection
                ?: throw BoundedHttpTextException("BOUNDED_HTTP_IO")
        },
    )

    suspend fun postJsonUtf8(
        rawUrl: String,
        requestBody: String,
        maximumBytes: Int,
    ): String = readBoundedHttpsUtf8(
        rawUrl = rawUrl,
        maximumBytes = maximumBytes,
        connectionOpener = BoundedHttpConnectionOpener { url ->
            url.openConnection() as? HttpURLConnection
                ?: throw BoundedHttpTextException("BOUNDED_HTTP_IO")
        },
        jsonRequestBody = requestBody,
        requireJsonResponse = true,
    )

    companion object {
        internal const val MAXIMUM_ALLOWED_BYTES = 16 * 1024 * 1024
    }
}

private const val IO_BUFFER_BYTES = 8 * 1024
private const val CONNECT_TIMEOUT_MILLIS = 30_000
private const val READ_TIMEOUT_MILLIS = 30_000
private const val ABSOLUTE_REQUEST_TIMEOUT_MILLIS = 45_000L
private const val MAXIMUM_ABSOLUTE_REQUEST_TIMEOUT_MILLIS = 60_000L
private const val MAXIMUM_URL_LENGTH = 2_048
private const val MAXIMUM_REQUEST_BODY_BYTES = 256 * 1_024
private const val MAX_REDIRECTS = 3
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
