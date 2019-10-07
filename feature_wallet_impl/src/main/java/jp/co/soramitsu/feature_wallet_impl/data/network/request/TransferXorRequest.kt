/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.request

import com.google.gson.annotations.SerializedName

data class TransferXorRequest(
    @SerializedName("transaction") val transaction: String
)