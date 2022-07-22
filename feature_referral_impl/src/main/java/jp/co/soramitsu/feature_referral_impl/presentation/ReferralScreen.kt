/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.components.ContainedButton
import jp.co.soramitsu.common.presentation.compose.components.ProgressContainedButton
import jp.co.soramitsu.common.presentation.compose.components.RegularButton
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPressed
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.NeuColorsCompat
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuBold18
import jp.co.soramitsu.common.presentation.compose.theme.neuBold24
import jp.co.soramitsu.common.presentation.compose.theme.neuMedium15
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular11
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular12
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular15
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular16
import jp.co.soramitsu.feature_referral_impl.R

@Composable
fun YourReferrer(
    referrer: String?,
    onClick: () -> Unit
) {
    NeuCardPunched(
        modifier = Modifier.padding(Dimens.x2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(text = stringResource(id = R.string.referral_your_referrer), style = neuBold18)
            if (referrer == null) {
                RegularButton(
                    modifier = Modifier.padding(top = Dimens.x2),
                    label = stringResource(id = R.string.referral_enter_link_title),
                    onClick = onClick
                )
            } else {
                Text(
                    text = stringResource(id = R.string.referral_referrer_address),
                    style = neuRegular12,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = referrer,
                    style = neuRegular16,
                    modifier = Modifier.fillMaxWidth(),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AvailableInvitations(
    invitations: Int,
    link: String,
    bonded: String,
    onBondClick: () -> Unit,
    onUnbondClick: () -> Unit,
    onShareLinkClick: () -> Unit,
) {
    NeuCardPunched(
        modifier = Modifier.padding(Dimens.x2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.referral_invitaion_link_title),
                    style = neuBold18,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = String.format("%d", invitations),
                    style = neuBold18,
                    modifier = Modifier.alignByBaseline()
                )
            }
            NeuCardPressed(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Row(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        start = 16.dp,
                        bottom = 8.dp,
                        end = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.referral_your_invitation_link_title),
                            modifier = Modifier.fillMaxWidth(),
                            style = neuRegular12,
                            color = NeuColorsCompat.color9a9a9a,
                        )
                        Text(
                            text = link,
                            modifier = Modifier.fillMaxWidth(),
                            overflow = TextOverflow.Ellipsis,
                            style = neuRegular16,
                            maxLines = 1,
                            color = Color.Black,
                        )
                    }
                    Image(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_share_24),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                onShareLinkClick.invoke()
                            }
                            .background(
                                color = NeuColorsCompat.neuBackgroundImage,
                                shape = CircleShape
                            )
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.Black),
                        contentDescription = null
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 8.dp, end = 8.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.wallet_bonded),
                    style = MaterialTheme.typography.neuRegular15,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = bonded,
                    style = MaterialTheme.typography.neuRegular15,
                    modifier = Modifier.alignByBaseline()
                )
            }
            ReferralButtons(
                modifier = Modifier.padding(horizontal = 8.dp),
                onTopButtonClick = onBondClick,
                onBottomButtonClick = onUnbondClick,
                topButtonText = R.string.referral_get_more_invitation_button_title,
                bottomButtonText = R.string.referral_unbond_button_title,
            )
        }
    }
}

@Composable
internal fun SheetContentBondXor(
    commonState: ReferrerState,
    state: ReferralBondState,
    onBondInvitationsCountChange: (Int) -> Unit,
    onBondMinus: () -> Unit,
    onBondPlus: () -> Unit,
    onBondClick: () -> Unit,
) {
    Text(
        text = stringResource(id = R.string.referral_input_reward_title),
        color = NeuColorsCompat.color281818,
        style = neuBold18,
        modifier = Modifier.padding(bottom = Dimens.x2),
    )
    Text(
        text = stringResource(
            id = R.string.referral_input_reward_description,
            commonState.referrerFee
        ),
        color = NeuColorsCompat.color281818,
        style = neuMedium15,
        modifier = Modifier.padding(bottom = Dimens.x3),
    )
    InvitationsEnterField(
        balance = state.balance,
        amount = state.invitationsAmount,
        count = state.invitationsCount,
        onMinus = { onBondMinus.invoke() },
        onPlus = { onBondPlus.invoke() },
        onCountChange = { onBondInvitationsCountChange.invoke(it) },
    )
    Text(
        text = String.format(
            "%s: %s",
            stringResource(id = R.string.polkaswap_network_fee),
            commonState.extrinsicFee
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.x2),
        overflow = TextOverflow.Ellipsis,
        style = neuMedium15,
        maxLines = 1,
        textAlign = TextAlign.Center,
        color = NeuColorsCompat.neuColor9d8181
    )
    ProgressContainedButton(
        label = stringResource(id = R.string.referral_bond_button_title),
        modifier = Modifier.padding(top = Dimens.x1),
        enabled = commonState.activate && state.invitationsCount > 0,
        progress = commonState.progress,
    ) {
        onBondClick.invoke()
    }
}

