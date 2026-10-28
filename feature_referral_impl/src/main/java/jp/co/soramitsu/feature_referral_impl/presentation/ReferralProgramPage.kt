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
