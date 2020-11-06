/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network.response

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto

data class GetActivityFeedResponse(
    @SerializedName("activity") val activity: List<JsonObject>,
    @SerializedName("projectsDict") val projectsDict: JsonObject,
    @SerializedName("usersDict") val usersDict: JsonObject,
    @SerializedName("status") val status: StatusDto
)