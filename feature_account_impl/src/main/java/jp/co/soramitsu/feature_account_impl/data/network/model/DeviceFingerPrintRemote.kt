package jp.co.soramitsu.feature_account_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class DeviceFingerPrintRemote(
    @SerializedName("model") val model: String,
    @SerializedName("osVersion") val osVersion: String,
    @SerializedName("screenWidth") val screenWidth: Int,
    @SerializedName("screenHeight") val screenHeight: Int,
    @SerializedName("language") val language: String,
    @SerializedName("country") val country: String,
    @SerializedName("timezone") val timezone: Int
)