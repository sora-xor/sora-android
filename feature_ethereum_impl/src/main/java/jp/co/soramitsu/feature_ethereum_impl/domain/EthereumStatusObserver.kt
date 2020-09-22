package jp.co.soramitsu.feature_ethereum_impl.domain

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import java.util.concurrent.TimeUnit

class EthereumStatusObserver(
    private val ethereumRepository: EthereumRepository,
    private val didRepository: DidRepository
) {

    companion object {
        private const val POLLING_REFRESH_TIME = 10L
    }

    private var disposables: CompositeDisposable? = null

    fun syncEthereumTransactionStatuses() {
        disposables?.dispose()
        disposables = CompositeDisposable()
        disposables?.add(
            didRepository.retrieveMnemonic()
                .subscribeOn(Schedulers.io())
                .delay(POLLING_REFRESH_TIME, TimeUnit.SECONDS, Schedulers.io())
                .flatMap {
                    ethereumRepository.getEthCredentials(it)
                }.flatMap { ethCredentials ->
                    didRepository.getAccountId()
                        .flatMap { accountId ->
                            ethereumRepository.getXorTokenAddress(ethCredentials)
                                .map { Triple(ethCredentials, accountId, it) }
                        }
                }
                .flatMapCompletable {
                    ethereumRepository.updateTransactionStatuses(it.first, it.second, it.third)
                        .andThen(ethereumRepository.processLastCombinedWithdrawTransaction(it.first, it.third))
                        .andThen(ethereumRepository.processLastCombinedDepositTransaction(it.first))
                }
                .doOnError { it.printStackTrace() }
                .repeat()
                .retry()
                .subscribe({
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun release() {
        disposables?.dispose()
    }
}