/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import androidx.annotation.StringRes
import androidx.compose.ui.text.AnnotatedString
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge

data class SwapMainState(
    val tokenFromState: AssetAmountInputState?,
    val tokenToState: AssetAmountInputState?,
    val slippage: Double,
    val market: Market,
    val selectMarketState: Pair<Market, List<Market>>?,
    val details: SwapDetailsState,
    val swapButtonState: ButtonState,
    val confirmButtonState: ButtonState,
    val confirmText: AnnotatedString,
    /** Result mark shown briefly after Confirm; taken from the transaction outcome. */
    val confirmResult: TransactionResultBadge?,
    val assetCatalogUnavailable: Boolean = false,
    /** Set when the network fee can't be checked; the screen says why and offers Try again. */
    val feeUnavailable: NetworkFeeUnavailable? = null,
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
    val route: String,
    val shouldTransactionReminderInsufficientWarningBeShown: Boolean,
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
        route = "",
        shouldTransactionReminderInsufficientWarningBeShown = false,
    )
