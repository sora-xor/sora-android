/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType

data class GetTransferMetaResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("feeRate") val feeRate: Double,
    @SerializedName("feeType") val feeType: FeeType
)