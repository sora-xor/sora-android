/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
