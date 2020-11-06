package jp.co.soramitsu.feature_votable_impl.data.mappers

import jp.co.soramitsu.core_db.model.ReferendumLocal
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.ReferendumStatus
import jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote
import java.util.Date

fun ReferendumLocal.toReferendum() =
    Referendum(
        id = id,
        imageLink = imageLink,
        name = name,
        status = ReferendumStatus.valueOf(status.toString()),
        statusUpdateTime = Date(statusUpdateTime),
        opposeVotes = opposeVotes,
        supportVotes = supportVotes,
        userOpposeVotes = userOpposeVotes,
        userSupportVotes = userSupportVotes,
        description = description,
        detailedDescription = detailedDescription,
        deadline = Date(fundingDeadline)
    )

fun Referendum.toReferendumLocal() =
    ReferendumLocal(
        description, detailedDescription, deadline.time,
        id = id,
        imageLink = imageLink,
        name = name,
        status = ReferendumStatusLocal.valueOf(status.toString()),
        statusUpdateTime = statusUpdateTime.time,
        opposeVotes = opposeVotes,
        supportVotes = supportVotes,
        userOpposeVotes = userOpposeVotes,
        userSupportVotes = userSupportVotes
    )

fun ReferendumRemote.toReferendum() =
    Referendum(
        id = id,
        imageLink = imageLink,
        name = name,
        status = ReferendumStatus.valueOf(status),
        statusUpdateTime = Date(statusUpdateTime * 1000L),
        opposeVotes = opposeVotes,
        supportVotes = supportVotes,
        userOpposeVotes = userOpposeVotes,
        userSupportVotes = userSupportVotes,
        description = description,
        detailedDescription = detailedDescription,
        deadline = Date(fundingDeadline * 1000L)
    )