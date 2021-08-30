package jp.co.soramitsu.feature_votable_api.domain.interfaces

import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory

interface VotesHistoryRepository {

    fun getVotesHistory(count: Int, offset: Int, updateCached: Boolean): List<VotesHistory>
}
