/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

data class WithdrawTransaction(
    val intentTxHash: String,
    val confirmTxHash: String,
    val transferTxHash: String,
    val status: Status,
    val details: String,
    val peerName: String,
    val withdrawAmount: BigDecimal,
    val transferAmount: BigDecimal,
    val timestamp: Long,
    val peerId: String?,
    val transferPeerId: String?,
    val reason: String?,
    val intentFee: BigDecimal,
    val gasLimit: BigInteger,
    val gasPrice: BigInteger
) {
    enum class Status {
        INTENT_STARTED,
        INTENT_PENDING,
        INTENT_COMPLETED,
        INTENT_FAILED,
        CONFIRM_PENDING,
        CONFIRM_COMPLETED,
        CONFIRM_FAILED,
        TRANSFER_PENDING,
        TRANSFER_FAILED,
        TRANSFER_COMPLETED
    }
}