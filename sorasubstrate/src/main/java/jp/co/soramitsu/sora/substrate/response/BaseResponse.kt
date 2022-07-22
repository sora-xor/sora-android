/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import com.google.gson.annotations.SerializedName

data class BaseResponse(
    @SerializedName("status") val status: StatusDto
)
