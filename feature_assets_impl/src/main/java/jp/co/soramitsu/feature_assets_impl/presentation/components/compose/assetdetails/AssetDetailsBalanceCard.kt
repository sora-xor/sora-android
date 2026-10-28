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
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyReason
import jp.co.soramitsu.common.presentation.compose.neon.safemode.rememberWalletReadOnly
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AssetDetailsBalanceCard(
    amount: String,
    amountFiat: String,
    frozenAmount: String? = null,
    frozenAmountFiat: String? = null,
    lockedNote: String? = null,
    isTransferableAmountAvailable: Boolean = false,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSwapClick: () -> Unit,
    onBalanceClick: () -> Unit,
    readOnly: Boolean = rememberWalletReadOnly(),
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
                        .fillMaxWidth()
                        .testTagAsId("AssetFrozenAmount"),
                    text = frozenAmount,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary
                )
            }
            if (lockedNote != null) {
                // Says why the liquid balance is smaller than what the account holds. An
                // account whose XOR is all locked would otherwise only see "0".
                Text(
                    modifier = Modifier
                        .padding(start = Dimens.x3, end = Dimens.x3, top = Dimens.x1)
                        .fillMaxWidth()
                        .testTagAsId("AssetLockedNote"),
                    text = lockedNote,
                    style = MaterialTheme.customTypography.paragraphXS,
                    color = MaterialTheme.customColors.fgSecondary,
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
            androidx.compose.foundation.layout.FlowRow(
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
                maxItemsInEachRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 2 else 4,
            ) {
                if (isTransferableAmountAvailable) {
                    AmountCardIcon(
                        res = R.drawable.ic_new_arrow_up_24,
                        text = stringResource(id = R.string.common_send),
                        enabled = !readOnly,
                        onClick = onSendClick,
                    )
                }
                AmountCardIcon(
                    res = R.drawable.ic_new_arrow_down_24,
                    text = stringResource(id = R.string.common_receive),
                    enabled = true,
                    onClick = onReceiveClick,
                )
                AmountCardIcon(
                    res = R.drawable.ic_refresh_24,
                    text = stringResource(id = R.string.polkaswap_swap_title),
                    enabled = !readOnly,
                    onClick = onSwapClick,
                )
            }
            if (readOnly) {
                // U9: Send and Swap are off in read-only mode, with the reason.
                ReadOnlyReason(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(start = Dimens.x3, end = Dimens.x3, bottom = Dimens.x3),
                )
            }
        }
    }
}

@Composable
private fun AmountCardIcon(@DrawableRes res: Int, text: String, enabled: Boolean, onClick: () -> Unit) =
    jp.co.soramitsu.common.view.AmountCardIcon(testTagId = "${text}Button", res = res, text = text,
        isEnabled = enabled, onClick = onClick)

@Preview
@Composable
private fun PreviewAssetDetailsBalanceCard() {
    AssetDetailsBalanceCard(
        amount = "54.2353434 XOR",
        amountFiat = "$4 923.34",
        frozenAmount = "12.3 XOR",
        frozenAmountFiat = "$ 1000.1",
        isTransferableAmountAvailable = true,
        onSendClick = { },
        onReceiveClick = { },
        onSwapClick = { },
        onBalanceClick = { },
    )
}
