/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common_wallet.data

import jp.co.soramitsu.oauth.network.SoraCardNetworkClient
import jp.co.soramitsu.oauth.network.SoraCardNetworkResponse
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestServerRequest
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.RestClientException
import kotlinx.serialization.DeserializationStrategy

class SoraCardNetworkClientImpl(
    private val restClient: RestClient
) : SoraCardNetworkClient {

    private companion object {
        const val SUCCESS_STATUS_CODE = 200
    }

    private class RestfulGetRequest<Deserializer>(
        override val userAgent: String?,
        override val bearerToken: String?,
        override val url: String,
        override val responseDeserializer: DeserializationStrategy<Deserializer>
    ) : AbstractRestServerRequest<Deserializer>()

    override suspend fun <T> get(
        header: String?,
        bearerToken: String?,
        url: String,
        deserializer: DeserializationStrategy<T>
    ): SoraCardNetworkResponse<T> {
        return try {
            val result = restClient.get(
                RestfulGetRequest(
                    userAgent = header,
                    bearerToken = bearerToken,
                    url = url,
                    responseDeserializer = deserializer
                )
            )

            SoraCardNetworkResponse(
                value = result,
                statusCode = SUCCESS_STATUS_CODE
            )
        } catch (exception: RestClientException) {
            if (exception !is RestClientException.WithCode)
                throw exception

            SoraCardNetworkResponse(
                value = null,
                statusCode = exception.code
            )
        }
    }

    private class RestfulPostRequest<Deserializer>(
        override val userAgent: String?,
        override val bearerToken: String?,
        override val url: String,
        override val responseDeserializer: DeserializationStrategy<Deserializer>,
        override val body: Any,
        override val requestContentType: RestClient.ContentType = RestClient.ContentType.JSON,
    ) : AbstractRestServerRequest.WithBody<Deserializer>()

    override suspend fun <T> post(
        header: String?,
        bearerToken: String?,
        url: String,
        body: String,
        deserializer: DeserializationStrategy<T>
    ): SoraCardNetworkResponse<T> {
        return try {
            val result = restClient.post(
                RestfulPostRequest(
                    userAgent = header,
                    bearerToken = bearerToken,
                    url = url,
                    responseDeserializer = deserializer,
                    body = body
                )
            )

            SoraCardNetworkResponse(
                value = result,
                statusCode = SUCCESS_STATUS_CODE
            )
        } catch (exception: RestClientException) {
            if (exception !is RestClientException.WithCode)
                throw exception

            SoraCardNetworkResponse(
                value = null,
                statusCode = exception.code
            )
        }
    }
}
