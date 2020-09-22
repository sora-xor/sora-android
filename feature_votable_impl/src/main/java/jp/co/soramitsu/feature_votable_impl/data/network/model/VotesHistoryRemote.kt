/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class VotesHistoryRemote(
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("votes") val votes: BigDecimal
)