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
