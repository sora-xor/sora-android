/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.WithdrawalProof
import jp.co.soramitsu.sora.substrate.response.StatusDto

data class WithdrawalProofsResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("proofs") val proofs: List<WithdrawalProof>
)
