package jp.co.soramitsu.feature_votable_api.domain.interfaces

import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import kotlinx.coroutines.flow.Flow

interface ReferendumRepository {
    fun observeReferendum(referendumId: String): Flow<Referendum>

    fun observeOpenedReferendums(): Flow<List<Referendum>>

    fun observeVotedReferendums(): Flow<List<Referendum>>

    fun observeFinishedReferendums(): Flow<List<Referendum>>
}
