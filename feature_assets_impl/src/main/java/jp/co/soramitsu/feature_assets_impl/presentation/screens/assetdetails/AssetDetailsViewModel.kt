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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.domain.formatFiatOrEmpty
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetCardState
import jp.co.soramitsu.feature_assets_impl.presentation.states.FrozenXorDetailsModel
import jp.co.soramitsu.feature_assets_impl.presentation.states.emptyAssetCardState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AssetDetailsViewModel @AssistedInject constructor(
    @Assisted private val assetId: String,
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val walletRouter: WalletRouter,
    private val clipboardManager: BasicClipboardManager,
    private val numbersFormatter: NumbersFormatter,
    private val poolsInteractor: PoolsInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    private val resourceManager: ResourceManager,
    private val coroutineManager: CoroutineManager,
) : BaseViewModel() {

    @AssistedFactory
    interface AssetDetailsViewModelFactory {
        fun create(assetId: String): AssetDetailsViewModel
    }

    companion object {
        private const val TX_HISTORY_COUNT = 5

        /** How long the page waits for the selected node before it shows the saved amounts. */
        internal const val XOR_BREAKDOWN_READ_TIMEOUT_MILLIS = 10_000L
    }

    internal var state by mutableStateOf(AssetCardState(true, emptyAssetCardState))
        private set

    private var xorAssetBalance: XorAssetBalance? = null

    init {
        _toolbarState.value = initSmallTitle2(
            title = "",
        )
        viewModelScope.launch {
            poolsInteractor.subscribePoolsCacheOfCurAccount()
                .catch { onError(it) }
                .collectLatest {
                    val filtered =
                        it.filter { poolData -> poolData.basic.targetToken.id == assetId || poolData.basic.baseToken.id == assetId }
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
        // A transaction of this asset that is still waiting for confirmation is listed here as
        // well, and the list follows it when the background status check resolves it.
        transactionHistoryHandler.flowLocalTransactions()
            .catch { }
            .onEach { refreshActivities() }
            .launchIn(viewModelScope)
        onPullToRefresh()
    }

    private fun getBalance() {
        state = state.copy(
            loading = true,
        )
        viewModelScope.launch {
            tryCatch {
                val asset = assetsInteractor.getAssetOrThrow(assetId)
                val isXor = asset.token.id == SubstrateOptionsProvider.feeAssetId
                if (isXor) {
                    // Whatever the transferable amount is. An account whose XOR is all locked
                    // has nothing transferable, and it is the one that most needs to see where
                    // its XOR is: skipping the breakdown for it showed "Frozen 0".
                    xorAssetBalance = readXorBreakdown(asset)
                }
                val xorBalance = xorAssetBalance?.takeIf { isXor }
                // For XOR every amount on the card comes from the one breakdown, so that the
                // liquid and the frozen amount always add up to what the account holds.
                val transferable = xorBalance?.transferable ?: asset.balance.transferable
                state = state.copy(
                    loading = false,
                    state = state.state.copy(
                        tokenId = asset.token.id,
                        tokenName = asset.token.name,
                        tokenIcon = asset.token.iconUri(),
                        tokenSymbol = asset.token.symbol,
                        poolsCardTitle = resourceManager.getString(R.string.asset_details_your_pools)
                            .format(asset.token.symbol),
                        price = asset.token.formatFiatOrEmpty(
                            asset.token.fiatPrice,
                            numbersFormatter,
                            true,
                        ),
                        priceChange = asset.token.fiatPriceChange?.let { f ->
                            formatFiatChange(f, numbersFormatter)
                        }.orEmpty(),
                        transferableBalance = asset.token.printBalance(
                            transferable,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        ),
                        transferableBalanceFiat = asset.token.printFiat(
                            transferable,
                            numbersFormatter,
                        ),
                        frozenBalance = xorBalance?.let {
                            asset.token.printBalance(
                                it.frozen,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            )
                        },
                        frozenBalanceFiat = xorBalance?.let {
                            asset.token.printFiat(it.frozen, numbersFormatter)
                        },
                        lockedNote = xorBalance?.locked
                            ?.takeIf { it > BigDecimal.ZERO }
                            ?.let { locked ->
                                resourceManager.getString(R.string.send_locked_balance_note).format(
                                    asset.token.printBalance(
                                        locked,
                                        numbersFormatter,
                                        AssetHolder.ROUNDING,
                                    )
                                )
                            },
                        isTransferableBalanceAvailable = transferable > BigDecimal.ZERO,
                    )
                )
            }
        }
    }

    /**
     * The parts of the XOR balance, from the network when it answers in time and otherwise from
     * the row saved at the last balance update. Never an error and never an endless wait: a
     * selected node that does not answer must not leave the page without its Frozen amount.
     */
    private suspend fun readXorBreakdown(asset: Asset): XorAssetBalance {
        val live = try {
            withTimeoutOrNull(XOR_BREAKDOWN_READ_TIMEOUT_MILLIS) {
                assetsInteractor.getXorBalance(asset.token.precision)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            FirebaseWrapper.recordException(error)
            null
        }
        return live ?: BalanceWrapper.mapStoredXorBalance(asset.balance)
    }

    fun onPullToRefresh() {
        getBalance()
        refreshActivities()
    }

    private fun refreshActivities() {
        viewModelScope.launch {
            // A failed read keeps the list as it is; it is read again on the next refresh.
            tryCatch {
                val events =
                    withContext(coroutineManager.io) {
                        transactionHistoryHandler.getCachedEvents(TX_HISTORY_COUNT, assetId)
                    }
                state = state.copy(
                    state = state.state.copy(
                        events = events,
                    )
                )
            }
        }
    }

    fun onRecentClick() {
        assetsRouter.showTxList(assetId)
    }

    fun onAssetIdClick() {
        clipboardManager.addToClipboard(assetId)
        copiedToast.trigger()
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
        walletRouter.openQrCodeFlow()
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
                                token.printBalance(
                                    xorBalance.frozen,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
                                token.printFiat(xorBalance.frozen, numbersFormatter),
                                token.printBalance(
                                    xorBalance.bonded,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
                                token.printFiat(xorBalance.bonded, numbersFormatter),
                                token.printBalance(
                                    xorBalance.locked,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
                                token.printFiat(xorBalance.locked, numbersFormatter),
                                token.printBalance(
                                    xorBalance.reserved,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
                                token.printFiat(xorBalance.reserved, numbersFormatter),
                                token.printBalance(
                                    xorBalance.redeemable,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
                                token.printFiat(xorBalance.redeemable, numbersFormatter),
                                token.printBalance(
                                    xorBalance.unbonding,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                ),
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
}
