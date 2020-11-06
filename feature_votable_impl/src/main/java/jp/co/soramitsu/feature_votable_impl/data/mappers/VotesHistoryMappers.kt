/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.mappers

import jp.co.soramitsu.core_db.model.VotesHistoryLocal
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_votable_impl.data.network.model.VotesHistoryRemote

fun mapVotesHistoryRemoteToVotesHistory(votesHistory: VotesHistoryRemote): VotesHistory {
    return with(votesHistory) {
        VotesHistory(message, timestamp, votes)
    }
}

fun mapVotesHistoryToVotesHistoryLocal(votesHistory: VotesHistory): VotesHistoryLocal {
    return with(votesHistory) {
        VotesHistoryLocal(0, message, timestamp, votes)
    }
}

fun mapVotesHistoryLocalToVotesHistory(votesHistory: VotesHistoryLocal): VotesHistory {
    return with(votesHistory) {
        VotesHistory(message, timestamp, votes)
    }
}