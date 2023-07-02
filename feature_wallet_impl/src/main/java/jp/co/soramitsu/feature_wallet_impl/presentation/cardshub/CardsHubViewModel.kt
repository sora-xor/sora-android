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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.fiatSum
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.BuyXorState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_sora_card_api.util.createSoraCardContract
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class CardsHubViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val cardsHubInteractorImpl: CardsHubInteractorImpl,
    private val numbersFormatter: NumbersFormatter,
    private val progress: WithProgress,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter,
    private val mainRouter: MainRouter,
    private val assetsRouter: AssetsRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val connectionManager: ConnectionManager,
    coroutineManager: CoroutineManager,
) : BaseViewModel(), WithProgress by progress {

    var state by mutableStateOf(
        CardsState(
            loading = true,
            cards = emptyList(),
            curAccount = "",
        )
    )
        private set

    private val _launchSoraCardSignIn = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardSignIn: LiveData<SoraCardContractData> = _launchSoraCardSignIn

    init {
        walletInteractor.pollSoraCardStatusIfPending()
            .flowOn(coroutineManager.io)
            .launchIn(viewModelScope)

        viewModelScope.launch {
            cardsHubInteractorImpl
                .subscribeVisibleCardsHubList()
                .catch { onError(it) }
                .distinctUntilChanged()
                .flatMapLatest { data ->
                    state =
                        state.copy(curAccount = data.first.accountTitle())
                    val flows = data.second.map { cardHub ->
                        when (cardHub.cardType) {
                            CardHubType.ASSETS -> {
                                assetsInteractor.subscribeAssetsFavoriteOfAccount(data.first)
                                    .map {
                                        cardHub to it
                                    }
                            }

                            CardHubType.POOLS -> {
                                poolsInteractor.subscribePoolsCacheOfAccount(data.first)
                                    .map { list ->
                                        cardHub to list.filter { it.favorite }
                                    }
                            }

                            CardHubType.GET_SORA_CARD -> {
                                cardsHubInteractorImpl.subscribeSoraCardInfo()
                                    .map { cardInfo ->
                                        cardHub to listOf(
                                            SoraCardState(
                                                visible = cardHub.visibility,
                                                kycStatus = cardInfo?.kycStatus?.let(::mapKycStatus),
                                                cardInfo = cardInfo
                                            )
                                        )
                                    }
                            }

                            CardHubType.BUY_XOR_TOKEN -> {
                                flow {
                                    emit(listOf(BuyXorState(visible = cardHub.visibility)))
                                }.map {
                                    cardHub to it
                                }
                            }
                        }
                    }
                    combine(flows) { it.toList() }
                }
                .distinctUntilChanged()
                .collectLatest { cards ->
                    val usableCards = cards.filter {
                        it.first.cardType == CardHubType.ASSETS || it.second.isNotEmpty()
                    }
                    state = state.copy(
                        loading = false,
                        cards = mapCardsState(usableCards)
                    )
                }
        }
    }

    private fun mapKycStatus(kycStatus: String): String? {
        return when (runCatching { SoraCardCommonVerification.valueOf(kycStatus) }.getOrNull()) {
            SoraCardCommonVerification.Pending -> {
                resourceManager.getString(R.string.sora_card_verification_in_progress)
            }

            SoraCardCommonVerification.Successful -> {
                resourceManager.getString(R.string.sora_card_verification_successful)
            }

            SoraCardCommonVerification.Rejected -> {
                resourceManager.getString(R.string.sora_card_verification_rejected)
            }

            SoraCardCommonVerification.Failed -> {
                resourceManager.getString(R.string.sora_card_verification_failed)
            }

//            SoraCardCommonVerification.NoFreeAttempt -> {
//                resourceManager.getString(R.string.sora_card_no_more_free_tries)
//            }

            else -> {
                null
            }
        }
    }

    fun openQrCodeFlow() {
        router.openQrCodeFlow()
    }

    fun onAssetClick(tokenId: String) {
        assetsRouter.showAssetDetails(tokenId)
    }

    fun onPoolClick(poolId: StringPair) {
        polkaswapRouter.showPoolDetails(poolId)
    }

    fun onAccountClick() {
        mainRouter.showAccountList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCardsState(data: List<Pair<CardHub, Any>>): List<CardState> {
        return data.map {
            when (it.first.cardType) {
                CardHubType.ASSETS -> mapAssetsCard(it.first.collapsed, it.second as List<Asset>)
                CardHubType.POOLS -> mapPoolsCard(it.first.collapsed, it.second as List<PoolData>)
                CardHubType.GET_SORA_CARD -> (it.second as List<SoraCardState>).first()
                CardHubType.BUY_XOR_TOKEN -> (it.second as List<BuyXorState>).first()
            }
        }
    }

    private fun mapAssetsCard(collapsed: Boolean, assets: List<Asset>): CardState {
        return TitledAmountCardState(
            amount = formatFiatAmount(assets.fiatSum(), assets.fiatSymbol(), numbersFormatter),
            title = R.string.liquid_assets,
            state = FavoriteAssetsCardState(mapAssetsToCardState(assets, numbersFormatter)),
            collapsedState = collapsed,
            onCollapseClick = { collapseCardToggle(CardHubType.ASSETS.hubName, !collapsed) },
            onExpandClick = ::expandAssetsCard
        )
    }

    private fun mapPoolsCard(collapsed: Boolean, pools: List<PoolData>): CardState {
        val data = mapPoolsData(pools, numbersFormatter)
        return TitledAmountCardState(
            amount = formatFiatAmount(data.second, pools.fiatSymbol, numbersFormatter),
            title = R.string.pooled_assets,
            state = FavoritePoolsCardState(
                state = data.first
            ),
            onExpandClick = ::expandPoolsCard,
            onCollapseClick = { collapseCardToggle(CardHubType.POOLS.hubName, !collapsed) },
            collapsedState = collapsed
        )
    }

    private fun collapseCardToggle(cardString: String, collapsed: Boolean) {
        viewModelScope.launch {
            cardsHubInteractorImpl.updateCardCollapsedStateOnCardHub(
                cardString,
                collapsed
            )
        }
    }

    private fun expandAssetsCard() {
        router.showAssetSettings()
    }

    private fun expandPoolsCard() {
        polkaswapRouter.showPoolSettings()
    }

    fun onCardStateClicked() {
        state.cards.filterIsInstance<SoraCardState>().firstOrNull()?.let { card ->
            if (card.kycStatus == null) {
                if (!connectionManager.isConnected) return
                mainRouter.showGetSoraCard()
            } else {
                _launchSoraCardSignIn.value = createSoraCardContract()
            }
        }
    }

    fun logoutSoraCard() {
        viewModelScope.launch {
            walletInteractor.logoutSoraCard()
        }
    }

    fun updateSoraCardInfo(
        accessToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        viewModelScope.launch {
            walletInteractor.updateSoraCardInfo(
                accessToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }

    fun onRemoveSoraCard() {
        viewModelScope.launch {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.GET_SORA_CARD.hubName,
                visible = false
            )
        }
    }

    fun onRemoveBuyXorToken() {
        viewModelScope.launch {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.BUY_XOR_TOKEN.hubName,
                visible = false
            )
        }
    }

    fun onBuyCrypto() {
        if (!connectionManager.isConnected) return
        assetsRouter.showBuyCrypto()
    }
}
