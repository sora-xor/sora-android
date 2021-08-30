/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.interfaces

import kotlinx.coroutines.flow.Flow

interface VotesDataSource {
    fun observeVotes(): Flow<String>

    fun saveVotes(votes: String)

    fun retrieveVotes(): String

    fun saveLastReceivedVotes(toString: String)

    fun retrieveLastReceivedVotes(): String
}
