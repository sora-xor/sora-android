/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Transaction(
    val ethTxHash: String,
    val secondTxHash: String,
    val soranetTxHash: String,
    val status: Status,
    val detailedStatus: DetailedStatus,
    val assetId: String?,
    val myAddress: String,
    val details: String,
    val peerName: String,
    val amount: BigDecimal,
    val timestamp: Long,
    val peerId: String?,
    val reason: String?,
    val type: Type,
    val ethFee: BigDecimal,
    val soranetFee: BigDecimal
) {
    enum class Status {
        PENDING,
        COMMITTED,
        REJECTED
    }

    enum class DetailedStatus {
        INTENT_STARTED,
        INTENT_PENDING,
        INTENT_COMPLETED,
        INTENT_FAILED,
        CONFIRM_PENDING,
        CONFIRM_COMPLETED,
        CONFIRM_FAILED,
        TRANSFER_PENDING,
        TRANSFER_FAILED,
        TRANSFER_COMPLETED,
        DEPOSIT_PENDING,
        DEPOSIT_FAILED,
        DEPOSIT_COMPLETED,
        DEPOSIT_RECEIVED,
    }

    enum class Type {
        INCOMING,
        OUTGOING,
        WITHDRAW,
        DEPOSIT,
        REWARD
    }

    val timestampInMillis: Long
        get() = timestamp * 1000L
}