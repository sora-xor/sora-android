/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.local

import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

class PrefsVotesDataSource @Inject constructor(
    private val soraPreferences: SoraPreferences
) : VotesDataSource {

    companion object {
        private const val KEY_VOTES = "key_votes"
        private const val KEY_LAST_VOTES = "key_projects_last_voted"
    }

    private val votesObserver = MutableStateFlow<String>("0")

    override fun observeVotes(): Flow<String> {
        return votesObserver.asStateFlow().filterNotNull()
    }

    override suspend fun saveVotes(votes: String) {
        soraPreferences.putString(KEY_VOTES, votes)

        votesObserver.value = votes
    }

    override suspend fun retrieveVotes(): String {
        return soraPreferences.getString(KEY_VOTES)
    }

    override suspend fun saveLastReceivedVotes(toString: String) {
        soraPreferences.putString(KEY_LAST_VOTES, toString)
    }

    override suspend fun retrieveLastReceivedVotes(): String {
        return soraPreferences.getString(KEY_LAST_VOTES)
    }
}
