/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.repository.datasource

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectDatasource
import javax.inject.Inject

class PrefsProjectDatasource @Inject constructor(
    private val preferences: Preferences
) : ProjectDatasource {

    companion object {
        private const val KEY_VOTES = "key_votes"
        private const val KEY_LAST_VOTES = "key_projects_last_voted"
    }

    override fun saveVotes(votes: String) {
        preferences.putString(KEY_VOTES, votes)
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
}
