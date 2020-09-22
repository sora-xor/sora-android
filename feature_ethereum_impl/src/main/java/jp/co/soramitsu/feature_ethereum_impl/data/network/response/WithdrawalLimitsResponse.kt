/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.WithdrawalLimits

data class WithdrawalLimitsResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("xorWithdrawalLimits") val xorWithdrawalLimits: WithdrawalLimits
)