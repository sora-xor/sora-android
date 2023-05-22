/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class CardsHubInteractorImpl @Inject constructor(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
) {

    fun subscribeVisibleCardsHubList(): Flow<Pair<SoraAccount, List<CardHub>>> {
        return userRepository.flowCurSoraAccount().flatMapLatest { account ->
            walletRepository.subscribeVisibleGlobalCardsHubList()
                .combine(walletRepository.subscribeVisibleCardsHubList(account.substrateAddress)) { global, local ->
                    global + local
                }
                .map {
                    account to it
                }
        }
    }

    suspend fun updateCardVisibilityOnCardHub(cardId: String, visible: Boolean) {
        walletRepository.updateCardVisibilityOnCardHub(cardId, visible)
    }

    suspend fun updateCardCollapsedStateOnCardHub(cardId: String, collapsed: Boolean) {
        walletRepository.updateCardCollapsedState(cardId, collapsed)
    }

    fun subscribeSoraCardInfo(): Flow<SoraCardInformation?> =
        walletRepository.subscribeSoraCardInfo()
}
