/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.feature_wallet_api.data.BuyCryptoDataSource
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrderInfo
import jp.co.soramitsu.network.WebSocket
import jp.co.soramitsu.network.WebSocketListener
import jp.co.soramitsu.network.WebSocketRequest
import jp.co.soramitsu.network.WebSocketResponse
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BuyCryptoDataSourceImpl(
    clientProvider: SoramitsuHttpClientProvider
) : BuyCryptoDataSource {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

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
        url = FlavorOptionsProvider.wssX1StatusUrl,
        listener = webSocketListener,
        json = json,
        logging = false,
        provider = clientProvider,
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
