/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

data class Invitations(
    val acceptedInviteVms: List<InvitedUser>,
    val parentInvitations: InvitedUser?
)
