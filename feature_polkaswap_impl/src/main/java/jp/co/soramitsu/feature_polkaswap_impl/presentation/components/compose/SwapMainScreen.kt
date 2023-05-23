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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.common.view.WarningTextCard
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.SwapMainState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.defaultSwapDetailsState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun SwapMainScreen(
    state: SwapMainState,
    onSlippageClick: () -> Unit,
    onSelectFrom: () -> Unit,
    onSelectTo: () -> Unit,
    onMarketClick: () -> Unit,
    onFocusChangeFrom: (Boolean) -> Unit,
    onFocusChangeTo: (Boolean) -> Unit,
    onAmountChangeFrom: (BigDecimal) -> Unit,
    onAmountChangeTo: (BigDecimal) -> Unit,
    onTokenSwapClick: () -> Unit,
    onSwapClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val (token1, token2, arrow) = createRefs()
            AssetAmountInput(
                modifier = Modifier.constrainAs(token1) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                },
                state = state.tokenFromState,
                onAmountChange = onAmountChangeFrom,
                onSelectToken = onSelectFrom,
                onFocusChange = onFocusChangeFrom,
            )
            AssetAmountInput(
                modifier = Modifier.constrainAs(token2) {
                    top.linkTo(token1.bottom, 8.dp)
                    start.linkTo(parent.start)
                },
                state = state.tokenToState,
                onAmountChange = onAmountChangeTo,
                onSelectToken = onSelectTo,
                onFocusChange = onFocusChangeTo,
            )
            Icon(
                modifier = Modifier
                    .clickable { onTokenSwapClick() }
                    .size(size = Dimens.x3)
                    .constrainAs(arrow) {
                        top.linkTo(token1.bottom, (-8).dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
                painter = painterResource(id = R.drawable.ic_round_swap),
                tint = Color.Unspecified,
                contentDescription = null
            )
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        SwapMarketSlippageSelector(
            market = stringResource(id = state.market.titleResource),
            slippage = "${state.slippage}%",
            isMarketSelectorEnabled = state.tokenFromState != null && state.tokenToState != null,
            onMarketClick = onMarketClick,
            onSlippageClick = onSlippageClick,
        )
        if (state.details.shouldTransactionReminderInsufficientWarningBeShown) {
            Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
            WarningTextCard(
                title = stringResource(id = R.string.common_title_warning),
                text = stringResource(
                    id = R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small,
                    formatArgs = arrayOf(state.details.transactionFeeToken, state.details.transactionFee)
                )
            )
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        LoaderWrapper(
            modifier = Modifier.fillMaxWidth(),
            loading = state.swapButtonState.loading,
            loaderSize = Size.Large,
        ) { modifier, elevation ->
            FilledButton(
                modifier = modifier.testTagAsId("SwapButton"),
                enabled = state.swapButtonState.enabled,
                size = Size.Large,
                order = Order.PRIMARY,
                text = state.swapButtonState.text,
                onClick = onSwapClick,
            )
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        DetailsItem(
            text = stringResource(id = state.details.minmaxTitle),
            hint = stringResource(id = state.details.minmaxHint),
            value1 = state.details.minmaxValue,
            value2 = state.details.minmaxValueFiat,
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
        DetailsItemNetworkFee(
            fee = state.details.transactionFee,
            feeFiat = state.details.transactionFeeFiat,
        )
        if (state.details.priceFromToTitle.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItem(
                text = state.details.priceFromToTitle,
                value1 = state.details.priceFromTo,
            )
        }
        if (state.details.priceToFromTitle.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItem(
                text = state.details.priceToFromTitle,
                value1 = state.details.priceToFrom,
            )
        }
        if (state.details.route.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItem(
                text = stringResource(id = R.string.route),
                value1 = state.details.route,
            )
        }
        if (state.details.lpFee.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItem(
                text = stringResource(id = R.string.polkaswap_liqudity_fee),
                hint = stringResource(id = R.string.polkaswap_liqudity_fee_info),
                value1 = state.details.lpFee,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 16777215)
@Composable
private fun PreviewSwapMainScreen() {
    Column {
        SwapMainScreen(
            SwapMainState(
                tokenFromState = previewAssetAmountInputState,
                tokenToState = previewAssetAmountInputState,
                slippage = 0.2,
                selectSearchAssetState = null,
                market = Market.SMART,
                selectMarketState = null,
                details = defaultSwapDetailsState(),
                swapButtonState = ButtonState("btn"),
                confirmButtonState = ButtonState(""),
                confirmText = AnnotatedString(""),
                confirmResult = null,
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}
