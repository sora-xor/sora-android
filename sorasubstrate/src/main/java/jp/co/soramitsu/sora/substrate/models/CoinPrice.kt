/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SoraCoin(
    @SerialName("pair") val pair: String,
    @SerialName("price") val price: String,
    @SerialName("source") val source: String,
    @SerialName("update_time") val update_time: Long,
)
