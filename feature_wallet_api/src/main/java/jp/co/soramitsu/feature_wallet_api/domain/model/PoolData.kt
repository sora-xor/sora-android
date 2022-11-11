/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

data class PoolData(
    val token: Token,
    val baseToken: Token,
    val basePooled: BigDecimal,
    val baseReserves: BigDecimal,
    val secondPooled: BigDecimal,
    val secondReserves: BigDecimal,
    val strategicBonusApy: BigDecimal?,
    val poolShare: Double,
    val totalIssuance: BigDecimal,
    val poolProvidersBalance: BigDecimal,
)
