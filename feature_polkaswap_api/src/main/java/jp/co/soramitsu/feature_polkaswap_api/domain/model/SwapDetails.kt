/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.PoolDex

data class SwapDetails(
    val amount: BigDecimal,
    val per1: BigDecimal,
    val per2: BigDecimal,
    val minmax: BigDecimal,
    val liquidityFee: BigDecimal,
    val networkFee: BigDecimal,
    val dex: PoolDex,
    val swapRoute: List<String>? = null,
)

data class SwapQuote(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val route: List<String>? = null,
)
