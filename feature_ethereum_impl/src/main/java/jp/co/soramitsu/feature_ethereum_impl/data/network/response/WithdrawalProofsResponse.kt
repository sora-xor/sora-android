package jp.co.soramitsu.feature_ethereum_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.WithdrawalProof

data class WithdrawalProofsResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("proofs") val proofs: List<WithdrawalProof>
)
