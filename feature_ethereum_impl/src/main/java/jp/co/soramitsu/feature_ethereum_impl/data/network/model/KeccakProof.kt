/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class KeccakProof(
    @SerializedName("v") val v: String,
    @SerializedName("r") val r: String,
    @SerializedName("s") val s: String
)