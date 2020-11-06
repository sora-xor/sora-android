package jp.co.soramitsu.feature_votable_impl.data.network.model

import com.google.gson.annotations.SerializedName

data class GalleryItemRemote(
    @SerializedName("type") val type: GalleryItemTypeRemote,
    @SerializedName("url") val url: String,
    @SerializedName("preview") val preview: String?,
    @SerializedName("duration") val duration: Int?
)