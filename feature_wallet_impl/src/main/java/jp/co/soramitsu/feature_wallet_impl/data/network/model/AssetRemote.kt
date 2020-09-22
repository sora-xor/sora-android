/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class AssetRemote(
    @SerializedName("id") val id: String,
    @SerializedName("balance") val balance: BigDecimal
)