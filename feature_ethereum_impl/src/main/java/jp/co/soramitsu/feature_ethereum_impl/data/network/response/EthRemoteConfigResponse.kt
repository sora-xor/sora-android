package jp.co.soramitsu.feature_ethereum_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto

data class EthRemoteConfigResponse(
    @SerializedName("status") val status: StatusDto,
    @SerializedName("masterContractAddress") val master: String,
    @SerializedName("etherscanBaseUrl") val scanUrl: String,
    @SerializedName("ethereumUsername") val username: String,
    @SerializedName("ethereumUrl") val url: String,
    @SerializedName("ethereumPassword") val password: String
)
