package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class AssetBalance(
    val assetId: String,
    val balance: BigDecimal
)