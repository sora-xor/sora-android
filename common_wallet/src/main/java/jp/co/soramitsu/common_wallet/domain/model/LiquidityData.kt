/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.domain.model

import java.math.BigDecimal

data class LiquidityData(
    val firstReserves: BigDecimal = BigDecimal.ZERO,
    val secondReserves: BigDecimal = BigDecimal.ZERO,
    val firstPooled: BigDecimal = BigDecimal.ZERO,
    val secondPooled: BigDecimal = BigDecimal.ZERO,
    val sbApy: Double? = null
)
