/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.model

data class Reputation(
    val rank: Int,
    val reputation: Float,
    val totalRank: Int
)