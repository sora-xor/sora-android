/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

data class PoolModel(
    val tokenFrom: Token,
    val tokenTo: Token,
    val tokenFromPooled: BigDecimal,
    val tokenToPooled: BigDecimal,
    val strategicBonusApy: BigDecimal?,
    val poolShare: Double
)
