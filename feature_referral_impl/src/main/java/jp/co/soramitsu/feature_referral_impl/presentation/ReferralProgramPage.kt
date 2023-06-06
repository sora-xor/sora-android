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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ReferralProgramPage(
    state: ReferralProgramState,
    onGetMoreInvitations: () -> Unit,
    onUnboundXor: () -> Unit,
    onEnterLink: () -> Unit,
    onReferralsCardHeadClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        ReferralAvailableInvitationsCard(
            state = state.referralInvitationsCardState,
            modifier = Modifier.padding(top = Dimens.x1_5, bottom = Dimens.x2),
            onGetMoreInvitations = onGetMoreInvitations,
            onUnboundXor = onUnboundXor,
            onShareClick = onShareClick
        )

        YourReferrerCard(
            state = state.common,
            modifier = Modifier.padding(bottom = Dimens.x2),
            onEnterReferrersLink = onEnterLink
        )

        if (state.referralInvitationsCardState.referrals.rewards.isNotEmpty()) {
            YourReferralsCard(
                state = state.referralInvitationsCardState.referrals,
                modifier = Modifier.padding(bottom = Dimens.x2),
                onHeaderClick = onReferralsCardHeadClick
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWelcomePageScreen() {
    ReferralProgramPage(
        state = ReferralProgramState(
            common = ReferralCommonState(
                activate = false,
                progress = false,
                referrer = "address",
                referrerFee = "0.003 XOR",
                extrinsicFee = "0.234 XOR",
                extrinsicFeeFiat = "$12"
            ),
            bondState = ReferralBondState(
                invitationsCount = 2, invitationsAmount = "0.9 XOR", balance = "12 XOR"
            ),
            referrerInputState = InputTextState(),
            referralInvitationsCardState = ReferralInvitationsCardState(
                "Available Invitations",
                5,
                WrappedTextState(title = "Invitations Link", text = "polkaswap.io/#/referral/cnVyaue39dssBc2bReZycusLdys3vbeoz2irRF8BbwVcdCNmm"),
                "0.007 XOR",
                referrals = ReferralsCardState()
            )
        ),
        onGetMoreInvitations = {
        },
        onUnboundXor = {
        },
        onEnterLink = {
        },
        onReferralsCardHeadClick = {
        },
        onShareClick = {
        }
    )
}
