/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_account_impl.data.network.model.UserRemote

data class GetUserResponse(
    @SerializedName("user") val user: UserRemote,
    @SerializedName("status") val status: StatusDto
)