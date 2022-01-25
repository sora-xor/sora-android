/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.launch
import java.math.BigDecimal

class TransferAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val numbersFormatter: NumbersFormatter,
    private val recipientId: String,
    private val assetId: String,
    private val recipientFullName: String,
    private var transferType: TransferType,
    private val clipboardManager: ClipboardManager,
) : BaseViewModel() {

    private val _balanceFormattedLiveData = MediatorLiveData<AssetBalanceData>()
    val balanceFormattedLiveData: LiveData<AssetBalanceData> = _balanceFormattedLiveData

    private val _transactionFeeFormattedLiveData = MediatorLiveData<String>()
    val transactionFeeFormattedLiveData: LiveData<String> = _transactionFeeFormattedLiveData

    private val _recipientNameLiveData = MutableLiveData<String>()
    val recipientNameLiveData: LiveData<String> = _recipientNameLiveData

    private val _inputTokenIcon = MutableLiveData<Int>()
    val inputTokenIcon: LiveData<Int> = _inputTokenIcon

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    private val _inputTokenName = MutableLiveData<String>()
    val inputTokenName: LiveData<String> = _inputTokenName

    private val _inputTokenSymbol = MutableLiveData<String>()
    val inputTokenSymbol: LiveData<String> = _inputTokenSymbol

    private val _transactionFeeProgressVisibilityLiveData = MutableLiveData<Boolean>()
    val transactionFeeProgressVisibilityLiveData: LiveData<Boolean> =
        _transactionFeeProgressVisibilityLiveData

    private val _decimalLength = MutableLiveData<Int>()
    val decimalLength: LiveData<Int> = _decimalLength

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val transactionFeeLiveData = MediatorLiveData<BigDecimal>()
    private val amountLiveData = MutableLiveData<BigDecimal>()

    private lateinit var curAsset: Asset
    private lateinit var feeAsset: Asset

    init {
        viewModelScope.launch {
            curAsset = interactor.getAsset(assetId)!!
            feeAsset = interactor.getAsset(OptionsProvider.feeAssetId)!!
            _balanceFormattedLiveData.value =
                AssetBalanceData(
                    amount = numbersFormatter.formatBigDecimal(
                        curAsset.balance.transferable,
                        AssetHolder.ROUNDING
                    ),
                    style = AssetBalanceStyle(
                        R.style.TextAppearance_Soramitsu_Neu_Semibold_18,
                        R.style.TextAppearance_Soramitsu_Neu_Semibold_13
                    )
                )
            _decimalLength.value = curAsset.token.precision
            configureScreenByTransferType()
            calcTransactionFee()
        }
    }

    private suspend fun calcTransactionFee() {
        _transactionFeeProgressVisibilityLiveData.value = true
        transactionFeeLiveData.value = interactor.calcTransactionFee(
            recipientId, assetId,
            amountLiveData.value
                ?: BigDecimal.ZERO
        )
        _transactionFeeProgressVisibilityLiveData.value = false
    }

    fun copyAddress() {
        clipboardManager.addToClipboard("Address", recipientId)
        _copiedAddressEvent.trigger()
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun nextButtonClicked(amount: BigDecimal?) {
        soraNetTransfer(amount ?: BigDecimal.ZERO)
    }

    private fun soraNetTransfer(amount: BigDecimal) {
        val fee = transactionFeeLiveData.value ?: return
        when {
            feeAsset.balance.transferable < fee -> onError(R.string.error_transaction_fee_title)
            curAsset.balance.transferable < amount -> onError(R.string.amount_error_no_funds)
            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable < amount + fee) -> onError(
                R.string.amount_error_no_funds
            )
            (curAsset.token.id == feeAsset.token.id) && (curAsset.balance.transferable - amount - fee < OptionsProvider.existentialDeposit.toBigDecimal()) -> onError(
                R.string.wallet_send_existential_warning_message
            )
            else -> router.showTransactionConfirmation(
                recipientId, recipientFullName, BigDecimal.ZERO,
                amount, curAsset.token.id, BigDecimal.ZERO,
                fee, transferType
            )
        }
    }

    fun amountChanged(amount: BigDecimal) {
        amountLiveData.setValueIfNew(amount)
    }

    private fun configureScreenByTransferType() {
        when (transferType) {
            TransferType.VAL_TRANSFER -> {
                _recipientNameLiveData.value = recipientId

                _inputTokenName.value = curAsset.token.name
                _inputTokenSymbol.value = curAsset.token.symbol
                _inputTokenIcon.value = curAsset.token.icon

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    val soraFee = "${
                    numbersFormatter.formatBigDecimal(
                        fee,
                        feeAsset.token.precision
                    )
                    } ${feeAsset.token.symbol}"
                    _transactionFeeFormattedLiveData.value = soraFee
                }

                amountLiveData.observeForever {
                    _nextButtonEnableLiveData.value = it > BigDecimal.ZERO
                }
            }
            else -> {
            }
        }
    }
}
