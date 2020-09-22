package jp.co.soramitsu.feature_votable_impl.data.network.response

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_votable_impl.data.network.model.ProjectDetailsRemote

data class GetProjectDetailsResponse(
    @SerializedName("project") val project: ProjectDetailsRemote,
    @SerializedName("status") val status: StatusDto
)