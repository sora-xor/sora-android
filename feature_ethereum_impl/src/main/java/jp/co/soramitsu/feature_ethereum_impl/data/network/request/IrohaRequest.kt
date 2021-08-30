package jp.co.soramitsu.feature_ethereum_impl.data.network.request

import com.google.gson.annotations.SerializedName

data class IrohaRequest(
    @SerializedName("transaction") val transaction: String
)
