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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_assets_impl.presentation.states.FrozenXorDetailsModel
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun XorBalancesDialog(
    state: FrozenXorDetailsModel,
    onClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClick,
    ) {
        ContentCard(
            modifier = Modifier.fillMaxWidth(),
            innerPadding = PaddingValues(
                Dimens.x3
            )
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Text(
                            text = stringResource(id = R.string.details_frozen),
                            color = MaterialTheme.customColors.fgPrimary,
                            style = MaterialTheme.customTypography.headline2
                        )
                        Icon(
                            modifier = Modifier
                                .padding(start = Dimens.x1)
                                .size(Dimens.x2)
                                .align(Alignment.CenterVertically),
                            painter = painterResource(id = R.drawable.ic_settings_info),
                            tint = MaterialTheme.customColors.fgSecondary,
                            contentDescription = ""
                        )
                    }
                    Text(
                        text = state.frozen,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.headline2
                    )
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.frozenFiat,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.customTypography.headline3,
                    color = MaterialTheme.customColors.fgSecondary
                )
                Spacer(modifier = Modifier.height(Dimens.x3))

                BalanceItem(
                    title = stringResource(id = R.string.wallet_balance_locked),
                    amount = state.locked,
                    amountFiat = state.lockedFiat
                )
                Divider(
                    modifier = Modifier.padding(vertical = Dimens.x1_5),
                    color = MaterialTheme.customColors.fgOutline,
                    thickness = 1.dp,
                )
                BalanceItem(
                    title = stringResource(id = R.string.wallet_bonded),
                    amount = state.bonded,
                    amountFiat = state.bondedFiat
                )
                Divider(
                    modifier = Modifier.padding(vertical = Dimens.x1_5),
                    color = MaterialTheme.customColors.fgOutline,
                    thickness = 1.dp,
                )
                BalanceItem(
                    title = stringResource(id = R.string.wallet_balance_reserved),
                    amount = state.reserved,
                    amountFiat = state.reservedFiat
                )
                Divider(
                    modifier = Modifier.padding(vertical = Dimens.x1_5),
                    color = MaterialTheme.customColors.fgOutline,
                    thickness = 1.dp,
                )
                BalanceItem(
                    title = stringResource(id = R.string.wallet_redeemable),
                    amount = state.redeemable,
                    amountFiat = state.redeemableFiat
                )
                Divider(
                    modifier = Modifier.padding(vertical = Dimens.x1_5),
                    color = MaterialTheme.customColors.fgOutline,
                    thickness = 1.dp,
                )
                BalanceItem(
                    title = stringResource(id = R.string.wallet_unbonding),
                    amount = state.unbonding,
                    amountFiat = state.unbondingFiat
                )
            }
        }
    }
}

@Composable
private fun BalanceItem(title: String, amount: String, amountFiat: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Start,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary
        )
        Text(
            text = amount,
            textAlign = TextAlign.End,
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textM
        )
    }
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
        text = amountFiat,
        style = MaterialTheme.customTypography.textXSBold,
        color = MaterialTheme.customColors.fgSecondary
    )
}
