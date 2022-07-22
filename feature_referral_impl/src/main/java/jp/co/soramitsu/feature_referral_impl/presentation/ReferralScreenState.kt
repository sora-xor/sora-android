/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

enum class DetailedBottomSheet { REQUEST_REFERRER, SHOW_REFERRER, BOND, UNBOND }

data class ReferralProgramState(
    val common: ReferrerState,
    val screen: ReferralProgramStateScreen,
    val bondState: ReferralBondState
)

sealed interface ReferralProgramStateScreen {
    object Initial : ReferralProgramStateScreen

    data class ReferralProgramData(
        val invitations: Int,
        val bonded: String,
        val link: String,
        val referrals: ReferralsCardModel,
    ) : ReferralProgramStateScreen
}

data class ReferrerState(
    val referrer: String? = null,
    val activate: Boolean,
    val progress: Boolean = false,
    val referrerFee: String,
    val extrinsicFee: String,
)

data class ReferralBondState(
    val invitationsCount: Int,
    val invitationsAmount: String,
    val balance: String,
)

data class ReferralsCardModel(
    val rewards: List<ReferralModel> = emptyList(),
    val totalRewards: String = "",
    val isExpanded: Boolean = false,
)

data class ReferralModel(val address: String, val amountFormatted: String)
