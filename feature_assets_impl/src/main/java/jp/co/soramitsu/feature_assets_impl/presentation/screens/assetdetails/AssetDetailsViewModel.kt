/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.domain.formatFiatOrEmpty
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetCardState
import jp.co.soramitsu.feature_assets_impl.presentation.states.FrozenXorDetailsModel
import jp.co.soramitsu.feature_assets_impl.presentation.states.emptyAssetCardState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AssetDetailsViewModel @AssistedInject constructor(
    @Assisted private val assetId: String,
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val clipboardManager: ClipboardManager,
    private val numbersFormatter: NumbersFormatter,
    private val poolsInteractor: PoolsInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    @AssistedFactory
    interface AssetDetailsViewModelFactory {
        fun create(assetId: String): AssetDetailsViewModel
    }

    companion object {
        private const val TX_HISTORY_COUNT = 5
    }

    internal var state by mutableStateOf(AssetCardState(true, emptyAssetCardState))
        private set

    private val _copyEvent = SingleLiveEvent<Unit>()
    val copyEvent: LiveData<Unit> = _copyEvent

    private var xorAssetBalance: XorAssetBalance? = null
    private var precision = 8

    init {
        _toolbarState.value = initSmallTitle2(
            title = "",
        )
        viewModelScope.launch {
            poolsInteractor.subscribePoolsCache()
                .catch { onError(it) }
                .collectLatest {
                    val filtered =
                        it.filter { poolData -> poolData.token.id == assetId || poolData.baseToken.id == assetId }
                    val mapped = mapPoolsData(filtered, numbersFormatter)
                    state = state.copy(
                        state = state.state.copy(
                            poolsState = mapped.first,
                            poolsSum = formatFiatAmount(
                                mapped.second,
                                it.fiatSymbol,
                                numbersFormatter
                            ),
                        )
                    )
                }
        }
        viewModelScope.launch {
            tryCatch {
                val asset = assetsInteractor.getAssetOrThrow(assetId)
                precision = asset.token.precision
                if (asset.token.id == SubstrateOptionsProvider.feeAssetId) {
                    if (asset.balance.transferable.isZero().not()) {
                        fetchBalanceForXor(asset.token.precision)
                    }
                }
                state = state.copy(
                    loading = false,
                    state = state.state.copy(
                        tokenId = asset.token.id,
                        tokenName = asset.token.name,
                        tokenIcon = asset.token.iconUri(),
                        tokenSymbol = asset.token.symbol,
                        poolsCardTitle = resourceManager.getString(R.string.asset_details_your_pools).format(asset.token.symbol),
                        price = asset.token.formatFiatOrEmpty(
                            asset.token.fiatPrice,
                            numbersFormatter
                        ),
                        priceChange = asset.token.fiatPriceChange?.let { f ->
                            formatFiatChange(f, numbersFormatter)
                        }.orEmpty(),
                        transferableBalance = asset.token.printBalance(
                            asset.balance.transferable,
                            numbersFormatter,
                            AssetHolder.ROUNDING
                        ),
                        transferableBalanceFiat = asset.token.printFiat(
                            asset.balance.transferable,
                            numbersFormatter
                        ),
                        frozenBalance = if (assetId == SubstrateOptionsProvider.feeAssetId) { asset.token.printBalance(xorAssetBalance?.frozen ?: BigDecimal.ZERO, numbersFormatter, AssetHolder.ROUNDING) } else { null },
                        frozenBalanceFiat = if (assetId == SubstrateOptionsProvider.feeAssetId) { asset.token.printFiat(xorAssetBalance?.frozen ?: BigDecimal.ZERO, numbersFormatter) } else { null },
                        isTransferableBalanceAvailable = asset.balance.transferable > BigDecimal.ZERO,
                    )
                )
            }
        }
        viewModelScope.launch {
            refreshActivities()
        }
    }

    private suspend fun refreshActivities() {
        val events = transactionHistoryHandler.getCachedEvents(TX_HISTORY_COUNT, assetId)
        state = state.copy(
            state = state.state.copy(
                events = events,
            )
        )
    }

    private suspend fun fetchBalanceForXor(precision: Int) {
        tryCatch {
            xorAssetBalance = assetsInteractor.getXorBalance(precision)
        }
    }

    fun onRecentClick() {
        assetsRouter.showTxList(assetId)
    }

    fun onAssetIdClick() {
        clipboardManager.addToClipboard("assetId", assetId)
        _copyEvent.trigger()
    }

    fun onHistoryItemClick(txHash: String) {
        assetsRouter.showTxDetails(txHash)
    }

    fun sendClicked() {
        assetsRouter.showContacts(assetId)
    }

    fun swapClicked() {
        polkaswapRouter.showSwap(tokenFromId = assetId)
    }

    fun receiveClicked() {
        assetsRouter.showReceive()
    }

    fun onPoolClick(ids: StringPair) {
        polkaswapRouter.showPoolDetails(ids)
    }

    fun onBalanceClick() {
        viewModelScope.launch {
            val token = assetsInteractor.getAssetOrThrow(assetId).token
            val xorBalance = xorAssetBalance
            val curState = state
            if (!curState.loading && xorBalance != null) {
                if (curState.state.xorBalance == null) {
                    state = curState.copy(
                        state = curState.state.copy(
                            xorBalance = FrozenXorDetailsModel(
                                token.printBalance(xorBalance.frozen, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.frozen, numbersFormatter),
                                token.printBalance(xorBalance.bonded, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.bonded, numbersFormatter),
                                token.printBalance(xorBalance.locked, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.locked, numbersFormatter),
                                token.printBalance(xorBalance.reserved, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.reserved, numbersFormatter),
                                token.printBalance(xorBalance.redeemable, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.redeemable, numbersFormatter),
                                token.printBalance(xorBalance.unbonding, numbersFormatter, AssetHolder.ROUNDING),
                                token.printFiat(xorBalance.unbonding, numbersFormatter),
                            )
                        )
                    )
                } else {
                    state = state.copy(
                        state = state.state.copy(
                            xorBalance = null
                        )
                    )
                }
            }
        }
    }

    fun onBuyCrypto() {
        assetsRouter.showBuyCrypto()
    }
}