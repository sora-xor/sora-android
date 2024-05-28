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

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.subtractFee
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.nullZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.SendState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val clipboardManager: BasicClipboardManager,
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

    private val _transactionSuccessEvent = SingleLiveEvent<Unit>()
    val transactionSuccessEvent: LiveData<Unit> = _transactionSuccessEvent

    private val enteredFlow = MutableStateFlow(BigDecimal.ZERO)

    private var curAsset: Asset? = null
    private var feeAsset: Asset? = null
    private var fee: BigDecimal? = null
    private val assetsList = mutableListOf<Asset>()
    private var curTokenId: String = assetId
    private var hasXorReminderWarningBeenChecked = false

    private val _sendState = MutableStateFlow(
        SendState(
            address = recipientId,
            icon = avatarGenerator.createAvatar(recipientId, 40),
        )
    )
    internal val sendState = _sendState.asStateFlow()

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
                    updateCurAsset()
                    feeAsset = list.find { it.token.id == SubstrateOptionsProvider.feeAssetId }
                        ?.also { asset ->
                            if (fee == null) {
                                calcTransactionFee(asset)
                            }
                        }
                    _sendState.value.input?.amount?.let {
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
                    _sendState.value.input?.token?.id == SubstrateOptionsProvider.feeAssetId ||
                        !hasXorReminderWarningBeenChecked
                }.onEach {
                    updateTransactionReminderWarningVisibility()
                    hasXorReminderWarningBeenChecked = true
                }.collect()
        }
    }

    private fun updateCurAsset() {
        curAsset = assetsList.find { it.token.id == curTokenId }?.also { asset ->
            if (_sendState.value.input == null) {
                _sendState.value = _sendState.value.copy(
                    input = AssetAmountInputState(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        amount = initialSendAmount?.toBigDecimalOrNull(),
                        amountFiat = "",
                        enabled = false,
                        error = false,
                        errorHint = ""
                    )
                )
            } else {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        token = asset.token,
                        balance = getAssetBalanceText(asset),
                        amountFiat = asset.token.printFiat(
                            _sendState.value.input?.amount.orZero(),
                            numbersFormatter
                        )
                    )
                )
            }
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(_sendState.value.input) {
            if (this == null)
                return@with

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = fee.orZero(),
                xorChange = if (token.id == SubstrateOptionsProvider.feeAssetId) amount else null,
            )

            _sendState.value = _sendState.value.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        }

    fun onTokenChange(tokenId: String) {
        curTokenId = tokenId
        updateCurAsset()
        _sendState.value.input?.amount?.let {
            checkEnteredAmount(it)
        }
        hasXorReminderWarningBeenChecked = false
    }

    override fun onToolbarSearch(value: String) {
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        _sendState.value = _sendState.value.copy(
            searchFilter = _sendState.value.searchFilter.copy(
                filter = value,
            ),
        )
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            when (currentDestination) {
                SendRoutes.start -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.common_enter_amount,
                            searchEnabled = false,
                        )
                    )
                    _sendState.value = _sendState.value.copy(
                        input = _sendState.value.input?.copy(
                            readOnly = false,
                            amount = _sendState.value.input?.amount?.nullZero()
                        ),
                    )
                }

                SendRoutes.selectToken -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.choose_token,
                            searchEnabled = true,
                            searchPlaceholder = R.string.search_token_placeholder,
                            searchValue = _sendState.value.searchFilter.filter,
                        )
                    )
                }

                else -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            title = R.string.confirm_sending,
                            searchEnabled = false,
                        )
                    )
                    _sendState.value = _sendState.value.copy(
                        input = _sendState.value.input?.copy(
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
                _sendState.value = _sendState.value.copy(
                    feeLoading = false,
                    fee = feeAsset.token.printBalance(it, numbersFormatter, AssetHolder.ROUNDING),
                    feeFiat = feeAsset.token.printFiat(it, numbersFormatter),
                    input = _sendState.value.input?.copy(
                        enabled = true,
                    ),
                )
            }
        }
    }

    fun copyAddress() {
        clipboardManager.addToClipboard(recipientId)
        copiedToast.trigger()
    }

    fun onConfirmClick() {
        val curAsset = curAsset ?: return
        val amount = _sendState.value.input?.amount ?: return
        val fee = fee ?: return
        viewModelScope.launch {
            _sendState.value = _sendState.value.copy(
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
                _sendState.value = _sendState.value.copy(
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
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    reviewEnabled = false,
                )
            }

            feeAsset.balance.transferable < fee -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.error_transaction_fee_title),
                    ),
                    reviewEnabled = false,
                )
            }

            curAsset.balance.transferable < amount -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable < amount + fee) -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.amount_error_no_funds),
                    ),
                    reviewEnabled = false,
                )
            }

            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable - amount - fee < SubstrateOptionsProvider.existentialDeposit.toBigDecimal()) -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = true,
                        errorHint = resourceManager.getString(R.string.wallet_send_existential_warning_message),
                    ),
                    reviewEnabled = false,
                )
            }

            else -> {
                _sendState.value = _sendState.value.copy(
                    input = _sendState.value.input?.copy(
                        error = false,
                        errorHint = "",
                    ),
                    reviewEnabled = true,
                )
            }
        }
    }

    fun onReviewClick() {
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = _sendState.value.input?.amount.orZero()
            )
        )
    }

    fun amountChanged(value: BigDecimal) {
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = value,
                amountFiat = _sendState.value.input?.token?.printFiat(
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
        _sendState.value = _sendState.value.copy(
            input = _sendState.value.input?.copy(
                amount = amount,
                amountFiat = _sendState.value.input?.token?.printFiat(
                    amount,
                    numbersFormatter
                ).orEmpty()
            )
        )
        enteredFlow.value = amount
    }
}
