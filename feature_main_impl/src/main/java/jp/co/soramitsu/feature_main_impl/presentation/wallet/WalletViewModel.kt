/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.wallet.mappers.mapTransactionToSoraTransaction
import jp.co.soramitsu.feature_main_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.recent_events.list.models.EventItem

class WalletViewModel(
    private val interactor: WalletInteractor,
    private val router: MainRouter,
    pushHandler: PushHandler
) : BaseViewModel() {

    val balanceLiveData = MutableLiveData<String>()
    val transactionsLiveData = MutableLiveData<List<SoraTransaction>>()
    val hideSwipeProgressLiveData = MutableLiveData<Event<Unit>>()

    init {
        disposables.add(
            pushHandler.observeNewPushes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    getBalance(true)
                    getTransactionHistory(true)
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun getBalance(updateCached: Boolean) {
        disposables.add(
            interactor.getBalance(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    hideSwipeProgressLiveData.value = Event(Unit)
                    if (!updateCached) getBalance(true)
                }
                .subscribe({
                    balanceLiveData.value = DeciminalFormatter.formatBigDecimal(it)
                }, {
                    logException(it)
                })
        )
    }

    fun getTransactionHistory(updateCached: Boolean) {
        disposables.add(
            interactor.getTransactionHistory(updateCached, false)
                .map { it.map { mapTransactionToSoraTransaction(it) } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { if (!updateCached) getTransactionHistory(true) }
                .subscribe({
                    transactionsLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun sendButtonClicked() {
        balanceLiveData.value?.let {
            router.showContacts(it)
        }
    }

    fun receiveButtonClicked() {
        router.showReceiveAmount()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun eventClicked(eventItem: EventItem) {
        transactionsLiveData.value?.let { transactions ->
            transactions.firstOrNull { it.transactionId == eventItem.transactionId }?.let {
                balanceLiveData.value?.let { balance ->
                    router.showTransactionDetailsFromList(
                        it.recipientId,
                        balance,
                        it.recipient,
                        it.transactionId,
                        it.amount,
                        it.status,
                        it.dateTime,
                        it.type,
                        it.description,
                        it.fee
                    )
                }
            }
        }
    }
}