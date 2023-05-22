/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import androidx.annotation.StringRes
import androidx.compose.ui.text.AnnotatedString
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState

data class SwapMainState(
    val tokenFromState: AssetAmountInputState?,
    val tokenToState: AssetAmountInputState?,
    val slippage: Double,
    val selectSearchAssetState: SelectSearchAssetState?,
    val market: Market,
    val selectMarketState: Pair<Market, List<Market>>?,
    val details: SwapDetailsState,
    val swapButtonState: ButtonState,
    val confirmButtonState: ButtonState,
    val confirmText: AnnotatedString,
    val confirmResult: Boolean?,
)

data class SwapDetailsState(
    val transactionFee: String,
    val transactionFeeFiat: String,
    @StringRes val minmaxTitle: Int,
    @StringRes val minmaxHint: Int,
    val minmaxValue: String,
    val minmaxValueFiat: String,
    val priceFromToTitle: String,
    val priceFromTo: String,
    val priceToFromTitle: String,
    val priceToFrom: String,
    val lpFee: String,
    val route: String,
)

fun defaultSwapDetailsState() =
    SwapDetailsState(
        transactionFee = "",
        transactionFeeFiat = "",
        minmaxTitle = R.string.polkaswap_minimum_received,
        minmaxHint = R.string.polkaswap_minimum_received_info,
        minmaxValue = "",
        minmaxValueFiat = "",
        priceFromToTitle = "",
        priceFromTo = "",
        priceToFromTitle = "",
        priceToFrom = "",
        lpFee = "",
        route = "",
    )
