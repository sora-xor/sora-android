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
import jp.co.soramitsu.ui_core.component.card.ContentCard
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
