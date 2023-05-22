/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState

data class LiquidityAddState(
    val btnState: ButtonState,
    val slippage: Double,
    val assetState1: AssetAmountInputState?,
    val assetState2: AssetAmountInputState?,
    val estimated: LiquidityAddEstimatedState,
    val prices: LiquidityAddPricesState,
    val confirm: LiquidityAddConfirmState,
    val pairNotExist: Boolean? = null,
    val selectSearchAssetState: SelectSearchAssetState?,
    val hintVisible: Boolean,
)

data class LiquidityAddConfirmState(
    val text: String,
    val confirmResult: Boolean?,
    val btnState: ButtonState,
)

data class LiquidityAddEstimatedState(
    val token1: String,
    val token1Value: String,
    val token2: String,
    val token2Value: String,
    val shareOfPool: String,
)

data class LiquidityAddPricesState(
    val pair1: String,
    val pair1Value: String,
    val pair2: String,
    val pair2Value: String,
    val apy: String? = null,
    val fee: String,
)
