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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AssetDetailsBalanceCard(
    amount: String,
    amountFiat: String,
    frozenAmount: String? = null,
    frozenAmountFiat: String? = null,
    isTransferableAmountAvailable: Boolean = false,
    buyCryptoAvailable: Boolean = false,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSwapClick: () -> Unit,
    onBalanceClick: () -> Unit,
    onBuyCryptoClick: (() -> Unit)? = null
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        onClick = onBalanceClick,
        innerPadding = PaddingValues(
            top = Dimens.x3,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = Dimens.x3)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.asset_details_liquid_balance),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline2
                )
                Text(
                    text = amountFiat,
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline2
                )
            }
            Text(
                modifier = Modifier
                    .padding(horizontal = Dimens.x3)
                    .fillMaxWidth(),
                text = amount,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary
            )

            Spacer(modifier = Modifier.height(Dimens.x2))

            if (frozenAmount != null && frozenAmountFiat != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x3)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Text(
                            text = stringResource(id = R.string.details_frozen),
                            style = MaterialTheme.customTypography.headline2,
                            color = MaterialTheme.customColors.fgSecondary
                        )
                        Icon(
                            modifier = Modifier
                                .padding(start = Dimens.x1)
                                .align(Alignment.CenterVertically)
                                .size(Dimens.x2),
                            painter = painterResource(id = R.drawable.ic_neu_lock),
                            tint = MaterialTheme.customColors.fgSecondary,
                            contentDescription = "",
                        )
                    }
                    Text(
                        text = frozenAmountFiat,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.headline2
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x3)
                        .fillMaxWidth(),
                    text = frozenAmount,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary
                )
            }
            Divider(
                color = MaterialTheme.customColors.fgOutline,
                thickness = 1.dp,
                modifier = Modifier
                    .padding(horizontal = Dimens.x3, vertical = Dimens.x2)
                    .height(1.dp)
                    .fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(
                        top = Dimens.x1,
                        bottom = Dimens.x3,
                        start = Dimens.x2,
                        end = Dimens.x2,
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (isTransferableAmountAvailable) {
                    AmountCardIcon(
                        res = R.drawable.ic_new_arrow_up_24,
                        text = stringResource(id = R.string.common_send),
                        onClick = onSendClick,
                    )
                }
                AmountCardIcon(
                    res = R.drawable.ic_new_arrow_down_24,
                    text = stringResource(id = R.string.common_receive),
                    onClick = onReceiveClick,
                )
                AmountCardIcon(
                    res = R.drawable.ic_refresh_24,
                    text = stringResource(id = R.string.polkaswap_swap_title),
                    onClick = onSwapClick,
                )

                if (buyCryptoAvailable && onBuyCryptoClick != null) {
                    AmountCardIcon(
                        res = R.drawable.ic_buy_crypto,
                        text = stringResource(id = R.string.common_buy),
                        onClick = onBuyCryptoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountCardIcon(
    @DrawableRes res: Int,
    text: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(Size.Large)) {
            BleachedButton(
                modifier = Modifier.testTagAsId("${text}Button"),
                shape = CircleShape,
                size = Size.Large,
                order = Order.TERTIARY,
                leftIcon = painterResource(res),
                onClick = onClick,
            )
        }
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(top = Dimens.x1),
            text = text,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
        )
    }
}

@Preview
@Composable
private fun PreviewAssetDetailsBalanceCard() {
    AssetDetailsBalanceCard(
        amount = "54.2353434 XOR",
        amountFiat = "$4 923.34",
        frozenAmount = "12.3 XOR",
        frozenAmountFiat = "$ 1000.1",
        buyCryptoAvailable = true,
        isTransferableAmountAvailable = true,
        onSendClick = { },
        onReceiveClick = { },
        onSwapClick = { },
        onBalanceClick = { },
        onBuyCryptoClick = { }
    )
}
