/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import java.math.BigDecimal

data class GetProjectVotesResponse(
    @SerializedName("votes") val votes: BigDecimal,
    @SerializedName("lastReceivedVotes") val lastReceivedVotes: BigDecimal,
    @SerializedName("status") val status: StatusDto
)