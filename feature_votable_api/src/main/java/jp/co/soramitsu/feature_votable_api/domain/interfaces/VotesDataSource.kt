/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.interfaces

import kotlinx.coroutines.flow.Flow

interface VotesDataSource {
    fun observeVotes(): Flow<String>

    suspend fun saveVotes(votes: String)

    suspend fun retrieveVotes(): String

    suspend fun saveLastReceivedVotes(toString: String)

    suspend fun retrieveLastReceivedVotes(): String
}
