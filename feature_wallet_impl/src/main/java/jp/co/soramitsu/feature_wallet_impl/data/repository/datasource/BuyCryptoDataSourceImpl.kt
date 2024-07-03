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

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.feature_wallet_api.data.BuyCryptoDataSource
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrderInfo
import jp.co.soramitsu.network.WebSocket
import jp.co.soramitsu.network.WebSocketListener
import jp.co.soramitsu.network.WebSocketRequest
import jp.co.soramitsu.network.WebSocketResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BuyCryptoDataSourceImpl(
    private val json: Json
) : BuyCryptoDataSource {

    private val paymentOrderFlow = MutableSharedFlow<PaymentOrderInfo>()

    private val webSocketListener = object : WebSocketListener {
        override suspend fun onResponse(response: WebSocketResponse) {
            val paymentOrderInfo = json.decodeFromString<PaymentOrderInfo>(response.json)
            paymentOrderFlow.emit(paymentOrderInfo)
            paymentOrderWebSocket.disconnect()
        }

        override fun onSocketClosed() {
        }

        override fun onConnected() {
        }
    }

    private val paymentOrderWebSocket: WebSocket = WebSocket(
        url = BuildConfigWrapper.soraCardX1StatusUrl,
        listener = webSocketListener,
        json = json,
        logging = false
    )

    override suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder) {
        try {
            paymentOrderWebSocket.sendRequest(
                request = WebSocketRequest(json = json.encodeToString(paymentOrder))
            )
        } catch (t: Throwable) {
        }
    }

    override fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo> = paymentOrderFlow
}
