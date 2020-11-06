package jp.co.soramitsu.feature_account_impl.data.network.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class CountryRemote(
    @SerializedName("sectionName") val sectionName: String,
    @SerializedName("topics") val topics: JsonObject
)