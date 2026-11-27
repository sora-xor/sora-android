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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.NetworkFeeUnavailableNotice
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.view.WarningTextCard
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddConfirmState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddEstimatedState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddPricesState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddState
import jp.co.soramitsu.common.presentation.compose.neon.FilledPolkaswapButton as FilledButton
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
    onRetryEstimate: () -> Unit = {},
    onRetryAssets: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.x2)
    ) {
        if (state.assetCatalogUnavailable) {
            SwapAssetsUnavailable(state.refreshingAssets, onRetryAssets)
            return@Column
        }
        AssetAmountInput(
            modifier = Modifier.fillMaxWidth(),
            state = state.assetState1,
            label = stringResource(R.string.liquidity_base_token),
            onAmountChange = onAmountChange1,
            onSelectToken = onSelect1,
            onFocusChange = onFocusChange1,
        )
        Icon(
            painter = painterResource(R.drawable.ic_plus_24_inside),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(Dimens.x3),
            tint = MaterialTheme.customColors.fgSecondary,
        )
        AssetAmountInput(
            modifier = Modifier.fillMaxWidth(),
            state = state.assetState2,
            label = stringResource(R.string.liquidity_pair_token),
            onAmountChange = onAmountChange2,
            onSelectToken = onSelect2,
            onFocusChange = onFocusChange2,
        )
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
        // The reminder names the fee, so it is shown only while the fee is known.
        if (
            state.shouldTransactionReminderInsufficientWarningBeShown &&
            state.prices.fee.isNotBlank()
        ) {
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
        state.estimateUnavailable?.let { reason ->
            NetworkFeeUnavailableNotice(
                reason = reason,
                onRetry = onRetryEstimate,
            )
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = Dimens.x2,
                color = Color.Transparent
            )
        }
        LoaderWrapper(
            modifier = Modifier.fillMaxWidth(),
            loading = state.btnState.loading,
            loaderSize = Size.Large,
        ) { modifier, elevation ->
            FilledButton(
                modifier = modifier
                    .testTagAsId("PrimaryButton"),
                enabled = state.btnState.enabled && !LocalSelectedAccountReadOnly.current,
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
        if (state.estimated.shareOfPool.isNotBlank()) {
            DetailsItem(
                text = stringResource(id = R.string.pool_share_title_1),
                value1 = state.estimated.shareOfPool,
            )
        }
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimens.x2,
            color = Color.Transparent
        )
        if (state.pairNotExist != true && !state.prices.apy.isNullOrBlank()) {
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
        if (state.prices.fee.isNotBlank()) {
            DetailsItemNetworkFee(fee = state.prices.fee)
        }
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
                    confirmResult = null,
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
