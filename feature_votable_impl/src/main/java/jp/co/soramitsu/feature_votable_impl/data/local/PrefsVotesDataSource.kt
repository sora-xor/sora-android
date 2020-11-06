package jp.co.soramitsu.feature_votable_impl.data.local

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.SingleTransformer
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.network.ProjectNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectVotesResponse
import javax.inject.Inject

class PrefsVotesDataSource @Inject constructor(
    private val projectNetworkApi: ProjectNetworkApi,
    private val preferences: Preferences
) : VotesDataSource {

    companion object {
        private const val KEY_VOTES = "key_votes"
        private const val KEY_LAST_VOTES = "key_projects_last_voted"
    }

    private val votesObserver = BehaviorSubject.createDefault(retrieveVotes())

    override fun syncVotes(): Completable {
        return projectNetworkApi.getVotes()
            .compose(writeVotesComposer())
            .ignoreElement()
    }

    override fun observeVotes(): Observable<String> {
        return if (hasLocalCopy()) {
            votesObserver
        } else {
            projectNetworkApi.getVotes()
                .compose(writeVotesComposer())
                .flatMapObservable { votesObserver }
        }
    }

    override fun saveVotes(votes: String) {
        preferences.putString(KEY_VOTES, votes)

        votesObserver.onNext(votes)
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

    private fun writeVotesComposer() = SingleTransformer<GetProjectVotesResponse, GetProjectVotesResponse> { single ->
        single.doOnSuccess {
            saveVotes(it.votes.toString())
            saveLastReceivedVotes(it.lastReceivedVotes.toString())
        }
    }

    private fun hasLocalCopy() = preferences.contains(KEY_VOTES)
}
