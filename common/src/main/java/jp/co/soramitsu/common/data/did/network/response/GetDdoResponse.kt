package jp.co.soramitsu.common.data.did.network.response

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto

data class GetDdoResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("ddo") val ddo: JsonObject?
)