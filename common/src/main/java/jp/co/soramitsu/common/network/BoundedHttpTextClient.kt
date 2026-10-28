package jp.co.soramitsu.common.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
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
    /**
     * True only for a JSON POST that provably never reached the server: the request failed before
     * the first byte of its body was handed to the connection (name lookup, TCP connect, TLS
     * handshake, or a deadline that expired during them). A server cannot act on a POST whose
     * body it never received, so such a request may safely be sent to another endpoint. False for
     * every failure after that point, where the server may have received the complete request.
     */
    val requestNotSent: Boolean = false,
) : IOException(safeCode)

/** An admitted response body and the server's own clock reading for it. */
class BoundedHttpTextResponse(
    val body: String,
    /**
     * The response's HTTP `Date` header (RFC 1123) in epoch milliseconds, or null when the header
     * is absent or malformed. It is the server's time, received over the authenticated TLS
     * channel, and lets a caller judge the freshness of server data without trusting the
     * device's clock.
     */
    val serverDateEpochMillis: Long?,
)

/** Null for anything but one exact RFC 1123 date, the only form HTTP servers may send. */
internal fun parseHttpDateEpochMillisOrNull(raw: String?): Long? {
    if (raw == null || raw.length > MAXIMUM_HTTP_DATE_LENGTH) return null
    val value = raw.trim()
    if (value.isEmpty()) return null
    return try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
            .takeIf { it > 0L }
    } catch (_: Exception) {
        null
    }
}

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
    connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
): String = readBoundedHttpsResponse(
    rawUrl = rawUrl,
    maximumBytes = maximumBytes,
    connectionOpener = connectionOpener,
    absoluteTimeoutMillis = absoluteTimeoutMillis,
    jsonRequestBody = jsonRequestBody,
    requireJsonResponse = requireJsonResponse,
    connectTimeoutMillis = connectTimeoutMillis,
).body

