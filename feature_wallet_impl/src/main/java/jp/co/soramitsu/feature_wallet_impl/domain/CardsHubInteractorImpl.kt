/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class CardsHubInteractorImpl @Inject constructor(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
) {

    /**
     * The number of accounts, and how many of them can sign on this phone (an account whose
     * stored signing key is missing, unreadable or not the key of its address is read-only).
     */
    suspend fun accountSigningCounts(): Pair<Int, Int> {
        val total = userRepository.getSoraAccountsCount()
        val readOnly = userRepository.unavailableWalletIds().size
        return total to (total - readOnly).coerceAtLeast(0)
    }

    fun subscribeVisibleCardsHubList(): Flow<Pair<SoraAccount, List<CardHub>>> {
        // The shared current-account stream emits identity changes only. Resolve display
        // metadata separately so renaming the selected wallet refreshes its existing header.
        return userRepository.flowCurSoraAccount()
            .combine(userRepository.flowSoraAccountsList()) { selected, accounts ->
                accounts.firstOrNull { it.substrateAddress == selected.substrateAddress }
                    ?.let { selected.copy(accountName = it.accountName) }
                    ?: selected
            }
            .distinctUntilChanged()
            .flatMapLatest { account ->
                walletRepository.subscribeVisibleGlobalCardsHubList()
                    .combine(walletRepository.subscribeCardsHubList(account.substrateAddress)) { global, local ->
                        global + local
                    }
                    .map {
                        account to it
                    }
            }
    }

    suspend fun updateCardVisibilityOnCardHub(cardId: String, visible: Boolean) {
        when (cardId) {
            CardHubType.REFERRAL_SYSTEM.hubName -> {
                walletRepository.updateCardVisibilityOnGlobalCardsHub(
                    cardId = cardId,
                    visible = visible,
                )
            }
            CardHubType.POOLS.hubName -> {
                walletRepository.updateCardVisibilityOnCardsHub(
                    cardId = cardId,
                    visible = visible,
                )
            }
        }
    }

    suspend fun updateCardCollapsedStateOnCardHub(cardId: String, collapsed: Boolean) {
        walletRepository.updateCardCollapsedState(cardId, collapsed)
    }
}
