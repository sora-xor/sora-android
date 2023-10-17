/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

    val state = cardsHubInteractor.subscribeVisibleCardsHubList().map { (_, cardsHubList) ->
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
        CardHubType.ASSETS -> HubCardVisibility.VISIBLE_AND_DISABLED
        CardHubType.POOLS -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
        CardHubType.GET_SORA_CARD -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
        CardHubType.BUY_XOR_TOKEN -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
        CardHubType.REFERRAL_SYSTEM -> if (isVisible) HubCardVisibility.VISIBLE_AND_ENABLED else HubCardVisibility.NOT_VISIBLE_ENABLED
        CardHubType.BACKUP -> if (isVisible) HubCardVisibility.VISIBLE_AND_DISABLED else HubCardVisibility.NOT_VISIBLE_DISABLED
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
