/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import com.google.gson.annotations.SerializedName

data class StatusDto(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String
)
