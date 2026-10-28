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
    val shouldTransactionReminderInsufficientWarningBeShown: Boolean = false,
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
