package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Transaction(
    val ethTxHash: String,
    val soranetTxHash: String,
    val status: Status,
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