package jp.co.soramitsu.common.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto

data class BaseResponse(
    @SerializedName("status") val status: StatusDto
)