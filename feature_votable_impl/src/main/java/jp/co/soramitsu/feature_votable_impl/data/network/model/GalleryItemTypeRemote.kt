package jp.co.soramitsu.feature_votable_impl.data.network.model

import com.google.gson.annotations.SerializedName

enum class GalleryItemTypeRemote {
    @SerializedName("IMAGE") IMAGE,
    @SerializedName("VIDEO") VIDEO
}