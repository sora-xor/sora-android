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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.fiatSum
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoice
import jp.co.soramitsu.common.nexus.WalletNetworkChoices
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.WalletIssue
import jp.co.soramitsu.common.presentation.compose.components.initNoToolbar
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.presentation.walletIssue
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.receive.ReceiveTokenByQrScreenState
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoriteAssetsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
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
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPreparedSend
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendRequest
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusSendResult
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusTransactionCoordinator
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
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
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * The network has not said how the transfer ended, or an earlier transfer is still being checked:
 * nothing more can be sent from this draft until Activity shows the outcome.
 */
internal fun NexusSendUiState.needsStatusCheck(): Boolean =
    submissionNeedsCheck || errorCode?.let { walletIssue(it) == WalletIssue.RECOVERY } == true

/**
 * Which step of Send is on screen: the form, its review, or what became of the transfer. An
 * earlier transfer that is still being checked keeps the form, with its notice and no Review.
 */
internal enum class NexusSendStep { FORM, REVIEW, OUTCOME }

internal fun NexusSendUiState.step(): NexusSendStep = when {
    result != null || submissionNeedsCheck -> NexusSendStep.OUTCOME
    prepared != null -> NexusSendStep.REVIEW
    else -> NexusSendStep.FORM
}

/** The receive page of one network's address, built off the main thread like SORA2's. */
data class NexusReceiveUiState(
    val walletId: String = "",
    val networkId: WalletNetworkId? = null,
    val address: String = "",
    val screen: ReceiveTokenByQrScreenState = ReceiveTokenByQrScreenState(
        screenStatus = ScreenStatus.LOADING,
        untransformedQrBitmap = null,
        untransformedUserName = null,
        untransformedAvatarDrawable = null,
        untransformedUserAddress = null,
    ),
) {
    fun isFor(balance: NexusPortfolioBalance): Boolean =
        walletId == balance.walletId && networkId == balance.networkId && address == balance.address
}

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
    private val assetsRouter: AssetsRouter,
    private val referralRouter: ReferralRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val coroutineManager: CoroutineManager,
    private val nexusPortfolioRepository: NexusPortfolioRepository,
    private val nexusTransactionCoordinator: NexusTransactionCoordinator,
    private val nexusPresentation: NexusAssetPresentation,
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

    private val _nexusPortfolio = MutableStateFlow<List<NexusPortfolioBalance>>(emptyList())
    val nexusPortfolio = _nexusPortfolio.asStateFlow()

    private val _sora2Portfolio = MutableStateFlow<Sora2PortfolioBalance?>(null)
    val sora2Portfolio = _sora2Portfolio.asStateFlow()

    private val _nexusSend = MutableStateFlow(NexusSendUiState())
    val nexusSend = _nexusSend.asStateFlow()

    private val _nexusReceive = MutableStateFlow(NexusReceiveUiState())
    val nexusReceive = _nexusReceive.asStateFlow()
    private var nexusReceiveLoad: Job? = null

    private val _nexusShare = SingleLiveEvent<Pair<Uri, String>>()

    /** The QR code file and message to share, as SORA2's Receive shares them. */
    val nexusShare: LiveData<Pair<Uri, String>> = _nexusShare

    private var nexusSendOperation: Job? = null
    private var nexusSendGeneration = 0L
    private var recipientScan: NexusRecipientScan? = null
    private var selectedPortfolioWalletId: String? = null

    init {
        viewModelScope.launch {
            // Send's title follows its step: the form, its review, then the outcome page.
            _nexusSend.collect { if (currentDestination == NexusRoutes.SEND) updateToolbar() }
        }
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
                    // The previous wallet's receive page (QR code, address) is not kept.
                    nexusReceiveLoad?.cancel()
                    _nexusReceive.value = NexusReceiveUiState()
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
                    _state.value = _state.value.copy(
                        accountAddress = data.first.substrateAddress,
                        curAccount = data.first.accountTitle(),
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
                                    .transformLatest<List<CommonUserPoolData>, Pair<CardHub, Any>> { list ->
                                        val favorites = list.filter { it.user.favorite }
                                        // Farm rewards are read from the chain. The cached pools
                                        // show at once and get their reward icons when the node
                                        // answers, so a slow or unreachable node cannot hold back
                                        // every card on the home.
                                        emit(cardHub to (favorites to emptyList<DemeterFarmingPool>()))
                                        val farms =
                                            demeterFarmingInteractor.getFarmedPools() ?: emptyList()
                                        emit(cardHub to (favorites to farms))
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
                    combine(flows) { it.toList() }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    // A failed balance/pool read must not crash the host or leave another
                    // wallet's holdings visible. Network rows independently show availability.
                    jp.co.soramitsu.common.logger.WalletDiagnostics.recordException(error)
                    _state.value = _state.value.copy(loading = false, cards = emptyList())
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
                val result = nexusTransactionCoordinator.submitAndTrack(prepared) { submitted ->
                    // Torii has the transfer: its outcome is on screen from now on, and shows
                    // how it ends below. Closing it leaves that to the recovery worker.
                    if (nexusSendOperationIsCurrent(generation, network)) {
                        _nexusSend.value = _nexusSend.value.copy(submitting = false, result = submitted)
                    }
                }
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

    /**
     * Back on Send, from the system or the toolbar: the review goes back to its form, as on
     * SORA2; the form and the outcome close Send. Nothing happens while a review is prepared or
     * a transfer is sent.
     */
    fun nexusSendBack() {
        val current = _nexusSend.value
        when {
            !current.visible -> Unit
            current.preparing || current.submitting -> Unit
            current.step() == NexusSendStep.REVIEW -> editNexusSend()
            else -> closeNexusSend()
        }
    }

    override fun onNavIcon() {
        if (sendInFront()) nexusSendBack() else super.onNavIcon()
    }

    override fun onBackPressed() {
        if (sendInFront()) nexusSendBack() else super.onBackPressed()
    }

    private fun sendInFront(): Boolean =
        currentDestination == NexusRoutes.SEND && _nexusSend.value.visible

    override fun onCurrentDestinationChanged(curDest: String) = updateToolbar()

    /** The toolbar of each page, as on the SORA2 page it matches; the home has none. */
    private fun updateToolbar() {
        _toolbarState.value = when (currentDestination) {
            NexusRoutes.ASSET -> initSmallTitle2(title = "")
            // As SORA2's Receive. SORA Nexus before its launch only has an address to copy.
            NexusRoutes.RECEIVE -> SoramitsuToolbarState(
                type = SoramitsuToolbarType.SmallCentered(),
                basic = BasicToolbarState(
                    title = if (_nexusReceive.value.networkId?.let { it in NexusNetworks.liveIds } == false) {
                        jp.co.soramitsu.common.R.string.nexus_production_address
                    } else {
                        jp.co.soramitsu.common.R.string.wallet_receive_xor
                    },
                    navIcon = jp.co.soramitsu.ui_core.R.drawable.ic_cross,
                ),
            )
            NexusRoutes.SEND -> when (_nexusSend.value.step()) {
                NexusSendStep.FORM -> initSmallTitle2(title = jp.co.soramitsu.common.R.string.wallet_send_xor)
                NexusSendStep.REVIEW -> initSmallTitle2(title = jp.co.soramitsu.common.R.string.confirm_sending)
                // The outcome is a transaction page, which has its own Close, as on SORA2.
                NexusSendStep.OUTCOME -> initNoToolbar()
            }
            else -> initNoToolbar()
        }
    }

    /** Builds the receive page of [balance]: its QR code and avatar, off the main thread. */
    fun loadNexusReceive(balance: NexusPortfolioBalance) {
        val current = currentNexusBalance(balance) ?: return
        val loaded = _nexusReceive.value
        if (loaded.isFor(current) && loaded.screen.screenStatus != ScreenStatus.ERROR) return
        nexusReceiveLoad?.cancel()
        _nexusReceive.value = NexusReceiveUiState(current.walletId, current.networkId, current.address)
        if (currentDestination == NexusRoutes.RECEIVE) updateToolbar()
        val accountName = _state.value.curAccount.ifBlank { current.networkName }
        nexusReceiveLoad = viewModelScope.launch {
            try {
                val (qrCode, avatar) = withContext(coroutineManager.io) {
                    nexusPresentation.qrCode(current.address) to
                        nexusPresentation.avatar(current.networkId, current.address)
                }
                if (!_nexusReceive.value.isFor(current)) return@launch
                _nexusReceive.value = _nexusReceive.value.copy(
                    screen = ReceiveTokenByQrScreenState(
                        screenStatus = ScreenStatus.READY_TO_RENDER,
                        untransformedQrBitmap = qrCode,
                        untransformedUserName = accountName,
                        untransformedAvatarDrawable = avatar,
                        untransformedUserAddress = current.address,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                jp.co.soramitsu.common.logger.WalletDiagnostics.recordException(error)
                if (_nexusReceive.value.isFor(current)) {
                    _nexusReceive.value = _nexusReceive.value.copy(
                        screen = _nexusReceive.value.screen.copy(screenStatus = ScreenStatus.ERROR),
                    )
                }
            }
        }
    }

    /** Copies the address of the selected wallet's row; [announce] shows "Copied" as SORA2 does. */
    fun copyNexusAddress(balance: NexusPortfolioBalance, announce: Boolean = true) {
        val current = currentNexusBalance(balance) ?: return
        nexusPresentation.copy(current.address)
        if (announce) copiedToast.trigger()
    }

    /** Copies a value of a transfer page or review (a hash or an address). */
    fun copyNexusValue(value: String) {
        if (value.isBlank()) return
        nexusPresentation.copy(value)
        copiedToast.trigger()
    }

    /** Shares the receive page's QR code with its message, once the page is built. */
    fun shareNexusAddress(balance: NexusPortfolioBalance) {
        val current = currentNexusBalance(balance) ?: return
        val receive = _nexusReceive.value
        val qrCode = receive.screen.untransformedQrBitmap
        if (!receive.isFor(current) || receive.screen.screenStatus != ScreenStatus.READY_TO_RENDER || qrCode == null) return
        _nexusShare.value = nexusPresentation.shareFile(qrCode) to
            nexusPresentation.shareMessage(current.networkName, current.address)
    }

    /** The avatar of an address on [network], for the review's recipient. */
    fun nexusAvatar(network: WalletNetworkId, address: String) = nexusPresentation.avatar(network, address)

    /** A date in the format of the SORA2 transaction page. */
    fun nexusDateTime(timestampMillis: Long): String = nexusPresentation.dateTime(timestampMillis)

    fun isCurrentNexusBalance(balance: NexusPortfolioBalance): Boolean =
        currentNexusBalance(balance) != null

    fun isCurrentSora2Wallet(walletId: String): Boolean =
        walletId == selectedPortfolioWalletId &&
            walletId == _sora2Portfolio.value?.address

    fun openSora2Receive(walletId: String) {
        if (isCurrentSora2Wallet(walletId)) router.openQrCodeFlow()
    }

    fun openSora2Send(walletId: String) {
        if (isCurrentSora2Wallet(walletId)) assetsRouter.showContacts(SubstrateOptionsProvider.feeAssetId)
    }

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

    fun openSora2Xor(walletId: String) {
        if (isCurrentSora2Wallet(walletId)) {
            assetsRouter.showAssetDetails(SubstrateOptionsProvider.feeAssetId)
        }
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
            is FavoriteAssetsCardState -> {
                router.showAssetSettings()
            }

            is FavoritePoolsCardState -> {
                polkaswapRouter.showPoolSettings()
            }
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
