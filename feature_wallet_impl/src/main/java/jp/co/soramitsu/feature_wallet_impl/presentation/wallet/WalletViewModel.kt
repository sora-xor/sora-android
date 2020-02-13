package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.mapTransactionToSoraTransactionWithHeaders
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction

class WalletViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val numbersFormatter: NumbersFormatter,
    private val dateTimeFormatter: DateTimeFormatter,
    private val resourceManager: ResourceManager,
    pushHandler: PushHandler
) : BaseViewModel() {

    val balanceLiveData = MutableLiveData<String>()
    val transactionsLiveData = MutableLiveData<List<Any>>()
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
                    balanceLiveData.value = numbersFormatter.formatBigDecimal(it)
                }, {
                    logException(it)
                })
        )
    }

    fun getTransactionHistory(updateCached: Boolean) {
        disposables.add(
            interactor.getTransactionHistory(updateCached, false)
                .map { mapTransactionToSoraTransactionWithHeaders(it, resourceManager, numbersFormatter, dateTimeFormatter) }
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
        router.showContacts()
    }

    fun receiveButtonClicked() {
        router.showReceive()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun eventClicked(eventItem: SoraTransaction) {
        with(eventItem) {
            router.showTransactionDetailsFromList(
                recipientId,
                recipient,
                transactionId,
                amount,
                status,
                dateTime,
                type,
                description,
                fee,
                totalAmount
            )
        }
    }
}