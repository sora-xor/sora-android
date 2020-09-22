/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class EthRegistrationFailedEventRemote(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("operationId") val operationId: String,
    @SerializedName("reason") val reason: String?
) : EventRemote() {

    override fun getEventType(): Type {
        return Type.EthRegistrationFailed
    }
}