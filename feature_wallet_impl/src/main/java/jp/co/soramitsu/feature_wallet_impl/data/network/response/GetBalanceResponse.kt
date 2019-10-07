/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import jp.co.soramitsu.feature_wallet_impl.data.network.model.AssetRemote

data class GetBalanceResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("assets") val assets: Array<AssetRemote>
)