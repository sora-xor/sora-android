/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    @StringRes val lpFeeTitle: Int,
    @StringRes val lpFeeHint: Int,
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
        lpFee = "",
        lpFeeTitle = R.string.polkaswap_liqudity_fee,
        lpFeeHint = R.string.polkaswap_liqudity_fee_info,
        route = "",
        shouldTransactionReminderInsufficientWarningBeShown = false,
    )
