/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.model

import java.math.BigDecimal

data class VotesHistory(
    val message: String,
    val timestamp: String,
    val votes: BigDecimal
)
