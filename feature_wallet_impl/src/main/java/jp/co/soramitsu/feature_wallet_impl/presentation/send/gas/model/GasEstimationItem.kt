package jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model

import java.math.BigInteger

data class GasEstimationItem(
    val type: Type,
    val titleString: String,
    val amount: BigInteger,
    val amountInEthFormatted: String,
    val timeFormatted: String
) {
    enum class Type {
        SLOW,
        REGULAR,
        FAST
    }
}