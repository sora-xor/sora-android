/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

data class Transaction(
    val transactionId: String,
    val status: Status,
    val assetId: String?,
    val details: String,
    val peerName: String,
    val amount: Double,
    val timestamp: Long,
    val peerId: String?,
    val reason: String?,
    val type: Type,
    val fee: Double
) {
    enum class Status {
        PENDING,
        COMMITTED,
        REJECTED
    }

    enum class Type {
        INCOMING,
        OUTGOING,
        WITHDRAW,
        REWARD
    }
}