package jp.co.soramitsu.feature_votable_impl.data.network.response

import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote

class VoteResponse(
    val status: StatusDto,
    val referendum: ReferendumRemote,
    val votesRemain: String
)