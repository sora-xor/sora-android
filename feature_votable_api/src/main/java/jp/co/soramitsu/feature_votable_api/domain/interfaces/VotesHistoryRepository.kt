/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.interfaces

import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory

interface VotesHistoryRepository {

    fun getVotesHistory(count: Int, offset: Int, updateCached: Boolean): List<VotesHistory>
}
