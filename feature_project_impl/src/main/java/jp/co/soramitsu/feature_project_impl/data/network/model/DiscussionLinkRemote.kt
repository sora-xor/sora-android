package jp.co.soramitsu.feature_project_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class DiscussionLinkRemote(
    @SerializedName("title") val title: String,
    @SerializedName("link") val link: String
)