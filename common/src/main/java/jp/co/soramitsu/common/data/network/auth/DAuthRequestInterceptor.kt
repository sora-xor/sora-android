/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.auth

import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.Util.UTF_8
import okio.Buffer
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.Hex
import java.io.IOException
import java.util.Date
import javax.inject.Inject

class DAuthRequestInterceptor @Inject constructor(
    private val authHolder: AuthHolder,
    private val didProvider: DidProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val keyPair = authHolder.getKeyPair()
        if (keyPair != null) {
            val request = bodyToString(originalRequest.body())

            val ed25519Sha3 = Ed25519Sha3()

            val owner = didProvider.generateDID(Hex.toHexString(keyPair.public.encoded).substring(0, 20))

            val key = owner.withFragment("keys-1")
            val uri = originalRequest.url().toString()
            val timestamp = Date().time.toString()

            val stringToSign = StringBuilder()
                .append(originalRequest.method())
                .append(uri)
                .append(request)
                .append(timestamp)
                .append(owner)
                .append(key)
                .toString()

            val signature = ed25519Sha3.rawSign(stringToSign.toByteArray(UTF_8), keyPair)

            val newRequest = originalRequest.newBuilder()
                .addHeader("SORA-AUTH-ID", owner.toString())
                .addHeader("SORA-AUTH-PUBLIC-KEY", key.toString())
                .addHeader("SORA-AUTH-TIMESTAMP", timestamp)
                .addHeader("SORA-AUTH-SIGNATURE", Base64.toBase64String(signature))
                .header("User-Agent", "sora android1.0")
                .build()

            return chain.proceed(newRequest)
        } else {
            return chain.proceed(originalRequest)
        }
    }

    private fun bodyToString(request: RequestBody?): String {
        try {
            val buffer = Buffer()
            if (request != null) {
                request.writeTo(buffer)
            } else {
                return ""
            }
            return buffer.readUtf8()
        } catch (e: IOException) {
            return "did not work"
        }
    }
}