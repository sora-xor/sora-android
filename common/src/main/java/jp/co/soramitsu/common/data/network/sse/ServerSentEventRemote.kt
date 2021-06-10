/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.sse

import com.google.gson.annotations.SerializedName

data class ServerSentEventRemote(
    @SerializedName("type") val type: Type
) {
    enum class Type {
        @SerializedName("signal") SIGNAL,
        @SerializedName("event") EVENT
    }
}
