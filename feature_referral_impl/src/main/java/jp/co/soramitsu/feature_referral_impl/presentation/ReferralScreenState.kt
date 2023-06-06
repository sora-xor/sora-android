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

import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState

internal val emptyState = ReferralProgramState(
    common = ReferralCommonState(
        referrer = null,
        activate = false,
        progress = false,
        referrerFee = "",
        extrinsicFee = "",
        extrinsicFeeFiat = "",
    ),
    bondState = ReferralBondState(
        isBond = false,
        invitationsCount = 0,
        invitationsAmount = "",
        balance = "",
    ),
    referrerInputState = InputTextState(),
    referralInvitationsCardState = ReferralInvitationsCardState(
        title = "",
        invitationsCount = 0,
        wrappedTextState = WrappedTextState(),
        bondedXorString = "",
        referrals = ReferralsCardState(
            rewards = emptyList(),
            totalRewards = "",
            isExpanded = false,
        )
    ),
)

data class ReferralProgramState(
    val common: ReferralCommonState,
    val bondState: ReferralBondState,
    val referrerInputState: InputTextState,
    val referralInvitationsCardState: ReferralInvitationsCardState,
) {
    fun isInitialized(): Boolean {
        return common.referrer != null || referralInvitationsCardState.referrals.rewards.isNotEmpty() || referralInvitationsCardState.invitationsCount > 0
    }
}

data class ReferralCommonState(
    val referrer: String? = null,
    val activate: Boolean,
    val progress: Boolean = false,
    val referrerFee: String,
    val extrinsicFee: String,
    val extrinsicFeeFiat: String,
)

data class ReferralBondState(
    val isBond: Boolean = false,
    val invitationsCount: Int,
    val invitationsAmount: String,
    val balance: String,
)

data class ReferralsCardState(
    val rewards: List<ReferralModel> = emptyList(),
    val totalRewards: String = "",
    val isExpanded: Boolean = true,
)

data class ReferralInvitationsCardState(
    val title: String,
    val invitationsCount: Int,
    val wrappedTextState: WrappedTextState,
    val bondedXorString: String,
    val referrals: ReferralsCardState,
)

data class ReferralModel(val address: String, val amountFormatted: String)
