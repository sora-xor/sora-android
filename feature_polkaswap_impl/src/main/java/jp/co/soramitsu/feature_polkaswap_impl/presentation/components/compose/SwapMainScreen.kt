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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.presentation.compose.components.AssetAmountInput
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.NetworkFeeUnavailableNotice
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.view.WarningTextCard
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.SwapMainState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.defaultSwapDetailsState
import jp.co.soramitsu.common.presentation.compose.neon.FilledPolkaswapButton as FilledButton
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
    onRetryFee: () -> Unit = {},
    onRetryAssets: () -> Unit = {},
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
            val focus1 = remember { FocusRequester() }
            val focus2 = remember { FocusRequester() }
            val focusPosition1 = remember { mutableStateOf(false) }
            val focusPosition2 = remember { mutableStateOf(false) }
            val (token1, token2, arrow) = createRefs()
            AssetAmountInput(
                modifier = Modifier.constrainAs(token1) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                },
                state = state.tokenFromState,
                label = stringResource(R.string.swap_pay_label),
                onAmountChange = onAmountChangeFrom,
                onSelectToken = onSelectFrom,
                onFocusChange = {
                    focusPosition1.value = it
                    onFocusChangeFrom.invoke(it)
                },
                focusRequester = focus1,
            )
            AssetAmountInput(
                modifier = Modifier.constrainAs(token2) {
                    top.linkTo(token1.bottom, 22.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                },
                state = state.tokenToState,
                label = stringResource(R.string.swap_receive_label),
                onAmountChange = onAmountChangeTo,
                onSelectToken = onSelectTo,
                onFocusChange = {
                    focusPosition2.value = it
                    onFocusChangeTo.invoke(it)
                },
                focusRequester = focus2,
            )
            val interaction = remember { MutableInteractionSource() }
            var turns by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val rotation by animateFloatAsState(turns, tween(if (LocalFx.current.reduceMotion) 0 else 240), label = "swap direction")
            NeumorphicSurface(
                shape = CircleShape,
                interactionSource = interaction,
                modifier = Modifier
                    .size(48.dp)
                    .constrainAs(arrow) {
                        top.linkTo(token1.bottom, (-13).dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .clickable(
                        enabled = state.tokenFromState != null || state.tokenToState != null,
                        interactionSource = interaction,
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Button,
                    ) {
                        turns += 180f
                        onTokenSwapClick()
                        if (focusPosition1.value && state.tokenToState != null) {
                            focus2.requestFocus()
                        } else if (focusPosition2.value && state.tokenFromState != null) {
                            focus1.requestFocus()
                        }
                    },
            ) {
                Icon(
                    modifier = Modifier.size(24.dp).rotate(rotation),
                    painter = painterResource(id = R.drawable.ic_polkaswap_40),
                    tint = MaterialTheme.neon.cyan,
                    contentDescription = stringResource(R.string.wallet_swap_direction),
                )
            }
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        SwapMarketSlippageSelector(
            market = stringResource(id = state.market.titleResource),
            slippage = "${state.slippage}%",
            isMarketSelectorEnabled = true,
            onMarketClick = onMarketClick,
            onSlippageClick = onSlippageClick,
        )
        if (state.details.shouldTransactionReminderInsufficientWarningBeShown) {
            Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
            WarningTextCard(
                text = stringResource(
                    id = R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small,
                    formatArgs = arrayOf(state.details.transactionFee),
                )
            )
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        if (state.assetCatalogUnavailable) {
            SwapAssetsUnavailable(state.refreshingAssets, onRetryAssets)
            return@Column
        }
        state.feeUnavailable?.let { reason ->
            NetworkFeeUnavailableNotice(
                reason = reason,
                onRetry = onRetryFee,
            )
            Spacer(modifier = Modifier.size(Dimens.x2))
        }
        LoaderWrapper(
            modifier = Modifier.fillMaxWidth(),
            loading = state.swapButtonState.loading,
            loaderSize = Size.Large,
        ) { modifier, elevation ->
            FilledButton(
                modifier = modifier.testTagAsId("SwapButton"),
                enabled = state.swapButtonState.enabled && !LocalSelectedAccountReadOnly.current,
                size = Size.Large,
                order = Order.PRIMARY,
                text = state.swapButtonState.text,
                onClick = onSwapClick,
            )
        }
        if (state.details.minmaxValue.isNotBlank()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItem(
                text = stringResource(id = state.details.minmaxTitle),
                hint = stringResource(id = state.details.minmaxHint),
                value1 = state.details.minmaxValue,
                value2 = state.details.minmaxValueFiat,
            )
        }
        if (state.details.transactionFee.isNotBlank()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            DetailsItemNetworkFee(
                fee = state.details.transactionFee,
                feeFiat = state.details.transactionFeeFiat,
            )
        }
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
    }
}

@Composable
internal fun SwapAssetsUnavailable(refreshing: Boolean, onRetry: () -> Unit) {
    ContentCard(
        modifier = Modifier.fillMaxWidth(),
        innerPadding = PaddingValues(Dimens.x2),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.x1)) {
                Icon(
                    painterResource(R.drawable.ic_menu_wallet_24), null,
                    modifier = Modifier.size(24.dp), tint = MaterialTheme.neon.cyan,
                )
                Text(
                    stringResource(if (refreshing) R.string.common_loading else R.string.wallet_assets_unavailable),
                    style = MaterialTheme.customTypography.textMBold,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
            FilledButton(
                modifier = Modifier.fillMaxWidth().testTagAsId("RetryAssets"),
                enabled = !refreshing,
                order = Order.PRIMARY,
                size = Size.Large,
                text = stringResource(R.string.asset_catalog_retry),
                onClick = onRetry,
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
