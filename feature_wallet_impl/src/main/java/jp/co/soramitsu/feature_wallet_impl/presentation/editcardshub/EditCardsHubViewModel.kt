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

package jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditCardsHubViewModel @Inject constructor(
    private val cardsHubInteractor: CardsHubInteractorImpl
) : BaseViewModel() {

    val state = cardsHubInteractor.subscribeVisibleCardsHubList().map { (_, allCards) ->
        // The Portfolio view shows the assets: the home has no Assets card to show or hide.
        val cardsHubList = allCards.filter { it.cardType != CardHubType.ASSETS }
        EditCardsHubScreenState(
            enabledCards = cardsHubList.filter { it.visibility }.map {
                HubCardState(
                    it.cardType.hubName,
                    it.cardType.mapToState(true),
                    it.cardType.userName
                )
            },
            disabledCards = cardsHubList.filter { it.visibility.not() }
                .map {
                    HubCardState(
                        it.cardType.hubName,
                        it.cardType.mapToState(false),
                        it.cardType.userName,
                    )
                },
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            EditCardsHubScreenState(emptyList(), emptyList()),
        )

    private fun CardHubType.mapToState(isVisible: Boolean) = when (this) {
        CardHubType.ASSETS, CardHubType.BACKUP ->
            if (isVisible) HubCardVisibility.VISIBLE_AND_DISABLED else HubCardVisibility.NOT_VISIBLE_DISABLED
        CardHubType.POOLS -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
        CardHubType.REFERRAL_SYSTEM -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
    }

    init {
        _toolbarState.value = SoramitsuToolbarState(
            basic = BasicToolbarState(
                title = R.string.edit_view,
                navIcon = jp.co.soramitsu.ui_core.R.drawable.ic_cross
            ),
            type = SoramitsuToolbarType.SmallCentered(),
        )
    }

    fun onEnabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            state.value.enabledCards.getOrNull(position)?.let { cardHub ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = false,
                )
            }
        }

    fun onDisabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            state.value.disabledCards.getOrNull(position)?.let { cardHub ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = true,
                )
            }
        }

    private companion object {
        const val ANIMATION_DURATION_DELAY = 500L
    }
}
