/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

class TransferAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val progress: WithProgress,
    private val numbersFormatter: NumbersFormatter,
    private val recipientId: String,
    private val assetId: String,
    private val recipientFullName: String,
    private var transferType: TransferType,
    private val clipboardManager: ClipboardManager,
) : BaseViewModel(), WithProgress by progress {

    private val _titleStringLiveData = MutableLiveData<String>()
    val titleStringLiveData: LiveData<String> = _titleStringLiveData

    private val _balanceFormattedLiveData = MediatorLiveData<String>()
    val balanceFormattedLiveData: LiveData<String> = _balanceFormattedLiveData

    private val _transactionFeeFormattedLiveData = MediatorLiveData<String>()
    val transactionFeeFormattedLiveData: LiveData<String> = _transactionFeeFormattedLiveData

    private val _recipientNameLiveData = MutableLiveData<String>()
    val recipientNameLiveData: LiveData<String> = _recipientNameLiveData

    private val _recipientIconLiveData = MutableLiveData<Int>()
    val recipientIconLiveData: LiveData<Int> = _recipientIconLiveData

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    private val _inputTokenLastName = MutableLiveData<String>()
    val inputTokenLastName: LiveData<String> = _inputTokenLastName

    private val _transactionFeeProgressVisibilityLiveData = MutableLiveData<Boolean>()
    val transactionFeeProgressVisibilityLiveData: LiveData<Boolean> =
        _transactionFeeProgressVisibilityLiveData

    private val _decimalLength = MutableLiveData<Int>()
    val decimalLength: LiveData<Int> = _decimalLength

    private val _copiedAddressEvent = SingleLiveEvent<Unit>()
    val copiedAddressEvent: LiveData<Unit> = _copiedAddressEvent

    private val transactionFeeLiveData = MediatorLiveData<BigDecimal>()
    private val amountLiveData = MutableLiveData<BigDecimal>()
    private val assetList = mutableListOf<Asset>()
    private val curAsset: Asset by lazy {
        requireNotNull(
            assetList.find { it.id == assetId },
            { "Asset not found" }
        )
    }

    init {
        disposables.add(
            interactor.getAssets()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterSuccess { calcTransactionFee() }
                .subscribe(
                    {
                        assetList.clear()
                        assetList.addAll(it)
                        _balanceFormattedLiveData.value = numbersFormatter.formatBigDecimal(
                            curAsset.balance,
                            curAsset.roundingPrecision
                        )
                        _decimalLength.value = curAsset.precision
                        configureScreenByTransferType()
                    },
                    {
                        logException(it)
                    }
                )
        )
    }

    private fun calcTransactionFee() {
        disposables.add(
            interactor.calcTransactionFee(
                recipientId, assetId,
                amountLiveData.value
                    ?: BigDecimal.ZERO
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { _transactionFeeProgressVisibilityLiveData.value = true }
                .doFinally { _transactionFeeProgressVisibilityLiveData.value = false }
                .subscribe(
                    {
                        transactionFeeLiveData.value = it
                    },
                    {
                        logException(it)
                    }
                )
        )
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
        // todo we need a right way to get fee asset
        val feeAsset = requireNotNull(
            assetList.find {
                it.symbol.equals(
                    SubstrateNetworkOptionsProvider.feeAssetSymbol,
                    true
                )
            },
            { "Fee asset not found" }
        )
        when {
            feeAsset.balance < fee -> onError(R.string.error_transaction_fee_title)
            curAsset.balance < amount -> onError(R.string.amount_error_no_funds)
            (curAsset.id == feeAsset.id) && (curAsset.balance < amount + fee) -> onError(R.string.amount_error_no_funds)
            (curAsset.id == feeAsset.id) && (curAsset.balance - amount - fee < SubstrateNetworkOptionsProvider.existentialDeposit.toBigDecimal()) -> onError(
                R.string.wallet_send_existential_warning_message
            )
            else -> router.showTransactionConfirmation(
                recipientId, recipientFullName, BigDecimal.ZERO,
                amount, curAsset.id, BigDecimal.ZERO,
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
                _titleStringLiveData.value = "Choose amount"

                _recipientIconLiveData.value = curAsset.iconShadow
                _recipientNameLiveData.value = recipientId.truncateUserAddress()
                _inputTokenLastName.value = curAsset.symbol

                _transactionFeeFormattedLiveData.addSource(transactionFeeLiveData) { fee ->
                    val soraFee = "${
                    numbersFormatter.formatBigDecimal(
                        fee,
                        curAsset.precision
                    )
                    } ${SubstrateNetworkOptionsProvider.feeAssetSymbol}"
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
