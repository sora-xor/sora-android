/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle
import jp.co.soramitsu.common.domain.LiquidityDetails

private const val SLIPPAGE_TOLERANCE_KEY = "SLIPPAGE_TOLERANCE_KEY"
var Bundle.slippageTolerance: Float
    get() = this.getFloat(SLIPPAGE_TOLERANCE_KEY)
    set(value) = this.putFloat(SLIPPAGE_TOLERANCE_KEY, value)

private const val LIQUIDITY_DETAILS_KEY = "LIQUIDITY_DETAILS_KEY"
var Bundle.liquidityDetails: LiquidityDetails
    get() = this.getParcelable(LIQUIDITY_DETAILS_KEY)
        ?: throw IllegalArgumentException("Argument with key $LIQUIDITY_DETAILS_KEY is null")
    set(value) = this.putParcelable(LIQUIDITY_DETAILS_KEY, value)
