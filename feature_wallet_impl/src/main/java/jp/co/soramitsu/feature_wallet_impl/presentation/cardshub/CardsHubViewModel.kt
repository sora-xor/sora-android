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

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoice
import jp.co.soramitsu.common.nexus.WalletNetworkChoices
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPreparedSend
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendRequest
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendResult
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusTransactionCoordinator
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

data class NexusSendUiState(
    val visible: Boolean = false,
    val network: NexusPortfolioBalance? = null,
    val recipient: String = "",
    val amount: String = "",
    val preparing: Boolean = false,
    val submitting: Boolean = false,
    val prepared: NexusPreparedSend? = null,
    val result: NexusSendResult? = null,
    val errorCode: String? = null,
    val submissionNeedsCheck: Boolean = false,
    val scannerPermissionDenied: Boolean = false,
)

internal fun NexusSendUiState.editableDraft(): NexusSendUiState? =
    if (!visible || preparing || submitting || result != null || submissionNeedsCheck) null
    else copy(prepared = null, errorCode = null)

internal fun NexusSendUiState.canConfirmSend(): Boolean =
    visible && !preparing && !submitting && result == null && !submissionNeedsCheck && prepared != null

internal fun NexusSendUiState.afterSubmissionFailure(error: Throwable): NexusSendUiState = copy(
    submitting = false, prepared = null, result = null,
    errorCode = error.message ?: "NEXUS_SUBMISSION_FAILED",
    submissionNeedsCheck = error !is jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusNotSubmittedException,
)

internal data class NexusRecipientScan(val generation: Long, val walletId: String, val networkId: jp.co.soramitsu.common.nexus.WalletNetworkId, val address: String)

internal fun NexusSendUiState.acceptsScan(scan: NexusRecipientScan, generation: Long): Boolean =
    visible && !preparing && !submitting && prepared == null && result == null && !submissionNeedsCheck &&
        scan.generation == generation && network?.let {
            it.walletId == scan.walletId && it.networkId == scan.networkId && it.address == scan.address
        } == true

data class Sora2PortfolioBalance(
    val address: String,
    val xorQuantity: String?,
    /** XOR's icon and name, for other networks' XOR rows in the same asset list. */
    val xorIcon: String? = null,
    val xorName: String? = null,
)

