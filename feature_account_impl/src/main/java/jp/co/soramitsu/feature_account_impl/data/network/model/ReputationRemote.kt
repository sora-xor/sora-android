/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class ReputationRemote(
    @SerializedName("rank") val rank: Int,
    @SerializedName("reputation") val reputation: Float,
    @SerializedName("totalRank") val totalRank: Int
)