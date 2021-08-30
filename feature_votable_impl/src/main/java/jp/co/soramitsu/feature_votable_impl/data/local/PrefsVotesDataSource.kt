package jp.co.soramitsu.feature_votable_impl.data.local

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

class PrefsVotesDataSource @Inject constructor(
    private val preferences: Preferences
) : VotesDataSource {

    companion object {
        private const val KEY_VOTES = "key_votes"
        private const val KEY_LAST_VOTES = "key_projects_last_voted"
    }

    private val votesObserver = MutableStateFlow<String>("0")

    override fun observeVotes(): Flow<String> {
        return votesObserver.asStateFlow().filterNotNull()
    }

    override fun saveVotes(votes: String) {
        preferences.putString(KEY_VOTES, votes)

        votesObserver.value = votes
    }

    override fun retrieveVotes(): String {
        return preferences.getString(KEY_VOTES)
    }

    override fun saveLastReceivedVotes(toString: String) {
        preferences.putString(KEY_LAST_VOTES, toString)
    }

    override fun retrieveLastReceivedVotes(): String {
        return preferences.getString(KEY_LAST_VOTES)
    }

    private fun hasLocalCopy() = preferences.contains(KEY_VOTES)
}
