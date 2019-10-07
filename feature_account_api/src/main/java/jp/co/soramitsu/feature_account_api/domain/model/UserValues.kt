/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.model

data class UserValues(
    val invitations: Int,
    val tokens: Float,
    val userId: String
)