@HiltViewModel
class CardsHubViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val cardsHubInteractorImpl: CardsHubInteractorImpl,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val router: WalletRouter,
    private val mainRouter: MainRouter,
    private val referralRouter: ReferralRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val coroutineManager: CoroutineManager,
    private val nexusPortfolioRepository: NexusPortfolioRepository,
    private val nexusTransactionCoordinator: NexusTransactionCoordinator,
) : BaseViewModel() {

    private val _state = MutableStateFlow(CardsState(accountAddress = ""))
    val state = _state.asStateFlow()

    private val _nexusPortfolio = MutableStateFlow<List<NexusPortfolioBalance>>(emptyList())
    val nexusPortfolio = _nexusPortfolio.asStateFlow()

    private val _sora2Portfolio = MutableStateFlow<Sora2PortfolioBalance?>(null)
    val sora2Portfolio = _sora2Portfolio.asStateFlow()

    private val _nexusSend = MutableStateFlow(NexusSendUiState())
    val nexusSend = _nexusSend.asStateFlow()

    private var nexusSendOperation: Job? = null
    private var nexusSendGeneration = 0L
    private var recipientScan: NexusRecipientScan? = null
    private var selectedPortfolioWalletId: String? = null

    init {
        viewModelScope.launch {
            // For the upgrade-complete sheet: it only ticks accounts that can really sign.
            runCatching {
                val counts = cardsHubInteractorImpl.accountSigningCounts()
                _state.value = _state.value.copy(
                    accountCount = counts.first,
                    signingAccountCount = counts.second,
                )
            }
        }
        viewModelScope.launch {
            assetsInteractor.flowCurSoraAccount()
                .distinctUntilChanged()
                .flatMapLatest { account ->
                    selectedPortfolioWalletId = account.substrateAddress
                    _sora2Portfolio.value = Sora2PortfolioBalance(
                        address = account.substrateAddress,
                        xorQuantity = null,
                    )
                    // A prepared Nexus confirmation belongs to the wallet
                    // that reviewed it. Account switching dismisses that UI;
                    // the durable transaction coordinator still owns any
                    // already-staged status reconciliation.
                    resetNexusSend()
                    assetsInteractor.subscribeAssetOfAccount(
                        account,
                        SubstrateOptionsProvider.feeAssetId,
                    ).map { asset ->
                        check(
                            asset == null ||
                                asset.token.id == SubstrateOptionsProvider.feeAssetId
                        ) { "SORA2_XOR_ASSET_IDENTITY_MISMATCH" }
                        Sora2PortfolioBalance(
                            address = account.substrateAddress,
                            xorQuantity = asset?.balance?.transferable
                                ?.stripTrailingZeros()
                                ?.toPlainString(),
                            xorIcon = asset?.token?.iconUri(),
                            xorName = asset?.token?.name,
                        )
                    }.onStart {
                        // Replace the previous wallet row before waiting for
                        // this explicitly bound account's asset stream.
                        emit(
                            Sora2PortfolioBalance(
                                address = account.substrateAddress,
                                xorQuantity = null,
                            )
                        )
                    }.catch { error ->
                        if (error is CancellationException) throw error
                        emit(
                            Sora2PortfolioBalance(
                                address = account.substrateAddress,
                                xorQuantity = null,
                            )
                        )
                    }
                }
                .collectLatest { _sora2Portfolio.value = it }
        }
        viewModelScope.launch {
            nexusPortfolioRepository.observeCurrentWallet()
                .catch { emit(emptyList()) }
                .collectLatest { balances ->
                    _nexusPortfolio.value = balances
                    val boundNetwork = _nexusSend.value.network
                    if (
                        boundNetwork != null &&
                        currentNexusBalance(boundNetwork)?.sendAvailable != true
                    ) {
                        // This also closes a Taira dialog as soon as Test
                        // Networks is disabled. A removed or disabled row can
                        // never remain an actionable confirmation surface.
                        resetNexusSend()
                    }
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
                    _state.value = _state.value.copy(accountAddress = data.first.substrateAddress)
                    // The Portfolio view draws the account's assets: there is no Assets card.
                    val flows = data.second.filter { it.visibility }.mapNotNull { cardHub ->
                        when (cardHub.cardType) {
                            CardHubType.ASSETS -> null

                            CardHubType.POOLS -> {
                                val poolsFlow =
                                    poolsInteractor.subscribePoolsCacheOfAccount(data.first)
                                        .onStart {
                                            if (indexed.index == 0) this.emit(emptyList())
                                        }
                                val demeterFlow =
                                    demeterFarmingInteractor.subscribeFarms(data.first.substrateAddress)
                                        .onStart {
                                            this.emit("")
                                        }
                                poolsFlow.combine(demeterFlow) { f1, _ -> f1 }
                                    .map { list ->
                                        val farms =
                                            demeterFarmingInteractor.getFarmedPools() ?: emptyList()
                                        cardHub to ((list.filter { it.user.favorite }) to farms)
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
                        }
                    }
                    // combine() of no flows never emits: with every card hidden, there are none.
                    if (flows.isEmpty()) flowOf(emptyList<Pair<CardHub, Any>>()) else combine(flows) { it.toList() }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    // A failed pool read must not crash the host or leave another wallet's
                    // holdings visible. Network rows independently show availability.
                    jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
                    _state.value = _state.value.copy(cards = emptyList())
                }
                .distinctUntilChanged()
                .collectLatest { cards ->
                    // A pools card without favorite pools is not shown.
                    val usableCards = cards.filterNot {
                        (it.second.safeCast<List<*>>()?.size == 0) ||
                            (it.second.safeCast<Pair<*, *>>()?.first?.safeCast<List<*>>())?.size == 0
                    }
                    _state.value = _state.value.copy(cards = mapCardsState(usableCards))
                }
        }
    }

    fun refreshNexusPortfolio() = nexusPortfolioRepository.refresh()

    fun loadMoreNexusHistory(balance: NexusPortfolioBalance) {
        val current = currentNexusBalance(balance) ?: return
        if (current.historyHasMore) {
            nexusPortfolioRepository.loadMoreHistory(current.walletId, current.networkId)
        }
    }

    fun openNexusSend(balance: NexusPortfolioBalance) {
        if (_nexusSend.value.preparing || _nexusSend.value.submitting) return
        val currentBalance = currentNexusBalance(balance)
            ?.takeIf { it.sendAvailable }
            ?: return
        invalidateNexusSendOperation()
        _nexusSend.value = NexusSendUiState(visible = true, network = currentBalance)
    }

    fun closeNexusSend() {
        if (_nexusSend.value.preparing || _nexusSend.value.submitting) return
        invalidateNexusSendOperation()
        _nexusSend.value = NexusSendUiState()
    }

    fun editNexusSend() {
        val current = _nexusSend.value
        val draft = current.editableDraft() ?: return
        invalidateNexusSendOperation()
        _nexusSend.value = draft
    }

    fun beginNexusRecipientScan(): Boolean {
        if (recipientScan != null) return false
        val state = _nexusSend.value
        val network = state.network?.let(::currentNexusBalance)?.takeIf { it.sendAvailable } ?: return false
        val scan = NexusRecipientScan(nexusSendGeneration, network.walletId, network.networkId, network.address)
        if (!state.acceptsScan(scan, nexusSendGeneration)) return false
        recipientScan = scan
        return true
    }

    fun finishNexusRecipientScan(value: String?, cameraPermissionDenied: Boolean = false) {
        val scan = recipientScan ?: return
        recipientScan = null
        val current = _nexusSend.value
        if (!current.acceptsScan(scan, nexusSendGeneration) ||
            current.network?.let(::currentNexusBalance)?.sendAvailable != true) return
        // Scanning only fills this immutable draft. Canonical network/address validation remains in prepare.
        invalidateNexusSendOperation()
        _nexusSend.value = current.afterRecipientScan(value, cameraPermissionDenied)
    }

    fun setNexusRecipient(value: String) {
        val current = _nexusSend.value
        if (!current.visible || current.submitting || current.result != null || current.prepared != null || current.submissionNeedsCheck) return
        invalidateNexusSendOperation()
        _nexusSend.value = current.copy(
            recipient = value.trim(),
            preparing = false,
            prepared = null,
            result = null,
            errorCode = null,
        )
    }

    fun setNexusAmount(value: String) {
        val current = _nexusSend.value
        if (!current.visible || current.submitting || current.result != null || current.prepared != null || current.submissionNeedsCheck) return
        if (value.isEmpty() || NexusQuantityContract.isInputQuantity(value)) {
            invalidateNexusSendOperation()
            _nexusSend.value = current.copy(
                amount = value,
                preparing = false,
                prepared = null,
                result = null,
                errorCode = null,
            )
        }
    }

    fun prepareNexusSend() {
        val current = _nexusSend.value
        if (
            !current.visible ||
            current.preparing ||
            current.submitting ||
            current.submissionNeedsCheck || current.result != null ||
            current.prepared != null
        ) return
        val network = current.network
            ?.let(::currentNexusBalance)
            ?.takeIf { it.sendAvailable }
            ?: run {
                resetNexusSend()
                return
            }
        // The immutable row owns the request. CardsState is fed by a separate
        // stream and can briefly still describe the previous selected wallet.
        val walletId = network.walletId
        val generation = beginNexusSendOperation()
        _nexusSend.value = current.copy(
            network = network,
            preparing = true,
            errorCode = null,
        )
        nexusSendOperation = viewModelScope.launch {
            try {
                val prepared = nexusTransactionCoordinator.prepare(
                    NexusSendRequest(
                        walletId = walletId,
                        networkId = network.networkId,
                        recipient = current.recipient,
                        amount = current.amount,
                    )
                )
                if (!nexusSendOperationIsCurrent(generation, network)) return@launch
                _nexusSend.value = _nexusSend.value.copy(
                    preparing = false,
                    prepared = prepared,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!nexusSendOperationIsCurrent(generation, network)) return@launch
                _nexusSend.value = _nexusSend.value.copy(
                    preparing = false,
                    prepared = null,
                    errorCode = error.message ?: "NEXUS_QUOTE_FAILED",
                )
            } finally {
                if (generation == nexusSendGeneration) nexusSendOperation = null
            }
        }
    }

    fun confirmNexusSend() {
        val current = _nexusSend.value
        if (!current.canConfirmSend()) return
        val prepared = current.prepared ?: return
        val network = current.network
            ?.let(::currentNexusBalance)
            ?.takeIf { it.sendAvailable }
            ?: run {
                resetNexusSend()
                return
            }
        if (
            prepared.request.walletId != network.walletId ||
            prepared.request.networkId != network.networkId
        ) {
            resetNexusSend()
            return
        }
        val generation = beginNexusSendOperation()
        _nexusSend.value = current.copy(network = network, submitting = true, errorCode = null)
        nexusSendOperation = viewModelScope.launch {
            try {
                val result = nexusTransactionCoordinator.submitAndTrack(prepared)
                if (!nexusSendOperationIsCurrent(generation, network)) return@launch
                _nexusSend.value = _nexusSend.value.copy(
                    submitting = false,
                    result = result,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!nexusSendOperationIsCurrent(generation, network)) return@launch
                _nexusSend.value = _nexusSend.value.afterSubmissionFailure(error)
            } finally {
                if (generation == nexusSendGeneration) nexusSendOperation = null
            }
        }
    }

    fun isCurrentNexusBalance(balance: NexusPortfolioBalance): Boolean =
        currentNexusBalance(balance) != null

    private fun isCurrentSora2Wallet(walletId: String): Boolean =
        walletId == selectedPortfolioWalletId &&
            walletId == _sora2Portfolio.value?.address

    /**
     * What Send or Receive can offer for the selected wallet, main network first. Only that
     * wallet's rows count: account selection is a privacy boundary.
     */
    fun networkChoices(action: WalletNetworkAction): List<WalletNetworkChoice> {
        val sora2 = _sora2Portfolio.value?.takeIf { isCurrentSora2Wallet(it.address) } ?: return emptyList()
        val nexus = _nexusPortfolio.value.filter { it.walletId == sora2.address }
        val addresses = mapOf(WalletNetworkId.SORA2 to sora2.address) +
            nexus.associate { it.networkId to it.address }
        val balances = buildMap {
            sora2.xorQuantity?.let { put(WalletNetworkId.SORA2, it) }
            nexus.forEach { balance -> balance.xorQuantity?.let { put(balance.networkId, it) } }
        }
        val sendable = nexus.filter { it.sendAvailable }.mapTo(mutableSetOf(WalletNetworkId.SORA2)) { it.networkId }
        return WalletNetworkChoices.make(action, addresses, balances, canSend = { it in sendable })
    }

    private fun currentNexusBalance(
        expected: NexusPortfolioBalance,
    ): NexusPortfolioBalance? {
        if (
            expected.walletId != selectedPortfolioWalletId ||
            expected.walletId != _sora2Portfolio.value?.address
        ) return null
        return _nexusPortfolio.value.firstOrNull {
            it.walletId == expected.walletId &&
                it.networkId == expected.networkId &&
                it.address == expected.address
        }
    }

    private fun nexusSendOperationIsCurrent(
        generation: Long,
        network: NexusPortfolioBalance,
    ): Boolean =
        generation == nexusSendGeneration &&
            _nexusSend.value.visible &&
            currentNexusBalance(network)?.sendAvailable == true

    private fun beginNexusSendOperation(): Long {
        invalidateNexusSendOperation()
        return nexusSendGeneration
    }

    private fun invalidateNexusSendOperation() {
        nexusSendGeneration += 1
        recipientScan = null
        nexusSendOperation?.cancel()
        nexusSendOperation = null
    }

    private fun resetNexusSend() {
        invalidateNexusSendOperation()
        _nexusSend.value = NexusSendUiState()
    }

    fun openQrCodeFlow() {
        router.openQrCodeFlow()
    }

    fun onPoolClick(poolId: StringPair) {
        polkaswapRouter.showPoolDetails(poolId)
    }

    fun onEditViewClick() {
        router.openEditCardsHub()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCardsState(data: List<Pair<CardHub, Any>>): List<CardState> {
        return data.mapNotNull {
            when (it.first.cardType) {
                CardHubType.ASSETS -> null
                CardHubType.POOLS -> mapPoolsCard(
                    it.first.collapsed,
                    it.second as Pair<List<CommonUserPoolData>, List<DemeterFarmingPool>>
                )

                CardHubType.BACKUP -> (it.second as BackupWalletState)
                CardHubType.REFERRAL_SYSTEM -> (it.second as ReferralState)
            }
        }
    }

    private fun mapPoolsCard(
        collapsed: Boolean,
        pools: Pair<List<CommonUserPoolData>, List<DemeterFarmingPool>>
    ): CardState {
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
            is FavoritePoolsCardState -> polkaswapRouter.showPoolSettings()
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
}

internal fun NexusSendUiState.afterRecipientScan(value: String?, cameraPermissionDenied: Boolean = false): NexusSendUiState {
    if (!visible || preparing || submitting || prepared != null || result != null || submissionNeedsCheck) return this
    return when {
        value != null -> copy(recipient = value, scannerPermissionDenied = false, errorCode = null)
        cameraPermissionDenied -> copy(scannerPermissionDenied = true)
        else -> this
    }
}