@Composable
internal fun SheetContentUnbondXor(
    commonState: ReferrerState,
    state: ReferralBondState,
    onUnbondInvitationsCountChange: (Int) -> Unit,
    onUnbondMinus: () -> Unit,
    onUnbondPlus: () -> Unit,
    onUnbondClick: () -> Unit,
) {
    Text(
        text = stringResource(
            id = R.string.referral_unbond_xor_description,
            commonState.referrerFee
        ),
        style = neuMedium15,
        modifier = Modifier.padding(bottom = 16.dp),
        color = NeuColorsCompat.color281818
    )
    InvitationsEnterField(
        balance = state.balance,
        amount = state.invitationsAmount,
        count = state.invitationsCount,
        onMinus = { onUnbondMinus.invoke() },
        onPlus = { onUnbondPlus.invoke() },
        onCountChange = { onUnbondInvitationsCountChange.invoke(it) }
    )
    Text(
        text = String.format(
            "%s: %s",
            stringResource(id = R.string.polkaswap_network_fee),
            commonState.extrinsicFee
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.x2),
        overflow = TextOverflow.Ellipsis,
        style = neuMedium15,
        maxLines = 1,
        textAlign = TextAlign.Center,
        color = NeuColorsCompat.neuColor9d8181
    )
    ProgressContainedButton(
        label = stringResource(id = R.string.referral_unbond_button_title),
        modifier = Modifier.padding(top = Dimens.x1),
        enabled = commonState.activate && state.invitationsCount > 0,
        progress = commonState.progress,
    ) {
        onUnbondClick.invoke()
    }
}

@Composable
private fun InvitationsEnterField(
    balance: String,
    amount: String,
    count: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onCountChange: (Int) -> Unit,
) {
    NeuCardPressed(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        radius = 24
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onMinus.invoke()
                        },
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_neu_minus_24),
                    contentDescription = null
                )
                TextField(
                    value = count.toString(),
                    onValueChange = {
                        onCountChange.invoke(it.toIntOrNull() ?: 0)
                    },
                    textStyle = neuBold24.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Image(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onPlus.invoke()
                        },
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_neu_plus_24),
                    contentDescription = null
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.x1)
            ) {
                Text(
                    text = amount,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left,
                    maxLines = 1,
                    style = neuRegular11,
                    color = NeuColorsCompat.neuTintDark,
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
                    style = neuRegular11,
                    color = NeuColorsCompat.neuTintDark,
                )
            }
        }
    }
}

@Composable
fun YourReferralsCard(referralsModel: ReferralsCardModel, onHeaderClick: () -> Unit) {
    NeuCardPunched(
        modifier = Modifier.padding(Dimens.x2)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Dimens.x3)
        ) {
            ReferralsCardHeader(
                referralsModel.totalRewards,
                referralsModel.rewards.size.toString(),
                { onHeaderClick.invoke() },
                referralsModel.isExpanded,
            )
            if (referralsModel.isExpanded && referralsModel.rewards.isNotEmpty()) {
                Divider(
                    color = ThemeColors.Secondary,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = Dimens.x2)
                )
                referralsModel.rewards.forEach { reward ->
                    ReferralsCardItem(referral = reward)
                }
            }
        }
    }
}

@Composable
private fun ReferralsCardHeader(
    totalRewards: String,
    size: String,
    headerClickHandler: () -> Unit,
    isExpanded: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.x3)
            .clickable { headerClickHandler.invoke() }
    ) {
        Text(
            text = stringResource(id = R.string.referral_your_referrals),
            textAlign = TextAlign.Start,
            style = neuBold18
        )
        Image(
            modifier = Modifier
                .size(Dimens.x4)
                .padding(start = Dimens.x1),
            alignment = Alignment.Center,
            painter = painterResource(if (isExpanded) R.drawable.ic_chevron_up_rounded_16 else R.drawable.ic_chevron_down_rounded_16),
            contentDescription = null
        )
        Text(
            text = size,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            style = neuBold18
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.x3)
            .padding(top = Dimens.x1)
    ) {
        Text(
            text = stringResource(id = R.string.referral_total_rewards),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.neuRegular15
        )
        Text(
            text = totalRewards,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.neuRegular15
        )
    }
}

@Composable
private fun ReferralsCardItem(referral: ReferralModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.x1, horizontal = Dimens.x3)
    ) {
        Text(
            text = referral.address,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.neuRegular15
        )
        Text(
            text = referral.amountFormatted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.neuRegular15
        )
    }
}

@Composable
internal fun ReferralButtons(
    modifier: Modifier = Modifier,
    onTopButtonClick: () -> Unit,
    onBottomButtonClick: () -> Unit,
    topButtonText: Int,
    bottomButtonText: Int,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedButton(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .fillMaxWidth(),
            label = stringResource(id = topButtonText),
            onClick = onTopButtonClick
        )

        RegularButton(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .fillMaxWidth(),
            label = stringResource(id = bottomButtonText),
            onClick = onBottomButtonClick
        )
    }
}

@Preview
@Composable
private fun SheetContentUnbondXorPreview() {
    Column {
        SheetContentUnbondXor(
            commonState = ReferrerState(
                referrer = "address",
                activate = true,
                progress = false,
                referrerFee = "0.005 XOR",
                extrinsicFee = "0.002 XOR"
            ),
            state = ReferralBondState(
                invitationsCount = 2,
                invitationsAmount = "0.098 XOR",
                balance = "123.56743 XOR"
            ),
            {}, {}, {}, {}
        )
    }
}
