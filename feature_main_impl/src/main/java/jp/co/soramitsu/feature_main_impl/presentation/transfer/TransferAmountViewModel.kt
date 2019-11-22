/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transfer

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import java.math.BigDecimal

class TransferAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel(), WithProgress by progress {

    val balanceLiveData = MutableLiveData<String>()
    val feeMetaLiveData = MutableLiveData<TransferMeta>()

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getBalanceAndTransferMeta(updateCached: Boolean) {
        disposables.add(
            interactor.getBalanceAndTransferMeta(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pair ->
                    balanceLiveData.value = numbersFormatter.formatBigDecimal(pair.first)
                    feeMetaLiveData.value = pair.second

                    if (!updateCached) {
                        getBalanceAndTransferMeta(true)
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun nextButtonClicked(accountId: String, fullName: String, amount: BigDecimal?, description: String, transactionFee: Double) {
        disposables.add(
            interactor.getBalance(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { progress.showProgress() }
                .subscribe({ balance ->
                    progress.hideProgress()
                    if (areFieldsValid(amount, balance.toDouble(), transactionFee)) {
                        router.showTransactionConfirmation(accountId, fullName, amount!!.toDouble(), description, transactionFee)
                    }
                }, {
                    onError(it)
                })
        )
    }

    private fun areFieldsValid(amount: BigDecimal?, userAmount: Double, fee: Double): Boolean {
        if (amount == null) {
            onError(R.string.amount_error)
            return false
        }

        if (amount.toDouble() <= 0) {
            onError(R.string.amount_is_zero_error)
            return false
        }

        if (amount.toDouble() + fee > userAmount) {
            onError(R.string.insufficient_balance)
            return false
        }

        return true
    }
}