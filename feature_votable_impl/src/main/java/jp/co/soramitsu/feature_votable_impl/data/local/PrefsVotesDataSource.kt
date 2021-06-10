/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.local

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import javax.inject.Inject

class PrefsVotesDataSource @Inject constructor(
    private val preferences: Preferences
) : VotesDataSource {

    companion object {
        private const val KEY_VOTES = "key_votes"
        private const val KEY_LAST_VOTES = "key_projects_last_voted"
    }

    private val votesObserver = BehaviorSubject.createDefault(retrieveVotes())

    override fun observeVotes(): Observable<String> {
        return if (hasLocalCopy()) {
            votesObserver
        } else {
            Observable.just("0")
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

    private fun hasLocalCopy() = preferences.contains(KEY_VOTES)
}
