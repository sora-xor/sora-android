/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.launch
import java.math.BigDecimal

class TransactionConfirmationViewModel @AssistedInject constructor(
    private val walletInteractor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val clipboardManager: ClipboardManager,
    private val progress: WithProgress,
    @Assisted("amount") private val amount: BigDecimal,
    @Assisted("transactionFee") private val transactionFee: BigDecimal,
    @Assisted("assetId") private val assetId: String,
    @Assisted("peerId") private val peerId: String,
    @Assisted private val transferType: TransferType,
) : BaseViewModel(), WithProgress by progress {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("amount") amount: BigDecimal,
            @Assisted("transactionFee") transactionFee: BigDecimal,
            @Assisted("assetId") assetId: String,
            @Assisted("peerId") peerId: String,
            transferType: TransferType
        ): TransactionConfirmationViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            f: Factory,
            amount: BigDecimal,
            transactionFee: BigDecimal,
            assetId: String,
            peerId: String,
            transferType: TransferType
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return f.create(
                    amount,
                    transactionFee,
                    assetId,
                    peerId,
                    transferType
                ) as T
            }
        }
    }

    private val _amountFormattedLiveData = MutableLiveData<String>()
    val amountFormattedLiveData: LiveData<String> = _amountFormattedLiveData

    private val _transactionFeeFormattedLiveData = MutableLiveData<String>()
    val transactionFeeFormattedLiveData: LiveData<String> = _transactionFeeFormattedLiveData

    private val _recipientNameLiveData = MutableLiveData<String>()
    val recipientNameLiveData: LiveData<String> = _recipientNameLiveData

    private val _inputTokenNameLiveData = MutableLiveData<String>()
    val inputTokenNameLiveData: LiveData<String> = _inputTokenNameLiveData

    private val _inputTokenSymbolLiveData = MutableLiveData<String>()
    val inputTokenSymbolLiveData: LiveData<String> = _inputTokenSymbolLiveData

    private val _balanceFormattedLiveData = MutableLiveData<AssetBalanceData>()
    val balanceFormattedLiveData: LiveData<AssetBalanceData> = _balanceFormattedLiveData

    private val _inputTokenIconLiveData = MutableLiveData<Int>()
    val inputTokenIconLiveData: LiveData<Int> = _inputTokenIconLiveData

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val _transactionSuccessEvent = SingleLiveEvent<Unit>()
    val transactionSuccessEvent: LiveData<Unit> = _transactionSuccessEvent

    private lateinit var curAsset: Asset
    private lateinit var feeToken: Token

    init {
        viewModelScope.launch {
            curAsset = walletInteractor.getAssetOrThrow(assetId)
            feeToken = walletInteractor.getFeeToken()

            _balanceFormattedLiveData.value =
                AssetBalanceData(
                    amount = numbersFormatter.formatBigDecimal(
                        curAsset.balance.transferable,
                        AssetHolder.ROUNDING,
                    ),
                    style = AssetBalanceStyle(
                        R.style.TextAppearance_Soramitsu_Neu_Semibold_18,
                        R.style.TextAppearance_Soramitsu_Neu_Semibold_13
                    )
                )

            _inputTokenSymbolLiveData.value = curAsset.token.symbol
            _inputTokenNameLiveData.value = curAsset.token.name

            configureScreen()
        }
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun nextClicked() {
        soraNetTransferToRecipient()
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", peerId)
        _copiedAddressEvent.trigger()
    }

    private fun configureScreen() {
        when (transferType) {
            TransferType.VAL_TRANSFER -> {
                _amountFormattedLiveData.value =
                    numbersFormatter.formatBigDecimal(amount, curAsset.token.precision)

                if (transactionFee != BigDecimal.ZERO) {
                    _transactionFeeFormattedLiveData.value = "${
                    numbersFormatter.formatBigDecimal(
                        transactionFee,
                        feeToken.precision
                    )
                    } ${feeToken.symbol}"
                }

                _recipientNameLiveData.value = peerId
                _inputTokenIconLiveData.value = curAsset.token.icon
            }
            else -> {
            }
        }
    }

    private fun soraNetTransferToRecipient() {
        viewModelScope.launch {
            showProgress()
            try {
                val success = walletInteractor.observeTransfer(
                    peerId,
                    curAsset.token,
                    amount,
                    transactionFee
                )
                if (success) _transactionSuccessEvent.trigger()
            } catch (t: Throwable) {
                onError(t)
            } finally {
                hideProgress()
                router.returnToWalletFragment()
            }
        }
    }
}
