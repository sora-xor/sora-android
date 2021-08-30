/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_api.domain.model

import java.math.BigInteger

data class Gas(
    val price: BigInteger,
    val limit: BigInteger,
    val estimations: List<GasEstimation>
)
