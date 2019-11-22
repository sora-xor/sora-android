/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.net.URL

data class ProjectDetailsRemote(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String?,
    @SerializedName("description") val description: String,
    @SerializedName("detailedDescription") val detailedDescription: String?,
    @SerializedName("fundingCurrent") val fundingCurrent: Long,
    @SerializedName("fundingTarget") val fundingTarget: Long,
    @SerializedName("fundingDeadline") val fundingDeadline: Long,
    @SerializedName("projectLink") val projectLink: URL,
    @SerializedName("status") val status: String,
    @SerializedName("imageLink") val imageLink: URL,
    @SerializedName("favorite") val favorite: Boolean,
    @SerializedName("unwatched") val unwatched: Boolean,
    @SerializedName("votedFriendsCount") val votedFriendsCount: Int,
    @SerializedName("favoriteCount") val favoriteCount: Int,
    @SerializedName("votes") val votes: BigDecimal,
    @SerializedName("gallery") val gallery: List<GalleryItemRemote>?,
    @SerializedName("statusUpdateTime") val statusUpdateTime: Long,
    @SerializedName("discussionLink") val discussionLinkRemote: DiscussionLinkRemote?
)