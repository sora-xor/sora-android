/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.network.model

import java.math.BigDecimal

data class ReferendumRemote(
    val description: String,
    val detailedDescription: String,
    val fundingDeadline: Long,
    val id: String,
    val imageLink: String,
    val name: String,
    val status: String,
    val statusUpdateTime: Long,
    val opposeVotes: BigDecimal,
    val supportVotes: BigDecimal,
    val userOpposeVotes: BigDecimal,
    val userSupportVotes: BigDecimal
)