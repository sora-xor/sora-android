/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class UserRemote(
    @SerializedName("userId") val userId: String,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("status") val status: String,
    @SerializedName("parentId") val parentId: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("inviteAcceptExpirationMoment") val inviteAcceptExpirationMomentSeconds: Long,
    @SerializedName("userValues") val userValues: UserValuesRemote
)