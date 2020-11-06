/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_impl.data.network.model.ReputationRemote

fun mapReputationRemoteToReputation(reputationRemote: ReputationRemote): Reputation {
    return with(reputationRemote) {
        Reputation(rank, reputation, totalRank)
    }
}