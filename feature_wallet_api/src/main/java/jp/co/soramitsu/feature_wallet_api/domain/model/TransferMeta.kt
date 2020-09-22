package jp.co.soramitsu.feature_wallet_api.domain.model

data class TransferMeta(
    val feeRate: Double,
    val feeType: FeeType
)