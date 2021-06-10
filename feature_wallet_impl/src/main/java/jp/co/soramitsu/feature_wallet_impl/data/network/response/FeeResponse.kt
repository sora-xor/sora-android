package jp.co.soramitsu.feature_wallet_impl.data.network.response

import java.math.BigInteger

data class FeeResponse(
    val partialFee: BigInteger,
    val weight: Long
)
