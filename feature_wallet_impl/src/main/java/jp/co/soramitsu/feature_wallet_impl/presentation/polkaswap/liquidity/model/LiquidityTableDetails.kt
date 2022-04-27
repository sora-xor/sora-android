/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model

import android.text.SpannableString

data class LiquidityTableDetails(
    val xorSymbol: String,
    val xorPooled: SpannableString,
    val secondTokenSymbol: String,
    val secondTokenPooled: SpannableString,
    val xorPerSecondTitle: String,
    val xorPerSecond: SpannableString,
    val secondPerXorTitle: String,
    val secondPerXor: SpannableString,
    val shareAfterTxTitle: String,
    val shareAfterTx: String,
    val sbApyTitle: String,
    val sbApy: String,
    val networkFeeTitle: String,
    val networkFeeValue: SpannableString
)
