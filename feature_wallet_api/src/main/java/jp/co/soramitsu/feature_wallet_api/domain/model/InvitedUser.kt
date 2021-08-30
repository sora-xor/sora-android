/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.util.Date

data class InvitedUser(
    val userId: String,
    val walletAccountId: String,
    val invitedDate: Date?
)
