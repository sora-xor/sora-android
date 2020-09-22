package jp.co.soramitsu.feature_votable_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import java.math.BigDecimal

data class GetProjectVotesResponse(
    @SerializedName("votes") val votes: BigDecimal,
    @SerializedName("lastReceivedVotes") val lastReceivedVotes: BigDecimal,
    @SerializedName("status") val status: StatusDto
)