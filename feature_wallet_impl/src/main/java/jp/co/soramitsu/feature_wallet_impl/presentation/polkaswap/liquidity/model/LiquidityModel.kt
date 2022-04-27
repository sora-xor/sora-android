/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

data class LiquidityModel(
    val fromToken: Token,
    val fromTokenInput: String = "0.0",
    val fromTokenBalance: String,
    val fromTokenFiatBalance: String? = null,
    val fromTokenPoolShare: BigDecimal? = null,
    val toToken: Token? = null,
    val toTokenInput: String = "0.0",
    val toTokenBalance: String? = null,
    val toTokenFiatBalance: String? = null,
    val toTokenPoolShare: BigDecimal? = null
)
