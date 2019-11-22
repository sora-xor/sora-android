/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.withdrawal

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import java.math.BigDecimal

class WithdrawalAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel(), WithProgress by progress {

    companion object {
        private const val ETH_ADDRESS_LENGTH = 42
        private const val ETH_ADDRESS_PREFIX = "0x"
    }

    val balanceLiveData = MutableLiveData<String>()
    val feeMetaLiveData = MutableLiveData<WithdrawalMeta>()

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun getBalanceAndWithdrawalMeta() {
        disposables.add(
            interactor.getBalanceAndWithdrawalMeta()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pair ->
                    balanceLiveData.value = numbersFormatter.formatBigDecimal(pair.first)
                    feeMetaLiveData.value = pair.second
                }, {
                    router.popBackStackFragment()
                    logException(it)
                })
        )
    }

    fun nextButtonClicked(amount: BigDecimal?, ethAddress: String, notaryAddress: String, feeAddress: String, transactionFee: Double) {
        disposables.add(
            interactor.getBalance(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { progress.showProgress() }
                .subscribe({ balance ->
                    progress.hideProgress()
                    if (areFieldsValid(amount, balance.toDouble(), ethAddress, transactionFee)) {
                        router.showTransactionConfirmationViaEth(amount!!.toDouble(), ethAddress, notaryAddress, feeAddress, transactionFee)
                    }
                }, {
                    onError(it)
                })
        )
    }

    private fun areFieldsValid(amount: BigDecimal?, userAmount: Double, ethAddress: String, fee: Double): Boolean {
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

        if (ethAddress.length < ETH_ADDRESS_LENGTH || !ethAddress.startsWith(ETH_ADDRESS_PREFIX)) {
            onError(R.string.eth_address_error)
            return false
        }

        return true
    }
}