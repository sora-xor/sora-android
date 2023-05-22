/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
