package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class XorAssetBalance(
    val transferable: BigDecimal,
    val frozen: BigDecimal,
    val totalBalance: BigDecimal,
    val locked: BigDecimal,
    val bonded: BigDecimal,
    val reserved: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal
)
