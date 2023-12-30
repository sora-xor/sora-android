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
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BuyXorState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.SoraCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_api.util.createSoraCardContract
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.oauth.base.sdk.contract.OutwardsScreen
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CardsHubViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val cardsHubInteractorImpl: CardsHubInteractorImpl,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter,
    private val mainRouter: MainRouter,
    private val assetsRouter: AssetsRouter,
    private val referralRouter: ReferralRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val connectionManager: ConnectionManager,
    private val soraCardInteractor: SoraCardInteractor,
    private val coroutineManager: CoroutineManager,
) : BaseViewModel() {

    private val _state = MutableStateFlow(
        CardsState(
            loading = true,
            cards = emptyList(),
            curAccount = "",
            accountAddress = "",
        )
    )
    val state = _state.asStateFlow()

    private val _launchSoraCardSignIn = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardSignIn: LiveData<SoraCardContractData> = _launchSoraCardSignIn

    private var currentSoraCardContractData: SoraCardContractData? = null

    init {
        viewModelScope.launch {
            withContext(coroutineManager.io) {
                soraCardInteractor.checkSoraCardPending()
            }
        }

        viewModelScope.launch {
            cardsHubInteractorImpl
                .subscribeVisibleCardsHubList()
                .catch { onError(it) }
                .distinctUntilChanged()
                .withIndex()
                .flatMapLatest { indexed ->
                    val data = indexed.value
                    _state.value = _state.value.copy(
                        accountAddress = data.first.substrateAddress,
                        curAccount = data.first.accountTitle(),
                        loading = false,
                    )
                    val flows = data.second.filter { it.visibility }.map { cardHub ->
                        when (cardHub.cardType) {
                            CardHubType.ASSETS -> {
                                assetsInteractor.subscribeAssetsFavoriteOfAccount(data.first)
                                    .onStart {
                                        if (indexed.index == 0) this.emit(emptyList())
                                    }
                                    .map {
                                        cardHub to it
                                    }
                            }

                            CardHubType.POOLS -> {
                                val poolsFlow = poolsInteractor.subscribePoolsCacheOfAccount(data.first)
                                    .onStart {
                                        if (indexed.index == 0) this.emit(emptyList())
                                    }
                                val demeterFlow = demeterFarmingInteractor.subscribeFarms(data.first.substrateAddress)
                                    .onStart {
                                        this.emit("")
                                    }
                                poolsFlow.combine(demeterFlow) { f1, _ -> f1 }
                                    .map { list ->
                                        val farms = demeterFarmingInteractor.getFarmedPools() ?: emptyList()
                                        cardHub to ((list.filter { it.user.favorite }) to farms)
                                    }
                            }

                            CardHubType.GET_SORA_CARD -> {
                                soraCardInteractor.subscribeSoraCardStatus()
                                    .map { status ->
                                        val mapped = mapKycStatus(status)
                                        cardHub to SoraCardState(
                                            kycStatus = mapped.first,
                                            loading = false,
                                            success = mapped.second,
                                            ibanBalance = if (mapped.second) soraCardInteractor.fetchIbanBalance()
                                                .getOrNull() else null,
                                            needUpdate = soraCardInteractor.needInstallUpdate(),
                                        )
                                    }
                                    .onStart {
                                        if (indexed.index == 0) this.emit(
                                            cardHub to SoraCardState(
                                                success = false,
                                                kycStatus = null,
                                                loading = true,
                                                ibanBalance = null,
                                                needUpdate = false,
                                            )
                                        )
                                    }
                            }

                            CardHubType.REFERRAL_SYSTEM -> {
                                flowOf(
                                    cardHub to ReferralState
                                )
                            }

                            CardHubType.BACKUP -> {
                                flowOf(cardHub to BackupWalletState)
                            }

                            CardHubType.BUY_XOR_TOKEN -> {
                                flowOf(
                                    cardHub to BuyXorState
                                )
                            }
                        }
                    }
                    combine(flows) { it.toList() }
                }
                .distinctUntilChanged()
                .collectLatest { cards ->
                    val usableCards = cards.filterNot {
                        (it.first.cardType != CardHubType.ASSETS) &&
                            ((it.second.safeCast<List<*>>()?.size == 0) || (it.second.safeCast<Pair<*, *>>()?.first?.safeCast<List<*>>())?.size == 0)
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        cards = mapCardsState(usableCards)
                    )
                }
        }

        soraCardInteractor.subscribeToSoraCardAvailabilityFlow().onEach {
            currentSoraCardContractData = createSoraCardContract(
                userAvailableXorAmount = it.xorBalance.toDouble(),
                isEnoughXorAvailable = it.enoughXor
            )
        }.launchIn(viewModelScope)
    }

    private fun mapKycStatus(kycStatus: SoraCardCommonVerification): Pair<String?, Boolean> {
        return when (kycStatus) {
            SoraCardCommonVerification.Failed -> {
                resourceManager.getString(R.string.sora_card_verification_failed) to false
            }

            SoraCardCommonVerification.Rejected -> {
                resourceManager.getString(R.string.sora_card_verification_rejected) to false
            }

            SoraCardCommonVerification.Pending -> {
                resourceManager.getString(R.string.sora_card_verification_in_progress) to false
            }

            SoraCardCommonVerification.Successful -> {
                resourceManager.getString(R.string.sora_card_verification_successful) to true
            }

            else -> {
                null to false
            }
        }
    }

    fun handleSoraCardResult(soraCardResult: SoraCardResult) {
        when (soraCardResult) {
            is SoraCardResult.NavigateTo -> {
                when (soraCardResult.screen) {
                    OutwardsScreen.DEPOSIT -> router.openQrCodeFlow()
                    OutwardsScreen.SWAP -> polkaswapRouter.showSwap(tokenToId = SubstrateOptionsProvider.feeAssetId)
                    OutwardsScreen.BUY -> assetsRouter.showBuyCrypto()
                }
            }

            is SoraCardResult.Success -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Failure -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }

            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Logout -> {
                soraCardInteractor.setLogout()
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

    fun onEditViewClick() {
        router.openEditCardsHub()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCardsState(data: List<Pair<CardHub, Any>>): List<CardState> {
        return data.map {
            when (it.first.cardType) {
                CardHubType.ASSETS -> mapAssetsCard(it.first.collapsed, it.second as List<Asset>)
                CardHubType.POOLS -> mapPoolsCard(
                    it.first.collapsed,
                    it.second as Pair<List<CommonUserPoolData>, List<DemeterFarmingPool>>
                )

                CardHubType.BACKUP -> (it.second as BackupWalletState)
                CardHubType.GET_SORA_CARD -> (it.second as SoraCardState)
                CardHubType.BUY_XOR_TOKEN -> (it.second as BuyXorState)
                CardHubType.REFERRAL_SYSTEM -> (it.second as ReferralState)
            }
        }
    }

    private fun mapAssetsCard(collapsed: Boolean, assets: List<Asset>): CardState {
        return TitledAmountCardState(
            amount = formatFiatAmount(assets.fiatSum(), assets.fiatSymbol(), numbersFormatter),
            title = CardHubType.ASSETS.userName,
            state = FavoriteAssetsCardState(mapAssetsToCardState(assets, numbersFormatter)),
            collapsedState = collapsed,
            onCollapseClick = { collapseCardToggle(CardHubType.ASSETS.hubName, !collapsed) },
            loading = false,
        )
    }

    private fun mapPoolsCard(collapsed: Boolean, pools: Pair<List<CommonUserPoolData>, List<DemeterFarmingPool>>): CardState {
        val rewardTokensList = pools.first.map { pool ->
            pools.second.filter {
                it.tokenBase.id == pool.basic.baseToken.id && it.tokenTarget.id == pool.basic.targetToken.id
            }.map {
                it.tokenReward.iconUri()
            }
        }

        val data = mapPoolsData(pools.first, numbersFormatter, rewardTokensList)
        return TitledAmountCardState(
            amount = formatFiatAmount(data.second, pools.first.fiatSymbol, numbersFormatter),
            title = CardHubType.POOLS.userName,
            state = FavoritePoolsCardState(
                state = data.first
            ),
            loading = false,
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

    fun onOpenFullCard(state: AssetCardState) {
        when (state) {
            is FavoriteAssetsCardState -> {
                router.showAssetSettings()
            }

            is FavoritePoolsCardState -> {
                polkaswapRouter.showPoolSettings()
            }
        }
    }

    fun onCardStateClicked() {
        _state.value.cards.filterIsInstance<SoraCardState>().firstOrNull()?.let { card ->
            if (card.kycStatus == null) {
                if (!connectionManager.isConnected) return
                mainRouter.showGetSoraCard()
            } else if (card.success) {
                mainRouter.showSoraCardDetails()
            } else {
                currentSoraCardContractData?.let { contractData ->
                    _launchSoraCardSignIn.value = contractData
                }
            }
        }
    }

    fun onRemoveSoraCard() {
        viewModelScope.launch {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.GET_SORA_CARD.hubName,
                visible = false,
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

    fun onRemoveReferralCard() {
        viewModelScope.launch {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.REFERRAL_SYSTEM.hubName,
                visible = false,
            )
        }
    }

    fun onStartReferral() {
        referralRouter.showReferrals()
    }

    fun onBackupBannerClick() {
        mainRouter.showAccountDetails(_state.value.accountAddress)
    }

    fun onBuyCrypto() {
        if (!connectionManager.isConnected) return
        assetsRouter.showBuyCrypto()
    }
}
