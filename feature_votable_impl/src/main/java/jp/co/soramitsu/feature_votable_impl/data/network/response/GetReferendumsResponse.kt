package jp.co.soramitsu.feature_votable_impl.data.network.response

import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote

class GetReferendumsResponse(
    val referendums: List<jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote>,
    val status: StatusDto
)