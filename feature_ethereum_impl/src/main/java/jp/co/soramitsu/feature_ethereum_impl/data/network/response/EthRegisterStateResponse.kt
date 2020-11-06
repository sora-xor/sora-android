package jp.co.soramitsu.feature_ethereum_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto

data class EthRegisterStateResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("address") val address: String?,
    @SerializedName("state") val state: State,
    @SerializedName("reason") val reason: String?
) {

    enum class State {
        INPROGRESS,
        COMPLETED,
        FAILED
    }
}