internal suspend fun readBoundedHttpsResponse(
    rawUrl: String,
    maximumBytes: Int,
    connectionOpener: BoundedHttpConnectionOpener,
    absoluteTimeoutMillis: Long = ABSOLUTE_REQUEST_TIMEOUT_MILLIS,
    jsonRequestBody: String? = null,
    requireJsonResponse: Boolean = false,
    connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
): BoundedHttpTextResponse = coroutineScope {
    require(maximumBytes in 1..BoundedHttpTextClient.MAXIMUM_ALLOWED_BYTES) {
        "BOUNDED_HTTP_LIMIT_INVALID"
    }
    require(absoluteTimeoutMillis in 1..MAXIMUM_ABSOLUTE_REQUEST_TIMEOUT_MILLIS) {
        "BOUNDED_HTTP_DEADLINE_INVALID"
    }
    require(connectTimeoutMillis in 1..CONNECT_TIMEOUT_MILLIS) {
        "BOUNDED_HTTP_CONNECT_TIMEOUT_INVALID"
    }
    val requestBodyBytes = jsonRequestBody?.let(::requireBoundedUtf8RequestBody)
    // Written and read only by the blocking transport thread below. While it is false, no byte
    // of a POST body has been given to the connection, so the server cannot have the request.
    val requestBodyHandedOver = AtomicBoolean(false)
    fun failure(safeCode: String): BoundedHttpTextException = BoundedHttpTextException(
        safeCode = safeCode,
        requestNotSent = requestBodyBytes != null && !requestBodyHandedOver.get(),
    )
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
                    throw failure("BOUNDED_HTTP_DEADLINE")
                }
                callerContext.ensureActive()
                val connection = try {
                    connectionOpener.open(currentUrl).apply {
                        requestMethod = if (requestBodyBytes == null) "GET" else "POST"
                        connectTimeout = minOf(
                            connectTimeoutMillis.toLong(),
                            absoluteTimeoutMillis,
                        ).toInt()
                        readTimeout = minOf(
                            READ_TIMEOUT_MILLIS.toLong(),
                            absoluteTimeoutMillis,
                        ).toInt()
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
                        // Setting this header turns the platform's transparent decompression
                        // off, so a gzip body is decoded below under the same byte bound.
                        setRequestProperty("Accept-Encoding", "gzip")
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
                    throw error.withRequestPhase(::failure)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    if (deadlineExpired.get()) {
                        throw failure("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw failure("BOUNDED_HTTP_IO")
                } catch (_: RuntimeException) {
                    if (deadlineExpired.get()) {
                        throw failure("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    // Programming, platform-policy, and connection-state defects must not borrow
                    // the offline-cache eligibility reserved for actual IO failures.
                    throw failure("BOUNDED_HTTP_RUNTIME")
                }
                activeConnection.set(connection)
                var redirectedUrl: URL? = null
                try {
                    if (deadlineExpired.get()) {
                        throw failure("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    if (requestBodyBytes != null) {
                        // Opening the stream resolves the name and completes the TCP and TLS
                        // handshakes. Up to here the server has no part of the body; from the
                        // next line on it may have all of it, whatever error follows.
                        val output = connection.outputStream
                        requestBodyHandedOver.set(true)
                        output.use {
                            it.write(requestBodyBytes)
                            it.flush()
                        }
                        if (deadlineExpired.get()) {
                            throw failure("BOUNDED_HTTP_DEADLINE")
                        }
                        callerContext.ensureActive()
                    }
                    val status = connection.responseCode
                    if (status in REDIRECT_STATUSES) {
                        if (requestBodyBytes != null) {
                            throw failure("BOUNDED_HTTP_REDIRECT_REJECTED")
                        }
                        if (redirects >= MAX_REDIRECTS) {
                            throw failure("BOUNDED_HTTP_REDIRECT_LIMIT")
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw failure("BOUNDED_HTTP_REDIRECT_INVALID")
                        if (location.length > MAXIMUM_URL_LENGTH) {
                            throw failure("BOUNDED_HTTP_REDIRECT_INVALID")
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
                            throw failure("BOUNDED_HTTP_STATUS_$status")
                        }
                        val contentEncoding = connection.getHeaderField("Content-Encoding")
                            ?.trim()
                            ?.lowercase()
                        val gzipEncoded = when (contentEncoding) {
                            null, "", "identity" -> false
                            "gzip", "x-gzip" -> true
                            else -> throw failure("BOUNDED_HTTP_ENCODING_UNSUPPORTED")
                        }
                        if (
                            requireJsonResponse &&
                            !connection.getHeaderField("Content-Type").isJsonMediaType()
                        ) {
                            throw failure("BOUNDED_HTTP_CONTENT_TYPE_INVALID")
                        }
                        val declaredLength = connection.contentLengthLong
                        if (declaredLength > maximumBytes.toLong()) {
                            throw failure("BOUNDED_HTTP_RESPONSE_TOO_LARGE")
                        }
                        val serverDate = parseHttpDateEpochMillisOrNull(
                            connection.getHeaderField("Date"),
                        )
                        val admitted = connection.inputStream.use { input ->
                            if (gzipEncoded) {
                                // The bound applies to the decoded text, so a small compressed
                                // body cannot expand past what the caller admitted.
                                readBoundedGzipUtf8(input, maximumBytes)
                            } else {
                                readBoundedStrictUtf8(input, maximumBytes)
                            }
                        }
                        if (deadlineExpired.get()) {
                            throw failure("BOUNDED_HTTP_DEADLINE")
                        }
                        callerContext.ensureActive()
                        return@runInterruptible BoundedHttpTextResponse(
                            body = admitted,
                            serverDateEpochMillis = serverDate,
                        )
                    }
                } catch (error: BoundedHttpTextException) {
                    throw error.withRequestPhase(::failure)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    if (deadlineExpired.get()) {
                        throw failure("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw failure("BOUNDED_HTTP_IO")
                } catch (_: RuntimeException) {
                    if (deadlineExpired.get()) {
                        throw failure("BOUNDED_HTTP_DEADLINE")
                    }
                    callerContext.ensureActive()
                    throw failure("BOUNDED_HTTP_RUNTIME")
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
                    ?: throw failure("BOUNDED_HTTP_REDIRECT_INVALID")
                redirects += 1
            }
            @Suppress("UNREACHABLE_CODE")
            throw failure("BOUNDED_HTTP_UNREACHABLE")
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

/** Keeps the code of a nested failure and adds whether the request body had been handed over. */
private fun BoundedHttpTextException.withRequestPhase(
    failure: (String) -> BoundedHttpTextException,
): BoundedHttpTextException = failure(safeCode).takeIf { it.requestNotSent != requestNotSent } ?: this

/**
 * Decodes a gzip body while reading at most [maximumBytes] decoded bytes (plus the one byte that
 * proves an overrun).
 *
 * Only data that is not valid gzip ([ZipException]: wrong magic, a corrupt block, a wrong
 * checksum) is a malformed response. Every other [IOException] is thrown on unchanged for the
 * caller to classify exactly as on the uncompressed path: a read that timed out, a connection
 * that was cut, a deadline that closed the socket, and a body that ended before the gzip stream
 * was complete are network failures, not bad data. Reporting them as malformed kept callers from
 * the offline data they reserve for network failures.
 */
internal fun readBoundedGzipUtf8(
    input: InputStream,
    maximumBytes: Int,
): String = try {
    // The constructor already reads the gzip header from the connection.
    GZIPInputStream(input, IO_BUFFER_BYTES).use { stream ->
        readBoundedStrictUtf8(stream, maximumBytes)
    }
} catch (_: ZipException) {
    throw BoundedHttpTextException("BOUNDED_HTTP_ENCODING_INVALID")
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
    ): String = postJson(
        rawUrl = rawUrl,
        requestBody = requestBody,
        maximumBytes = maximumBytes,
    ).body

    /**
     * One JSON POST, never retried or redirected. [connectTimeoutMillis] bounds name lookup, TCP
     * connect and TLS; a caller with a second endpoint uses a short one so that an unreachable
     * host is given up quickly. [absoluteTimeoutMillis] bounds the whole exchange.
     *
     * A failure before the request body was handed to the connection is reported with
     * [BoundedHttpTextException.requestNotSent].
     */
    suspend fun postJson(
        rawUrl: String,
        requestBody: String,
        maximumBytes: Int,
        connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        absoluteTimeoutMillis: Long = DEFAULT_ABSOLUTE_TIMEOUT_MILLIS,
    ): BoundedHttpTextResponse = readBoundedHttpsResponse(
        rawUrl = rawUrl,
        maximumBytes = maximumBytes,
        connectionOpener = BoundedHttpConnectionOpener { url ->
            url.openConnection() as? HttpURLConnection
                ?: throw BoundedHttpTextException("BOUNDED_HTTP_IO")
        },
        absoluteTimeoutMillis = absoluteTimeoutMillis,
        jsonRequestBody = requestBody,
        requireJsonResponse = true,
        connectTimeoutMillis = connectTimeoutMillis,
    )

    companion object {
        internal const val MAXIMUM_ALLOWED_BYTES = 16 * 1024 * 1024
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = CONNECT_TIMEOUT_MILLIS
        const val DEFAULT_ABSOLUTE_TIMEOUT_MILLIS = ABSOLUTE_REQUEST_TIMEOUT_MILLIS

        /** The longest whole-exchange deadline a caller may ask for. */
        const val MAXIMUM_ABSOLUTE_TIMEOUT_MILLIS = MAXIMUM_ABSOLUTE_REQUEST_TIMEOUT_MILLIS
    }
}

private const val IO_BUFFER_BYTES = 8 * 1024
private const val CONNECT_TIMEOUT_MILLIS = 30_000
private const val READ_TIMEOUT_MILLIS = 30_000
private const val ABSOLUTE_REQUEST_TIMEOUT_MILLIS = 45_000L
// Large enough for one 16 MiB runtime metadata download on a slow mobile connection.
private const val MAXIMUM_ABSOLUTE_REQUEST_TIMEOUT_MILLIS = 180_000L
private const val MAXIMUM_HTTP_DATE_LENGTH = 64
private const val MAXIMUM_URL_LENGTH = 2_048
private const val MAXIMUM_REQUEST_BODY_BYTES = 256 * 1_024
private const val MAX_REDIRECTS = 3
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
