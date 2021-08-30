package jp.co.soramitsu.common.data.network.dto

import java.math.BigInteger

data class SwapFeeDto(
    val amount: BigInteger,
    val fee: BigInteger,
)
