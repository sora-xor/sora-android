/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model

import android.text.SpannableString

data class RemoveLiquidityData(
    val xorPerSecond: SpannableString,
    val secondPerXor: SpannableString,
    val shareAfterTx: String,
    val networkFee: SpannableString
)
