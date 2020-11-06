package jp.co.soramitsu.common.data.network.dto

import com.google.gson.annotations.SerializedName

data class StatusDto(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String
)