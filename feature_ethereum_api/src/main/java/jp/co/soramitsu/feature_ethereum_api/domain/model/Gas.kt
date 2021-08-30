package jp.co.soramitsu.feature_ethereum_api.domain.model

import java.math.BigInteger

data class Gas(
    val price: BigInteger,
    val limit: BigInteger,
    val estimations: List<GasEstimation>
)
