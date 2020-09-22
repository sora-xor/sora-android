/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.network.request

import com.google.gson.annotations.SerializedName

data class PushRegistrationRequest(
    @SerializedName("pushTokens") val pushTokens: List<String>,
    @SerializedName("didsForPermit") val didsForPermit: List<String>
)