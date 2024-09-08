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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.view.WarningTextCard
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddConfirmState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddEstimatedState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddPricesState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun LiquidityAddScreen(
    state: LiquidityAddState,
    slippage: Double,
    onFocusChange1: (Boolean) -> Unit,
    onFocusChange2: (Boolean) -> Unit,
    onAmountChange1: (BigDecimal) -> Unit,
    onAmountChange2: (BigDecimal) -> Unit,
    onSlippageClick: () -> Unit,
    onReview: () -> Unit,
    onSelect1: () -> Unit,
    onSelect2: () -> Unit,
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
                state = state.assetState1,
                onAmountChange = onAmountChange1,
                onSelectToken = onSelect1,
                onFocusChange = onFocusChange1,
            )
            AssetAmountInput(
                modifier = Modifier.constrainAs(token2) {
                    top.linkTo(token1.bottom, 8.dp)
                    start.linkTo(parent.start)
                },
                state = state.assetState2,
                onAmountChange = onAmountChange2,
                onSelectToken = onSelect2,
                onFocusChange = onFocusChange2,
            )
            Icon(
                modifier = Modifier
                    .size(size = Dimens.x3)
                    .constrainAs(arrow) {
                        top.linkTo(token1.bottom, (-8).dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
                painter = painterResource(id = R.drawable.ic_neu_chevron_down_fill),
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = null
            )
        }
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimens.x2,
            color = Color.Transparent
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalArrangement = Arrangement.Center,
        ) {
            MarketSelector(
                value = "$slippage%",
                description = stringResource(id = R.string.slippage),
                onClick = onSlippageClick,
            )
        }
        if (state.pairNotExist == true) {
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = Dimens.x2,
                color = Color.Transparent
            )
            WarningTextCard(
                text = stringResource(id = R.string.confirn_supply_liquidity_first_provider_warning),
                error = false,
            )
        }
        if (state.shouldTransactionReminderInsufficientWarningBeShown) {
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = Dimens.x2,
                color = Color.Transparent
            )
            WarningTextCard(
                text = stringResource(
                    id = R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small,
                    formatArgs = arrayOf(state.prices.fee),
                )
            )
        }
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimens.x2,
            color = Color.Transparent
        )
        LoaderWrapper(
            modifier = Modifier.fillMaxWidth(),
            loading = state.btnState.loading,
            loaderSize = Size.Large,
        ) { modifier, elevation ->
            FilledButton(
                modifier = modifier
                    .testTagAsId("PrimaryButton"),
                enabled = state.btnState.enabled,
                size = Size.Large,
                order = Order.PRIMARY,
                text = state.btnState.text,
                onClick = onReview,
            )
        }
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimens.x2,
            color = Color.Transparent
        )
        DetailsItem(
            text = stringResource(id = R.string.pool_share_title_1),
            value1 = state.estimated.shareOfPool,
        )
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimens.x2,
            color = Color.Transparent
        )
        if (state.pairNotExist != true) {
            DetailsItem(
                text = stringResource(id = R.string.pool_apy_title),
                value1 = state.prices.apy.orEmpty(),
                hint = stringResource(id = R.string.polkaswap_sb_apy_info)
            )
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = Dimens.x2,
                color = Color.Transparent
            )
        }
        DetailsItemNetworkFee(
            fee = state.prices.fee,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLiquidityRemoveScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(12.dp)
    ) {
        LiquidityAddScreen(
            onReview = {},
            onSlippageClick = {},
            onAmountChange2 = {},
            onAmountChange1 = {},
            onFocusChange2 = {},
            onFocusChange1 = {},
            state = LiquidityAddState(
                btnState = ButtonState(
                    text = "btn",
                    enabled = true,
                    loading = false,
                ),
                pairNotExist = true,
                hintVisible = false,
                estimated = LiquidityAddEstimatedState(
                    token1 = "XOR",
                    token2 = "VAL",
                    token1Value = "12.1231",
                    token2Value = "0.0123",
                    shareOfPool = "12.12%",
                ),
                prices = LiquidityAddPricesState(
                    pair1 = "XOR/VAL",
                    pair2 = "VAL/XOR",
                    pair1Value = "1.4",
                    pair2Value = "0.61",
                    apy = "1.2%",
                    fee = "1.999 XOR",
                ),
                assetState1 = previewAssetAmountInputState,
                assetState2 = previewAssetAmountInputState,
                confirm = LiquidityAddConfirmState(
                    text = "",
                    confirmResult = false,
                    btnState = ButtonState(
                        text = "btn",
                        enabled = true,
                        loading = false,
                    ),
                ),
                shouldTransactionReminderInsufficientWarningBeShown = true,
            ),
            onSelect1 = {},
            onSelect2 = {},
            slippage = 0.34,
        )
    }
}
