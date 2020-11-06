package jp.co.soramitsu.feature_information_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_information_impl.data.network.model.InformationRemote

data class GetInformationResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("information") val information: InformationRemote
)