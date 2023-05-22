/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.feature_wallet_api.data.BuyCryptoDataSource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BuyCryptoRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrder
import jp.co.soramitsu.feature_wallet_api.domain.model.PaymentOrderInfo
import kotlinx.coroutines.flow.Flow

class BuyCryptoRepositoryImpl(
    private val buyCryptoDataSource: BuyCryptoDataSource
) : BuyCryptoRepository {

    override suspend fun requestPaymentOrderStatus(paymentOrder: PaymentOrder) {
        buyCryptoDataSource.requestPaymentOrderStatus(paymentOrder)
    }

    override fun subscribePaymentOrderInfo(): Flow<PaymentOrderInfo> =
        buyCryptoDataSource.subscribePaymentOrderInfo()
}
