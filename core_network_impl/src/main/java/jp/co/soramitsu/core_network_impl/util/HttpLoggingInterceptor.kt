/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.util

import com.orhanobut.logger.Logger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http.HttpHeaders
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Modified version of OkHttp interceptor with usage of [Logger]
 */
class HttpLoggingInterceptor : Interceptor {

    companion object {
        private val UTF8 = Charset.forName("UTF-8")

        /**
         * Returns true if the body in question probably contains human readable text. Uses a small sample
         * of code points to detect unicode control characters commonly used in binary file signatures.
         */
        internal fun isPlaintext(buffer: Buffer): Boolean {
            try {
                val prefix = Buffer()
                val byteCount = if (buffer.size() < 64) buffer.size() else 64
                buffer.copyTo(prefix, 0, byteCount)
                for (i in 0..15) {
                    if (prefix.exhausted()) {
                        break
                    }
                    val codePoint = prefix.readUtf8CodePoint()
                    if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                        return false
                    }
                }
                return true
            } catch (e: EOFException) {
                return false // Truncated UTF-8 sequence.
            }
        }
    }

    @Volatile private var level = Level.NONE

    enum class Level {
        /**
         * No logs.
         */
        NONE,
        /**
         * Logs request and response lines.
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1 (3-byte body)
         *
         * <-- 200 OK (22ms, 6-byte body)
        `</pre> *
         */
        BASIC,
        /**
         * Logs request and response lines and their respective headers.
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         * <-- END HTTP
        `</pre> *
         */
        HEADERS,
        /**
         * Logs request and response lines and their respective headers and bodies (if present).
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         *
         * Hi?
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         *
         * Hello!
         * <-- END HTTP
        `</pre> *
         */
        BODY
    }

    /**
     * Change the level at which this interceptor logs.
     */
    fun setLevel(level: Level?): HttpLoggingInterceptor {
        if (level == null) throw NullPointerException("level == null. Use Level.NONE instead.")
        this.level = level
        return this
    }

    fun getLevel(): Level {
        return level
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val logMessage = StringBuilder()
        var logRequestBodyMessage = ""
        var logResponseBodyMessage = ""
        val level = this.level

        val request = chain.request()
        if (level == Level.NONE) {
            return chain.proceed(request)
        }

        val logBody = level == Level.BODY
        val logHeaders = logBody || level == Level.HEADERS

        val requestBody = request.body()
        val hasRequestBody = requestBody != null

        val connection = chain.connection()
        var requestStartMessage = ("--> " + request.method() + ' '.toString() + request.url() + if (connection != null) " " + connection.protocol() else "")
        if (!logHeaders && hasRequestBody) {
            requestStartMessage += " (" + requestBody!!.contentLength() + "-byte body)"
        }
        logMessage.append(requestStartMessage)

        if (logHeaders) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody!!.contentType() != null) {
                    logMessage.append("\nContent-Type: ")
                    logMessage.append(requestBody.contentType())
                }
                if (requestBody.contentLength() != -1L) {
                    logMessage.append("\nContent-Length: ")
                    logMessage.append(requestBody.contentLength())
                }
            }

            val headers = request.headers()
            var i = 0
            val count = headers.size()
            while (i < count) {
                val name = headers.name(i)
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equals(name, ignoreCase = true) && !"Content-Length".equals(name, ignoreCase = true)) {
                    logMessage.append("\n")
                    logMessage.append(name)
                    logMessage.append(": ")
                    logMessage.append(headers.value(i))
                }
                i++
            }

            if (!logBody || !hasRequestBody) {
                logMessage.append("\n--> END ")
                logMessage.append(request.method())
            } else if (bodyHasUnknownEncoding(request.headers())) {
                logMessage.append("\n--> END ")
                logMessage.append(request.method())
                logMessage.append(" (encoded body omitted)")
            } else {
                val buffer = Buffer()
                requestBody!!.writeTo(buffer)

                var charset: Charset? = UTF8
                val contentType = requestBody.contentType()
                if (contentType != null) {
                    charset = contentType.charset(UTF8)
                }

                if (isPlaintext(buffer)) {
                    logRequestBodyMessage = buffer.readString(charset!!)
                    logMessage.append(logRequestBodyMessage)
                    logMessage.append("\n--> END ")
                    logMessage.append(request.method())
                    logMessage.append(" (")
                    logMessage.append(requestBody.contentLength())
                    logMessage.append("-byte body)")
                } else {
                    logMessage.append("\n--> END ")
                    logMessage.append(request.method())
                    logMessage.append(" (binary ")
                    logMessage.append(requestBody.contentLength())
                    logMessage.append("-byte body omitted)")
                }
            }
        }

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            logMessage.append("\n<-- HTTP FAILED: ")
            logMessage.append(e)
            Logger.e(logMessage.toString())
            throw e
        }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        val responseBody = response.body()
        val contentLength = responseBody!!.contentLength()
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
        logMessage.append("\n<-- " + response.code() + (if (response.message().isEmpty()) "" else ' ' + response.message()) +
            ' '.toString() + response.request().url() +
            " (" + tookMs + "ms" + (if (!logHeaders) ", $bodySize body" else "") + ')'.toString())

        if (logHeaders) {
            val headers = response.headers()
            var i = 0
            val count = headers.size()
            while (i < count) {
                logMessage.append("\n")
                logMessage.append(headers.name(i))
                logMessage.append(": ")
                logMessage.append(headers.value(i))
                i++
            }

            if (!logBody || !HttpHeaders.hasBody(response)) {
                logMessage.append("\n<-- END HTTP")
            } else if (bodyHasUnknownEncoding(response.headers())) {
                logMessage.append("\n<-- END HTTP (encoded body omitted)")
            } else {
                val source = responseBody.source()
                source.request(java.lang.Long.MAX_VALUE) // Buffer the entire body.
                var buffer = source.buffer()

                var gzippedLength: Long? = null
                headers["Content-Encoding"]?.let { contentEncoding ->
                    if ("gzip".equals(contentEncoding, true)) {
                        gzippedLength = buffer.size()
                        var gzippedResponseBody: GzipSource? = null
                        try {
                            gzippedResponseBody = GzipSource(buffer.clone())
                            buffer = Buffer()
                            buffer.writeAll(gzippedResponseBody)
                        } finally {
                            gzippedResponseBody?.close()
                        }
                    }
                }

                var charset: Charset? = UTF8
                val contentType = responseBody.contentType()
                if (contentType != null) {
                    charset = contentType.charset(UTF8)
                }

                if (!isPlaintext(buffer)) {
                    logMessage.append("\n")
                    logMessage.append("\n<-- END HTTP (binary ")
                    logMessage.append(buffer.size())
                    logMessage.append("-byte body omitted)")
                    Logger.d(logMessage.toString())
                    return response
                }

                if (contentLength != 0L) {
                    logResponseBodyMessage = buffer.clone().readString(charset!!)
                }

                if (gzippedLength != null) {
                    logMessage.append("\n<-- END HTTP (")
                    logMessage.append(buffer.size())
                    logMessage.append("-byte, ")
                    if (gzippedLength != null) logMessage.append(gzippedLength!!)
                    logMessage.append("-gzipped-byte body)")
                } else {
                    logMessage.append("\n<-- END HTTP (")
                    logMessage.append(buffer.size())
                    logMessage.append("-byte body)")
                }
            }
        }
        Logger.d(logMessage.toString())
        Logger.json(logRequestBodyMessage)
        Logger.json(logResponseBodyMessage)
        return response
    }

    private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
        val contentEncoding = headers.get("Content-Encoding")
        return contentEncoding != null && !contentEncoding.equals("identity", true) && !contentEncoding.equals("gzip", true)
    }
}