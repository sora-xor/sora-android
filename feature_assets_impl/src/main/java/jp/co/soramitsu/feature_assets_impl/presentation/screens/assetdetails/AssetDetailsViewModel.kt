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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.domain.formatFiatOrEmpty
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetCardState
import jp.co.soramitsu.feature_assets_impl.presentation.states.FrozenXorDetailsModel
import jp.co.soramitsu.feature_assets_impl.presentation.states.emptyAssetCardState
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val soraConfigManager: SoraConfigManager,
    private val coroutineManager: CoroutineManager,
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
        onPullToRefresh()
    }

    private fun getBalance() {
        state = state.copy(
            loading = true,
        )
        viewModelScope.launch {
            tryCatch {
                val soraCard = soraConfigManager.getSoraCard()
                val asset = assetsInteractor.getAssetOrThrow(assetId)
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
                            numbersFormatter,
                            true,
                        ),
                        priceChange = asset.token.fiatPriceChange?.let { f ->
                            formatFiatChange(f, numbersFormatter)
                        }.orEmpty(),
                        transferableBalance = asset.token.printBalance(
                            asset.balance.transferable,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        ),
                        transferableBalanceFiat = asset.token.printFiat(
                            asset.balance.transferable,
                            numbersFormatter,
                        ),
                        frozenBalance = if (assetId == SubstrateOptionsProvider.feeAssetId) {
                            asset.token.printBalance(xorAssetBalance?.frozen ?: BigDecimal.ZERO, numbersFormatter, AssetHolder.ROUNDING)
                        } else {
                            null
                        },
                        frozenBalanceFiat = if (assetId == SubstrateOptionsProvider.feeAssetId) {
                            asset.token.printFiat(xorAssetBalance?.frozen ?: BigDecimal.ZERO, numbersFormatter)
                        } else {
                            null
                        },
                        isTransferableBalanceAvailable = asset.balance.transferable > BigDecimal.ZERO,
                        buyCryptoAvailable = soraCard && (asset.token.id == SubstrateOptionsProvider.feeAssetId),
                    )
                )
            }
        }
    }

    fun onPullToRefresh() {
        getBalance()
        refreshActivities()
    }

    private fun refreshActivities() {
        viewModelScope.launch {
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

    private suspend fun fetchBalanceForXor(precision: Int) {
        tryCatch {
            xorAssetBalance = assetsInteractor.getXorBalance(precision)
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
