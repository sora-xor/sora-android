/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.repository

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class DriveRequest(
    val method: String,
    val url: String,
    val token: String,
    val contentType: String? = null,
    val body: ByteArray? = null,
)

internal class DriveResponse(
    val code: Int,
    val body: ByteArray,
)

/** One HTTPS exchange with the Drive API. */
internal fun interface DriveHttp {
    @Throws(IOException::class)
    fun execute(request: DriveRequest): DriveResponse
}

internal class OkHttpDriveHttp(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : DriveHttp {
    override fun execute(request: DriveRequest): DriveResponse {
        val body = request.body?.toRequestBody(request.contentType?.toMediaType())
        val call = client.newCall(
            Request.Builder()
                .url(request.url)
                .header("Authorization", "Bearer ${request.token}")
                .method(request.method, body)
                .build(),
        )
        call.execute().use { response ->
            val source = response.body?.source() ?: return DriveResponse(response.code, ByteArray(0))
            // Backups are a few kilobytes; refuse anything absurd instead of
            // reading it into memory.
            if (!source.request(MAX_RESPONSE_BYTES + 1)) {
                return DriveResponse(response.code, source.readByteArray())
            }
            throw IOException("Drive response larger than $MAX_RESPONSE_BYTES bytes")
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}

/** A Drive API error, with the reasons Google gave (for example storageQuotaExceeded). */
internal class DriveApiException(
    val statusCode: Int,
    val reasons: List<String>,
) : IOException("Drive API returned $statusCode $reasons")
