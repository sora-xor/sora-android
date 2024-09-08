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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedText
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ReferralAvailableInvitationsCard(
    state: ReferralInvitationsCardState,
    modifier: Modifier,
    onGetMoreInvitations: () -> Unit,
    onUnboundXor: () -> Unit,
    onShareClick: () -> Unit
) {
    ContentCard(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.x3)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x2)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary
                )

                val countString =
                    if (state.invitationsCount > 0) state.invitationsCount.toString() else ""

                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = countString,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary
                )
            }

            if (state.invitationsCount > 0) {
                WrappedText(
                    modifier = Modifier
                        .padding(bottom = Dimens.x2)
                        .background(MaterialTheme.customColors.bgSurface)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    state = state.wrappedTextState,
                    onClick = onShareClick
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.x3)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(id = R.string.wallet_bonded),
                        style = MaterialTheme.customTypography.textXSBold,
                        color = MaterialTheme.customColors.fgSecondary
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = state.bondedXorString,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.customTypography.textS,
                        color = MaterialTheme.customColors.fgPrimary
                    )
                }
            }

            FilledButton(
                modifier = Modifier
                    .testTagAsId("GetMoreInvitations")
                    .padding(top = Dimens.x1)
                    .fillMaxWidth(),
                text = stringResource(R.string.referral_get_more_invitation_button_title),
                onClick = onGetMoreInvitations,
                size = Size.Large,
                order = Order.PRIMARY
            )

            if (state.invitationsCount > 0) {
                TextButton(
                    modifier = Modifier
                        .testTagAsId("UnbondXor")
                        .padding(top = Dimens.x1)
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.referral_unbond_button_title),
                    onClick = onUnboundXor,
                    size = Size.Large,
                    order = Order.PRIMARY
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewReferralAvailableInvitationsCard() {
    ReferralAvailableInvitationsCard(
        state = ReferralInvitationsCardState(
            "Available invitations",
            5,
            WrappedTextState(
                title = "Invitations Link",
                text = "polkaswap.io/#/referral/cnVyaue39dssBc2bReZycusLdys3vbeoz2irRF8BbwVcdCNmm"
            ),
            "0.0008 XOR",
            referrals = ReferralsCardState()
        ),
        modifier = Modifier,
        onGetMoreInvitations = {},
        onUnboundXor = {},
        onShareClick = {}
    )
}
