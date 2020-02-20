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
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

class TransferAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val progress: WithProgress,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val recipientId: String,
    private val recipientFullName: String,
    private val initialAmount: BigDecimal
) : BaseViewModel(), WithProgress by progress {

    private val _balanceLiveData = MutableLiveData<String>()
    val balanceLiveData: LiveData<String> = _balanceLiveData

    private val _feeFormattedLiveData = MediatorLiveData<String>()
    val feeFormattedLiveData: LiveData<String> = _feeFormattedLiveData

    private val _descriptionLiveData = MutableLiveData<String>()
    val descriptionLiveData: LiveData<String> = _descriptionLiveData

    private val _initialAmountLiveData = MutableLiveData<String>()
    val initialAmountLiveData: LiveData<String> = _initialAmountLiveData

    private val feeMetaLiveData = MutableLiveData<TransferMeta>()
    private val amountLiveData = MutableLiveData<Double>()
    private val feeLiveData = MediatorLiveData<Double>()

    init {
        feeLiveData.addSource(feeMetaLiveData) {
            calcFee()
        }

        feeLiveData.addSource(amountLiveData) {
            calcFee()
        }

        _feeFormattedLiveData.addSource(feeLiveData) { fee ->
            val feeFormatted = resourceManager.getString(R.string.wallet_transaction_fee_template).format(Const.SORA_SYMBOL, numbersFormatter.format(fee))
            _feeFormattedLiveData.value = feeFormatted
        }

        if (BigDecimal.ZERO != initialAmount) {
            _initialAmountLiveData.value = initialAmount.toString()
        }

        _descriptionLiveData.value = recipientFullName
    }

    private fun calcFee() {
        val feeMeta = feeMetaLiveData.value ?: return
        val currentAmount = amountLiveData.value ?: 0.0

        val fee = if (FeeType.FACTOR == feeMeta.feeType) {
            currentAmount * feeMeta.feeRate
        } else {
            feeMeta.feeRate
        }

        feeLiveData.value = fee
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getBalanceAndTransferMeta(updateCached: Boolean) {
        disposables.add(
            interactor.getBalanceAndTransferMeta(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pair ->
                    _balanceLiveData.value = numbersFormatter.formatBigDecimal(pair.first)
                    feeMetaLiveData.setValueIfNew(pair.second)
                    if (!updateCached) {
                        getBalanceAndTransferMeta(true)
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun nextButtonClicked(amount: BigDecimal?, description: String) {
        feeLiveData.value?.let { fee ->
            disposables.add(
                interactor.getBalance(false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { progress.showProgress() }
                    .subscribe({ balance ->
                        progress.hideProgress()
                        if (areFieldsValid(amount, balance.toDouble(), fee)) {
                            _initialAmountLiveData.value = amount!!.toString()
                            router.showTransactionConfirmation(recipientId, recipientFullName, amount.toDouble(), description, fee)
                        }
                    }, {
                        onError(it)
                    })
            )
        }
    }

    fun amountChanged(amount: Double) {
        amountLiveData.setValueIfNew(amount)
    }

    private fun areFieldsValid(amount: BigDecimal?, userAmount: Double, fee: Double): Boolean {
        if (amount == null) {
            onError(R.string.wallet_amount_error)
            return false
        }

        if (amount.toDouble() <= 0) {
            onError(R.string.wallet_amount_is_zero_error)
            return false
        }

        if (amount.toDouble() + fee > userAmount) {
            onError(R.string.wallet_insufficient_balance)
            return false
        }

        return true
    }
}