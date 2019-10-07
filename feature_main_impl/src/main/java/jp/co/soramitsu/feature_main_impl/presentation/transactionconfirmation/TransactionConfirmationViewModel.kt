/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transactionconfirmation

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.util.Date

class TransactionConfirmationViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
    private val resourceManager: ResourceManager
) : BaseViewModel(), WithProgress by progress {

    fun btnNextClicked(recipientName: String, accountId: String, amount: Double, description: String, fee: Double) {
        disposables.add(
            interactor.transferAmount(amount.toString(), accountId, description, fee.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({ transactionId ->
                    router.showTransactionDetails(
                        recipientName,
                        transactionId,
                        amount,
                        Transaction.Status.PENDING.toString(),
                        Date(),
                        Transaction.Type.OUTGOING,
                        description,
                        fee
                    )
                }, {
                    onError(it)
                })
        )
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun btnNextClickedForWithdraw(amount: Double, ethAddress: String, notaryAddress: String, feeAddress: String, feeAmount: Double) {
        disposables.add(
            interactor.withdrawFlow(amount.toString(), ethAddress, notaryAddress, feeAddress, feeAmount.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doOnTerminate { hideProgress() }
                .subscribe({
                    router.showTransactionDetails(
                        amount,
                        Transaction.Status.PENDING.toString(),
                        Date(),
                        Transaction.Type.OUTGOING,
                        "${resourceManager.getString(R.string.withdrawal_description)} $ethAddress",
                        feeAmount
                    )
                }, {
                    onError(it)
                })
        )
    }
}