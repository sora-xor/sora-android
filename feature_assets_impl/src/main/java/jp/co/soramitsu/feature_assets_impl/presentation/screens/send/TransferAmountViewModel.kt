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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.send

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
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.subtractFee
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.nullZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.SendState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TransferAmountViewModel @AssistedInject constructor(
    private val interactor: AssetsInteractor,
    private val walletRouter: WalletRouter,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    avatarGenerator: AccountAvatarGenerator,
    @Assisted("recipientId") private val recipientId: String,
    @Assisted("assetId") private val assetId: String,
    @Assisted("initAmount") private val initialSendAmount: String?
) : BaseViewModel() {

    @AssistedFactory
    interface TransferAmountViewModelFactory {
        fun create(
            @Assisted("recipientId") recipientId: String,
            @Assisted("assetId") assetId: String,
            @Assisted("initAmount") initialSendAmount: String?
        ): TransferAmountViewModel
    }

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent
    private val _transactionSuccessEvent = SingleLiveEvent<Unit>()
    val transactionSuccessEvent: LiveData<Unit> = _transactionSuccessEvent

    private val enteredFlow = MutableStateFlow(BigDecimal.ZERO)

    private var curAsset: Asset? = null
    private var feeAsset: Asset? = null
    private var fee: BigDecimal? = null
    private val assetsList = mutableListOf<Asset>()
    private var curTokenId: String = assetId
    private var hasXorReminderWarningBeenChecked = false

    internal var sendState by mutableStateOf(
        SendState(
            address = recipientId,
            icon = avatarGenerator.createAvatar(recipientId, 40),
        )
    )

    override fun startScreen(): String = SendRoutes.start

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.common_enter_amount,
        )
        viewModelScope.launch {
            interactor.subscribeAssetsActiveOfCurAccount()
                .catch { onError(it) }
                .collectLatest { list ->
                    assetsList.clear()
                    assetsList.addAll(list)
                    sendState = sendState.copy(
                        selectSearchAssetState = sendState.selectSearchAssetState.copy(
                            fullList = mapAssetsToCardState(list, numbersFormatter)
                        )
                    )
                    updateCurAsset()
                    feeAsset = list.find { it.token.id == SubstrateOptionsProvider.feeAssetId }
                        ?.also { asset ->
                            if (fee == null) {
                                calcTransactionFee(asset)
                            }
                        }
                    sendState.input?.initialAmount?.let {
                        checkEnteredAmount(it)
                    }
                }
        }
        viewModelScope.launch {
            enteredFlow
                .drop(1)
                .debounce(ViewHelper.debounce)
                .onEach { amount ->
                    checkEnteredAmount(amount)
                }.filter {
                    sendState.input?.token?.id == SubstrateOptionsProvider.feeAssetId ||
                        !hasXorReminderWarningBeenChecked
                }.onEach {
                    updateTransactionReminderWarningVisibility()
                    hasXorReminderWarningBeenChecked = true
                }.collect()
        }
    }

    private fun updateCurAsset() {
        curAsset = assetsList.find { it.token.id == curTokenId }?.also { asset ->
            if (sendState.input == null) {
                sendState = sendState.copy(
                    input = AssetAmountInputState(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        initialAmount = initialSendAmount?.toBigDecimalOrNull(),
                        amountFiat = "",
                        enabled = false,
                        error = false,
                        errorHint = "",
                    )
                )
            } else {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        amountFiat = asset.token.printFiat(
                            sendState.input?.initialAmount.orZero(),
                            numbersFormatter
                        ),
                    )
                )
            }
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(sendState.input) {
            if (this == null)
                return@with

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = fee.orZero(),
                xorChange = if (token.id == SubstrateOptionsProvider.feeAssetId) initialAmount else null,
            )

            sendState = sendState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        }

    fun onTokenChange(tokenId: String) {
        curTokenId = tokenId
        updateCurAsset()
        sendState.input?.initialAmount?.let {
            checkEnteredAmount(it)
        }
        hasXorReminderWarningBeenChecked = false
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            when (currentDestination) {
                SendRoutes.start -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.common_enter_amount,
                        )
                    )
                    sendState = sendState.copy(
                        input = sendState.input?.copy(
                            readOnly = false,
                            initialAmount = sendState.input?.initialAmount?.nullZero()
                        ),
                    )
                }

                SendRoutes.selectToken -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.choose_token,
                        )
                    )
                }

                else -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.confirm_sending,
                        )
                    )
                    sendState = sendState.copy(
                        input = sendState.input?.copy(
                            readOnly = true,
                        ),
                    )
                }
            }
        }
    }

    private fun getAssetBalanceText(asset: Asset) = "%s (%s)".format(
        asset.printBalance(
            numbersFormatter,
            withSymbol = false,
            precision = AssetHolder.ROUNDING
        ),
        asset.printFiat(numbersFormatter)
    )

    private suspend fun calcTransactionFee(feeAsset: Asset) {
        fee = interactor.calcTransactionFee(
            recipientId, feeAsset.token, BigDecimal.ONE,
        ).also {
            if (it != null) {
                sendState = sendState.copy(
                    feeLoading = false,
                    fee = feeAsset.token.printBalance(it, numbersFormatter, AssetHolder.ROUNDING),
                    feeFiat = feeAsset.token.printFiat(it, numbersFormatter),
                    input = sendState.input?.copy(
                        enabled = true,
                    ),
                )
            }
        }
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", recipientId)
        _copiedAddressEvent.trigger()
    }

    fun onConfirmClick() {
        val curAsset = curAsset ?: return
        val amount = sendState.input?.initialAmount ?: return
        val fee = fee ?: return
        viewModelScope.launch {
            sendState = sendState.copy(
                inProgress = true
            )
            var success = ""
            try {
                success = interactor.observeTransfer(
                    recipientId,
                    curAsset.token,
                    amount,
                    fee,
                )
                if (success.isNotEmpty()) _transactionSuccessEvent.trigger()
            } catch (t: Throwable) {
                onError(t)
            } finally {
                sendState = sendState.copy(
                    inProgress = false
                )
                if (success.isNotEmpty())
                    assetsRouter.showTxDetails(success, true)
                else walletRouter.returnToHubFragment()
            }
        }
    }

    private fun checkEnteredAmount(amount: BigDecimal) {
        val fee = fee ?: return
        val feeAsset = feeAsset ?: return
        val curAsset = curAsset ?: return
        when {
            amount.isZero() -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    reviewEnabled = false,
                )
            }

            feeAsset.balance.transferable < fee -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.error_transaction_fee_title),
                    ),
                    reviewEnabled = false,
                )
            }

            curAsset.balance.transferable < amount -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable < amount + fee) -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable - amount - fee < SubstrateOptionsProvider.existentialDeposit.toBigDecimal()) -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.wallet_send_existential_warning_message),
                    ),
                    reviewEnabled = false,
                )
            }

            else -> {
                sendState = sendState.copy(
                    input = sendState.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    reviewEnabled = true,
                )
            }
        }
    }

    fun onReviewClick() {
        sendState = sendState.copy(
            input = sendState.input?.copy(
                initialAmount = sendState.input?.initialAmount.orZero()
            )
        )
    }

    fun amountChanged(value: BigDecimal) {
        sendState = sendState.copy(
            input = sendState.input?.copy(
                initialAmount = value,
                amountFiat = sendState.input?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        enteredFlow.value = value
    }

    fun optionSelected(percent: Int) {
        val fee = fee ?: return
        val curAsset = curAsset ?: return
        var amount = PolkaswapFormulas.calculateAmountByPercentage(
            curAsset.balance.transferable,
            percent.toDouble(),
            curAsset.token.precision
        )
        if (curAsset.token.id == SubstrateOptionsProvider.feeAssetId && amount > BigDecimal.ZERO) {
            amount = subtractFee(amount, curAsset.balance.transferable, fee)
        }
        sendState = sendState.copy(
            input = sendState.input?.copy(
                initialAmount = amount,
                amountFiat = sendState.input?.token?.printFiat(
                    amount,
                    numbersFormatter
                ).orEmpty()
            )
        )
        enteredFlow.value = amount
    }
}
