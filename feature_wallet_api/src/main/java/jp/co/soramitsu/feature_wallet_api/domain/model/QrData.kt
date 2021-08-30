package jp.co.soramitsu.feature_wallet_api.domain.model

data class QrData(
    val accountId: String,
    val amount: String?,
    val assetId: String?
)
