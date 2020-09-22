/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class TransactionRemote(
    @SerializedName("transactionId") val transactionId: String,
    @SerializedName("status") val status: Status,
    @SerializedName("assetId") val assetId: String?,
    @SerializedName("details") val details: String,
    @SerializedName("peerName") val peerName: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("peerId") val peerId: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("type") val type: Type,
    @SerializedName("fee") val fee: BigDecimal
) {
    enum class Status {
        @SerializedName("PENDING") PENDING,
        @SerializedName("COMMITTED") COMMITTED,
        @SerializedName("REJECTED") REJECTED
    }

    enum class Type {
        @SerializedName("INCOMING") INCOMING,
        @SerializedName("OUTGOING") OUTGOING,
        @SerializedName("WITHDRAW") WITHDRAW,
        @SerializedName("REWARD") REWARD,
        @SerializedName("DEPOSIT_ASSET") DEPOSIT
    }
}