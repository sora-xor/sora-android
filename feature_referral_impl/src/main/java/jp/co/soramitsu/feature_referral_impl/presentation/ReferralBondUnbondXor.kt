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

package jp.co.soramitsu.feature_referral_impl.presentation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common.view.WarningTextCard
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
fun ReferralBondXor(
    common: ReferralCommonState,
    state: ReferralBondState,
    onBondInvitationsCountChange: (Int) -> Unit,
    onBondMinus: () -> Unit,
    onBondPlus: () -> Unit,
    onBondClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(top = Dimens.x1_5)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x2),
                text = stringResource(id = R.string.referral_input_reward_title),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.headline2,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(
                    id = R.string.referral_input_reward_description,
                    common.referrerFee
                ),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            InvitationsEnterField(
                balance = state.balance,
                amount = state.invitationsAmount,
                count = state.invitationsCount,
                onMinus = onBondMinus,
                onPlus = onBondPlus,
                onCountChange = onBondInvitationsCountChange,
            )

            DetailsItemNetworkFee(
                modifier = Modifier.padding(top = Dimens.x3),
                fee = common.extrinsicFee,
                feeFiat = common.extrinsicFeeFiat,
            )

            if (state.shouldTransactionReminderInsufficientWarningBeShown) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = Dimens.x2,
                    color = Color.Transparent
                )
                WarningTextCard(
                    text = stringResource(
                        id = R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small,
                        formatArgs = arrayOf(common.extrinsicFee)
                    )
                )
            }

            LoaderWrapper(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x2),
                loading = common.progress,
                loaderSize = Size.Large
            ) { modifier, elevation ->
                FilledButton(
                    modifier = modifier
                        .testTagAsId("BondXor")
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.referral_bond_button_title),
                    enabled = common.activate && state.invitationsCount > 0,
                    onClick = onBondClick
                )
            }
        }
    }
}

@Composable
fun ReferralUnbondXor(
    common: ReferralCommonState,
    state: ReferralBondState,
    onUnbondInvitationsCountChange: (Int) -> Unit,
    onUnbondMinus: () -> Unit,
    onUnbondPlus: () -> Unit,
    onUnbondClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .padding(top = Dimens.x1_5)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x2),
                text = stringResource(id = R.string.referral_unbond_title),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.headline2,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(
                    id = R.string.referral_unbond_description,
                    common.referrerFee
                ),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            InvitationsEnterField(
                balance = state.balance,
                amount = state.invitationsAmount,
                count = state.invitationsCount,
                onMinus = onUnbondMinus,
                onPlus = onUnbondPlus,
                onCountChange = onUnbondInvitationsCountChange,
            )

            DetailsItemNetworkFee(
                modifier = Modifier.padding(top = Dimens.x3),
                fee = common.extrinsicFee,
                feeFiat = common.extrinsicFeeFiat,
            )

            if (state.shouldTransactionReminderInsufficientWarningBeShown) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = Dimens.x2,
                    color = Color.Transparent
                )
                WarningTextCard(
                    text = stringResource(
                        id = R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small,
                        formatArgs = arrayOf(common.extrinsicFee)
                    )
                )
            }

            LoaderWrapper(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x2),
                loading = common.progress,
                loaderSize = Size.Large
            ) { modifier, elevation ->
                FilledButton(
                    modifier = modifier
                        .testTagAsId("UnbondXor")
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.referral_unbond_button_title),
                    enabled = common.activate && state.invitationsCount > 0,
                    onClick = onUnbondClick
                )
            }
        }
    }
}

@Composable
fun InvitationsEnterField(
    balance: String,
    amount: String,
    count: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onCountChange: (Int) -> Unit,
) {
    val roundedShape = RoundedCornerShape(MaterialTheme.borderRadius.ml)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(roundedShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.customColors.fgPrimary,
                roundedShape
            )
            .background(MaterialTheme.customColors.bgSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(start = Dimens.x2, end = Dimens.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .clickable(onClick = onMinus),
                painter = painterResource(R.drawable.ic_neu_minus_24),
                contentDescription = null,
                tint = MaterialTheme.customColors.fgSecondary,
            )
            TextField(
                modifier = Modifier
                    .testTagAsId("InvitationsInput")
                    .weight(1f),
                value = count.toString(),
                onValueChange = {
                    onCountChange.invoke(it.toIntOrNull() ?: 0)
                },
                textStyle = MaterialTheme.customTypography.displayM.copy(textAlign = TextAlign.Center),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.customColors.fgPrimary,
                    cursorColor = MaterialTheme.customColors.fgPrimary,
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Icon(
                modifier = Modifier
                    .clickable(onClick = onPlus),
                painter = painterResource(R.drawable.ic_plus_24_inside),
                contentDescription = null,
                tint = MaterialTheme.customColors.fgSecondary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2),
        ) {
            Text(
                text = amount,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Left,
                maxLines = 1,
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary,
            )
            Text(
                text = String.format(
                    "%s: %s",
                    stringResource(id = R.string.common_balance),
                    balance
                ),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Right,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
fun PreviewReferralBondXor01() {
    SoraAppTheme {
        ReferralBondXor(
            common = ReferralCommonState(
                activate = true,
                progress = false,
                referrer = "address",
                referrerFee = "0.005 XOR",
                extrinsicFee = "0.002 XOR",
                extrinsicFeeFiat = "$12"
            ),
            state = ReferralBondState(
                invitationsCount = 2,
                invitationsAmount = "0.098 XOR",
                balance = "123.56743 XOR"
            ),
            onBondInvitationsCountChange = {},
            onBondMinus = {},
            onBondPlus = {},
            onBondClick = {}
        )
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun PreviewReferralBondXor02() {
    SoraAppTheme {
        ReferralBondXor(
            common = ReferralCommonState(
                activate = true,
                progress = false,
                referrer = "address",
                referrerFee = "0.005 XOR",
                extrinsicFee = "0.002 XOR",
                extrinsicFeeFiat = "$12"
            ),
            state = ReferralBondState(
                invitationsCount = 2,
                invitationsAmount = "0.098 XOR",
                balance = "123.56743 XOR"
            ),
            onBondInvitationsCountChange = {},
            onBondMinus = {},
            onBondPlus = {},
            onBondClick = {}
        )
    }
}
