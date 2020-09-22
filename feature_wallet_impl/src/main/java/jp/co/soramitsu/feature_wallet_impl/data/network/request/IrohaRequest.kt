package jp.co.soramitsu.feature_wallet_impl.data.network.request

import com.google.gson.annotations.SerializedName

data class IrohaRequest(
    @SerializedName("transaction") val transaction: String
)