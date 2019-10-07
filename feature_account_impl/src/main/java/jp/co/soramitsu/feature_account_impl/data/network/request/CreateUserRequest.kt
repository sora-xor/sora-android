/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.network.request

import com.google.gson.annotations.SerializedName

data class CreateUserRequest(
    @SerializedName("phone") val phone: String
)