/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
