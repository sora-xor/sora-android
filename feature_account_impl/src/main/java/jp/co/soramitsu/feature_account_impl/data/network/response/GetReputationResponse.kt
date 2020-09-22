package jp.co.soramitsu.feature_account_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_account_impl.data.network.model.ReputationRemote

data class GetReputationResponse(
    @SerializedName("reputation") var reputation: ReputationRemote,
    @SerializedName("status") var status: StatusDto
)