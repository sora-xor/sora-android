package jp.co.soramitsu.feature_votable_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_votable_impl.data.network.model.ProjectRemote

data class GetProjectResponse(
    @SerializedName("projects") val projects: List<ProjectRemote>,
    @SerializedName("status") val status: StatusDto
)