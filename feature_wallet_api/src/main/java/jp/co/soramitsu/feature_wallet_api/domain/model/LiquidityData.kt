/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class LiquidityData(
    val firstReserves: BigDecimal = BigDecimal.ZERO,
    val secondReserves: BigDecimal = BigDecimal.ZERO,
    val firstPooled: BigDecimal = BigDecimal.ZERO,
    val secondPooled: BigDecimal = BigDecimal.ZERO,
    val sbApy: BigDecimal? = null
)
