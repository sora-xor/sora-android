/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.components.previewAssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveConfirmState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveEstimatedState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemovePricesState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun LiquidityAddConfirmScreen(
    state: LiquidityAddState,
    onConfirmClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        SwapAmountSquare(
                            modifier = Modifier.weight(1f),
                            icon = state.assetState1?.token?.iconUri() ?: DEFAULT_ICON_URI,
                            amount = state.assetState1?.amount?.toPlainString().orEmpty(),
                            amountFiat = state.assetState1?.amountFiat.orEmpty(),
                        )
                        Divider(color = Color.Transparent, modifier = Modifier.width(Dimens.x1))
                        SwapAmountSquare(
                            modifier = Modifier.weight(1f),
                            icon = state.assetState2?.token?.iconUri() ?: DEFAULT_ICON_URI,
                            amount = state.assetState2?.amount?.toPlainString().orEmpty(),
                            amountFiat = state.assetState2?.amountFiat.orEmpty(),
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .size(size = Dimens.x3),
                        painter = painterResource(id = R.drawable.ic_round_plus_24),
                        tint = Color.Unspecified,
                        contentDescription = null
                    )
                }
                Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x3))
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    textAlign = TextAlign.Center,
                    text = state.confirm.text,
                    style = MaterialTheme.customTypography.paragraphS,
                )
                Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
                ContentCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    innerPadding = PaddingValues(Dimens.x3),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
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
                        value = "${state.slippage}%",
                        description = stringResource(id = R.string.slippage),
                        onClick = {},
                    )
                }
                Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x3))
                LoaderWrapper(
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.confirm.btnState.loading,
                    loaderSize = Size.Large,
                ) { modifier, elevation ->
                    FilledButton(
                        modifier = modifier,
                        enabled = state.confirm.btnState.enabled,
                        size = Size.Large,
                        order = Order.PRIMARY,
                        text = state.confirm.btnState.text,
                        onClick = onConfirmClick,
                    )
                }
            }
            if (state.confirm.confirmResult != null) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(RoundedCornerShape(MaterialTheme.borderRadius.xl))
                        .background(Color(0xffc5c9d0))
                        .padding(Dimens.x2),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.wrapContentSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(size = Dimens.x3),
                            painter = painterResource(id = if (state.confirm.confirmResult == true) R.drawable.ic_green_pin else R.drawable.ic_cross_red_16),
                            contentDescription = null,
                            tint = if (state.confirm.confirmResult == true) MaterialTheme.customColors.statusSuccess else MaterialTheme.customColors.statusError,
                        )
                        Text(
                            modifier = Modifier.wrapContentSize(),
                            textAlign = TextAlign.Center,
                            text = stringResource(id = if (state.confirm.confirmResult == true) R.string.wallet_transaction_submitted_1 else R.string.wallet_transaction_rejected),
                            style = MaterialTheme.customTypography.paragraphSBold,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLiquidityRemoveConfirmScreen() {
    LiquidityRemoveConfirmScreen(
        state = LiquidityRemoveState(
            btnState = ButtonState(
                text = "btn",
                enabled = true,
                loading = false,
            ),
            slippage = 0.3,
            estimated = LiquidityRemoveEstimatedState(
                token1 = "XOR",
                token2 = "VAL",
                token1Value = "12.1231",
                token2Value = "0.0123",
                shareOfPool = "12.12%",
            ),
            prices = LiquidityRemovePricesState(
                pair1 = "XOR/VAL",
                pair2 = "VAL/XOR",
                pair1Value = "1.4",
                pair2Value = "0.61",
                apy = "1.2%",
                fee = "1.999 XOR",
            ),
            hintVisible = false,
            assetState1 = previewAssetAmountInputState,
            assetState2 = previewAssetAmountInputState,
            confirm = LiquidityRemoveConfirmState(
                text = "Output is estimated. If the price changes",
                confirmResult = false,
                btnState = ButtonState(
                    text = "btn",
                    enabled = true,
                    loading = false,
                ),
            ),
        ),
        onConfirmClick = {},
    )
}