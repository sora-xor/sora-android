/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet.model

import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.util.Date

data class SoraTransaction(
    val status: String,
    val transactionId: String,
    val dateTime: Date,
    val recipientId: String,
    val recipient: String,
    val amount: Double,
    val type: Transaction.Type,
    val description: String,
    val fee: Double
)