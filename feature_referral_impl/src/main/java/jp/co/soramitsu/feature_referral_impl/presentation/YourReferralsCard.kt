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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun YourReferralsCard(
    state: ReferralsCardState,
    modifier: Modifier,
    onHeaderClick: () -> Unit
) {
    ContentCard(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Dimens.x3)
        ) {
            ReferralsCardHeader(
                state.totalRewards,
                state.rewards.size.toString(),
                onHeaderClick,
                state.isExpanded,
            )
            if (state.isExpanded && state.rewards.isNotEmpty()) {
                Divider(
                    color = MaterialTheme.customColors.fgOutline,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = Dimens.x2)
                )
                state.rewards.forEach { reward ->
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
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.headline2
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
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.headline2
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = Dimens.x3)
            .padding(top = Dimens.x1)
    ) {
        Text(
            text = stringResource(id = R.string.referral_total_rewards),
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textM,

        )
        Text(
            text = totalRewards,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textM
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
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textM
        )
        Text(
            text = referral.amountFormatted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textM
        )
    }
}

@Composable
@Preview
fun PreviewYourReferralsCard() {
    YourReferralsCard(
        state = ReferralsCardState(
            listOf(
                ReferralModel("cnVcC7gt5...NLS45gs3Fga", "1.212 XOR"),
                ReferralModel("cnVcC7gt5...NLS45gs3Fga", "1.212 XOR"),
            ),
            "7.465 XOR",
            true,
        ),
        modifier = Modifier
    ) {}
}
