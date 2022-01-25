/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

data class PoolData(
    val token: Token,
    val xorPooled: BigDecimal,
    val secondPooled: BigDecimal,
    val poolShare: Double
)
