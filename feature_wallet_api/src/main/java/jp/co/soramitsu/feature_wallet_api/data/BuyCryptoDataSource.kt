/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.data

import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrderInfo
import kotlinx.coroutines.flow.Flow

interface BuyCryptoDataSource {

    suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder)
    fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo>
}
