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
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditCardsHubViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val cardsHubInteractor: CardsHubInteractorImpl
) : BaseViewModel() {

    private val mutableState = MutableSharedFlow<List<Pair<CardHubType, Boolean>>>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun startScreen() = theOnlyRoute

    val state: StateFlow<EditCardsHubScreenState> = mutableState.map { cardsHub ->
        EditCardsHubScreenState(
            enabledCardsHeader = Text.StringRes(id = R.string.common_enabled),
            enabledCards = cardsHub.filter {
                it.second
            }.map { (cardType, _) ->
                cardType.mapToState(isVisible = true)
            },
            disabledCardsHeader = Text.StringRes(id = R.string.common_disabled),
            disabledCards = cardsHub.filter {
                !it.second
            }.map { (cardType, _) ->
                cardType.mapToState(isVisible = false)
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = EditCardsHubScreenState(
            enabledCardsHeader = Text.SimpleText(""),
            enabledCards = emptyList(),
            disabledCardsHeader = Text.SimpleText(""),
            disabledCards = emptyList()
        )
    )

    private fun CardHubType.mapToState(isVisible: Boolean) = when (this) {
        CardHubType.ASSETS ->
            Text.StringRes(R.string.liquid_assets) to
                HubCardState(HubCardVisibility.VISIBLE_AND_DISABLED)
        CardHubType.POOLS ->
            Text.StringRes(R.string.pooled_assets) to
                if (isVisible) HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED) else
                    HubCardState(HubCardVisibility.NOT_VISIBLE_ENABLED)
        CardHubType.GET_SORA_CARD ->
            Text.StringRes(R.string.more_menu_sora_card_title) to
                if (isVisible) HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED) else
                    HubCardState(HubCardVisibility.NOT_VISIBLE_ENABLED)
        CardHubType.BUY_XOR_TOKEN ->
            Text.StringRes(R.string.common_buy_xor) to
                if (isVisible) HubCardState(HubCardVisibility.VISIBLE_AND_ENABLED) else
                    HubCardState(HubCardVisibility.NOT_VISIBLE_ENABLED)
    }

    init {
        SoramitsuToolbarState(
            basic = BasicToolbarState(
                title = R.string.edit_view,
                navIcon = R.drawable.ic_cross_24
            ),
            type = SoramitsuToolbarType.SmallCentered()
        ).apply { _toolbarState.value = this }

        cardsHubInteractor.subscribeVisibleCardsHubList().onEach { (_, cardsHubListState) ->
            mutableState.tryEmit(
                value = cardsHubListState.map {
                    it.cardType to it.visibility
                }
            )
        }.launchIn(viewModelScope)
    }

    override fun onNavIcon() {
        super.onNavIcon()
        walletRouter.returnToHubFragment()
    }

    fun onEnabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            mutableState.replayCache.firstOrNull()?.filter {
                it.second
            }?.getOrNull(position)?.let { (cardHub, isSelected) ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = !isSelected
                )
            }
        }

    fun onDisabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            mutableState.replayCache.firstOrNull()?.filter {
                !it.second
            }?.getOrNull(position)?.let { (cardHub, isSelected) ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = !isSelected
                )
            }
        }

    private companion object {
        const val ANIMATION_DURATION_DELAY = 750L
    }
}
