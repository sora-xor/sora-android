/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import java.io.IOException
import jp.co.soramitsu.xbackup.repository.DriveRequest
import jp.co.soramitsu.xbackup.repository.OkHttpDriveHttp
import jp.co.soramitsu.xbackup.repository.PrefsAccountDatasource
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveHttpAndPreferencesTest {
    private val sent = mutableListOf<Request>()
    private val sentBodies = mutableListOf<String?>()

    /** An OkHttp client that answers every request itself, without a network. */
    private fun answering(code: Int, body: ByteArray) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            sent += request
            sentBodies += request.body?.let { requestBody -> Buffer().also(requestBody::writeTo).readUtf8() }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `requests carry the bearer token and return the status and body`() {
        val http = OkHttpDriveHttp(answering(200, "{\"id\":\"1\"}".toByteArray()))

        val response = http.execute(DriveRequest("GET", "https://drive.test/drive/v3/files", "token"))

        assertEquals(200, response.code)
        assertEquals("{\"id\":\"1\"}", String(response.body))
        val request = sent.single()
        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals("GET", request.method)
        assertNull(request.body)
    }

    @Test
    fun `uploads send the body with its content type`() {
        val http = OkHttpDriveHttp(answering(201, ByteArray(0)))

        val response = http.execute(
            DriveRequest("POST", "https://drive.test/upload", "token", "multipart/related; boundary=b", "payload".toByteArray()),
        )

        assertEquals(201, response.code)
        assertEquals(0, response.body.size)
        assertEquals("POST", sent.single().method)
        assertEquals("multipart/related; boundary=b", sent.single().body!!.contentType().toString())
        assertEquals("payload", sentBodies.single())
    }

    @Test
    fun `a response over eight megabytes is refused`() {
        val http = OkHttpDriveHttp(answering(200, ByteArray(8 * 1024 * 1024 + 1)))

        val error = assertThrows(IOException::class.java) {
            http.execute(DriveRequest("GET", "https://drive.test/x", "token"))
        }
        assertTrue(error.message!!.contains("larger than"))
    }

    @Test
    fun `the default client is built without a network`() {
        assertNotNull(OkHttpDriveHttp())
    }

    @Test
    fun `file ids are kept per address in the backup preferences`() = runTest {
        val datasource = PrefsAccountDatasource(TestContext)
        datasource.removeAllFields()
        assertEquals("", datasource.getFileId("a"))

        datasource.saveFileId("a", "f1")
        datasource.saveFileId("b", "f2")
        assertEquals("f1", datasource.getFileId("a"))

        datasource.removeFileId("a")
        assertEquals("", datasource.getFileId("a"))
        assertEquals("f2", datasource.getFileId("b"))

        datasource.removeAllFields()
        assertEquals("", datasource.getFileId("b"))
    }
}
