/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import java.util.Date

class TransactionConfirmationViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val progress: WithProgress,
    private val resourceManager: ResourceManager,
    private val numbersFormatter: NumbersFormatter,
    private val amount: Double,
    private val fee: Double,
    private val description: String,
    private val ethAddress: String,
    private val recipientFullName: String,
    private val recipientId: String,
    private val notaryAddress: String,
    private val feeAddress: String
) : BaseViewModel(), WithProgress by progress {

    private val _amountFormattedLiveData = MutableLiveData<String>()
    val amountFormattedLiveData: LiveData<String> = _amountFormattedLiveData

    private val _feeFormattedLiveData = MutableLiveData<String>()
    val feeFormattedLiveData: LiveData<String> = _feeFormattedLiveData

    private val _totalAmountFormattedLiveData = MutableLiveData<String>()
    val totalAmountFormattedLiveData: LiveData<String> = _totalAmountFormattedLiveData

    private val _descriptionLiveData = MutableLiveData<String>()
    val descriptionLiveData: LiveData<String> = _descriptionLiveData

    private val _btnTitleLiveData = MutableLiveData<String>()
    val btnTitleLiveData: LiveData<String> = _btnTitleLiveData

    init {
        _amountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(amount)}"
        _feeFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(fee)}"
        _totalAmountFormattedLiveData.value = "${Const.SORA_SYMBOL} ${numbersFormatter.format(amount + fee)}"

        if (description.isNotEmpty()) {
            _descriptionLiveData.value = description
        }

        if (ethAddress.isNotEmpty()) {
            _descriptionLiveData.value = ethAddress
        }

        _btnTitleLiveData.value = if (ethAddress.isEmpty()) {
            recipientFullName
        } else {
            resourceManager.getString(R.string.wallet_total_amount_template).format(Const.SORA_SYMBOL, amount + fee)
        }
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun nextClicked() {
        if (ethAddress.isEmpty()) {
            transferToRecipient()
        } else {
            transferWithdraw()
        }
    }

    private fun transferToRecipient() {
        disposables.add(
            interactor.transferAmount(amount.toString(), recipientId, description, fee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({ transactionId ->
                    router.showTransferTransactionDetails(
                        recipientId,
                        recipientFullName,
                        transactionId,
                        amount,
                        Transaction.Status.PENDING.toString(),
                        Date(),
                        Transaction.Type.OUTGOING,
                        description,
                        fee,
                        fee + amount
                    )
                }, {
                    onError(it)
                })
        )
    }

    private fun transferWithdraw() {
        disposables.add(
            interactor.withdrawFlow(amount.toString(), ethAddress, notaryAddress, feeAddress, fee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({
                    router.showWithdrawTransactionDetails(
                        recipientId,
                        recipientFullName,
                        amount,
                        Transaction.Status.PENDING.toString(),
                        Date(),
                        Transaction.Type.OUTGOING,
                        "${resourceManager.getString(R.string.wallet_withdrawal_description)} $ethAddress",
                        fee,
                        fee + amount
                    )
                }, {
                    onError(it)
                })
        )
    }
}