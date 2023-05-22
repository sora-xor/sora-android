/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain.subs

import javax.inject.Inject
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge

class GlobalSubscriptionManager @Inject constructor(
    private val featureStorageManagers:
    @JvmSuppressWildcards Map<String, SingleFeatureStorageManager>,
    private val db: AppDatabase,
    private val userRepository: UserRepository,
) {

    fun start(): Flow<String> {
        return userRepository.flowCurSoraAccount()
            .flatMapLatest { account ->
                db.cardsHubDao().getCardsHubVisible(account.substrateAddress)
                    .distinctUntilChanged { old, new ->
                        isHubsEqual(old, new)
                    }
                    .flatMapLatest { cards ->
                        val subscriptionList = cards.mapNotNull {
                            featureStorageManagers[it.cardId]?.subscribe(account.substrateAddress)
                        }
                        subscriptionList.merge()
                    }
            }
    }

    private fun isHubsEqual(old: List<CardHubLocal>, new: List<CardHubLocal>): Boolean {
        if (old.size == new.size) {
            for ((index, value) in old.withIndex()) {
                if (value.cardId != new[index].cardId || value.visibility != new[index].visibility) return false
            }
            return true
        } else {
            return false
        }
    }
}
