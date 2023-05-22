/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BuyXorCard(
    visible: Boolean,
    onCloseCard: () -> Unit,
    onBuyXorClicked: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        ContentCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBuyXorClicked,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BuyXorContent(onBuyXorClicked = onBuyXorClicked)

                    Image(
                        painter = painterResource(R.drawable.ic_buy_xor_banner_sora),
                        contentDescription = null
                    )
                }

                BleachedButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.TopEnd)
                        .padding(Dimens.x1),
                    size = Size.ExtraSmall,
                    order = Order.TERTIARY,
                    shape = CircleShape,
                    onClick = onCloseCard,
                    leftIcon = painterResource(R.drawable.ic_cross),
                )
            }
        }
    }
}

@Composable
private fun BuyXorContent(
    modifier: Modifier = Modifier,
    onBuyXorClicked: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(
                top = Dimens.x2,
                bottom = Dimens.x3,
                start = Dimens.x3,
                end = Dimens.x3,
            )
    ) {
        Text(
            text = stringResource(R.string.buy_crypto_buy_xor_banner_title),
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary
        )

        Text(
            modifier = Modifier.padding(top = Dimens.x1),
            text = stringResource(R.string.buy_crypto_buy_xor_with_fiat_subtitle),
            style = MaterialTheme.customTypography.paragraphXS,
            color = MaterialTheme.customColors.fgPrimary
        )

        FilledButton(
            modifier = Modifier
                .wrapContentWidth()
                .padding(top = Dimens.x2),
            text = stringResource(R.string.common_buy_xor),
            size = Size.ExtraSmall,
            order = Order.PRIMARY,
            onClick = onBuyXorClicked,
        )
    }
}

@Preview
@Composable
private fun PreviewBuyXorCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.x3)
    ) {
        BuyXorCard(
            visible = true,
            onCloseCard = {},
            onBuyXorClicked = {}
        )
    }
}
