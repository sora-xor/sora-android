/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.domain

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_sse_api.interfaces.EventRepository
import jp.co.soramitsu.feature_sse_api.model.DepositOperationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationFailedEvent
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationStartedEvent
import jp.co.soramitsu.feature_sse_api.model.Event
import jp.co.soramitsu.feature_sse_api.model.OperationCompletedEvent
import jp.co.soramitsu.feature_sse_api.model.OperationFailedEvent
import jp.co.soramitsu.feature_sse_api.model.OperationStartedEvent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawTransaction
import java.util.concurrent.TimeUnit

class EventObserver(
    private val eventRepository: EventRepository,
    private val ethereumRepository: EthereumRepository,
    private val walletRepository: WalletRepository,
    private val didRepository: DidRepository
) {

    private var disposable: Disposable? = null

    fun observeNewEvents() {
        disposable?.dispose()
        disposable = eventRepository.observeEvents()
            .subscribeOn(Schedulers.io())
            .flatMapCompletable {
                when (it.getEventType()) {
                    Event.Type.ETH_REGISTRATION_STARTED -> handleEthRegistrationStartedEvent(it as EthRegistrationStartedEvent)
                    Event.Type.ETH_REGISTRATION_COMPLETED -> handleEthRegistrationCompletedEvent(it as EthRegistrationCompletedEvent)
                    Event.Type.ETH_REGISTRATION_FAILED -> handleEthRegistrationFailedEvent(it as EthRegistrationFailedEvent)
                    Event.Type.OPERATION_STARTED -> handleOperationStartedEvent(it as OperationStartedEvent)
                    Event.Type.OPERATION_FAILED -> handleOperationFailedEvent(it as OperationFailedEvent)
                    Event.Type.OPERATION_COMPLETED -> handleOperationCompletedEvent(it as OperationCompletedEvent)
                    Event.Type.DEPOSIT_OPERATION_COMPLETED -> handleDepositOperationCompletedEvent(it as DepositOperationCompletedEvent)
                    else -> Completable.complete()
                }
            }
            .doOnError { FirebaseCrashlytics.getInstance().recordException(it) }
            .subscribe({
            }, { })
    }

    private fun handleEthRegistrationStartedEvent(event: EthRegistrationStartedEvent): Completable {
        return Completable.fromAction {
            ethereumRepository.registrationStarted(event.operationId)
        }
    }

    private fun handleEthRegistrationCompletedEvent(event: EthRegistrationCompletedEvent): Completable {
        return Completable.fromAction {
            val ethRegisterState = ethereumRepository.getEthRegistrationState()
            if (EthRegisterState.State.IN_PROGRESS == ethRegisterState.state && ethRegisterState.transactionHash == event.operationId) {
                ethereumRepository.registrationCompleted(event.operationId)
            }
        }
    }

    private fun handleEthRegistrationFailedEvent(event: EthRegistrationFailedEvent): Completable {
        return Completable.fromAction {
            val ethRegisterState = ethereumRepository.getEthRegistrationState()
            if (EthRegisterState.State.IN_PROGRESS == ethRegisterState.state && ethRegisterState.transactionHash == event.operationId) {
                ethereumRepository.registrationFailed(event.operationId)
            }
        }
    }

    private fun handleOperationStartedEvent(event: OperationStartedEvent): Completable {
        return if (event.type == OperationStartedEvent.OperationType.WITHDRAW) {
            Completable.fromAction {
                walletRepository.getWithdrawTransaction(event.operationId)
                    ?: walletRepository.saveWithdrawTransaction(event.operationId, event.amount, event.peerId, event.peerName, event.timestamp, event.details, event.fee)
            }
        } else {
            Completable.complete()
        }
    }

    private fun handleOperationFailedEvent(event: OperationFailedEvent): Completable {
        return walletRepository.updateWithdrawTransactionStatus(event.operationId, WithdrawTransaction.Status.INTENT_FAILED)
    }

    private fun handleOperationCompletedEvent(event: OperationCompletedEvent): Completable {
        return if (event.type == OperationCompletedEvent.OperationType.WITHDRAW) {
            Maybe.fromCallable { walletRepository.getWithdrawTransaction(event.operationId) }
                .flatMap {
                    val nextStatus = if (it.status == WithdrawTransaction.Status.INTENT_STARTED) {
                        WithdrawTransaction.Status.INTENT_COMPLETED
                    } else {
                        WithdrawTransaction.Status.CONFIRM_PENDING
                    }

                    walletRepository.updateWithdrawTransactionStatus(event.operationId, nextStatus).andThen(Maybe.just(it))
                }
                .flatMapCompletable { tx ->
                    if (tx.status == WithdrawTransaction.Status.INTENT_STARTED) {
                        didRepository.retrieveMnemonic()
                            .flatMapCompletable {
                                ethereumRepository.getEthCredentials(it)
                                    .flatMap { ethCredentials -> ethereumRepository.getValTokenAddress(ethCredentials).map { Pair(ethCredentials, it) } }
                                    .flatMapCompletable { pair ->
                                        didRepository.getAccountId()
                                            .flatMap { accountId ->
                                                ethereumRepository.confirmWithdraw(pair.first, tx.withdrawAmount, tx.intentTxHash, accountId, tx.gasPrice, tx.gasLimit, pair.second)
                                            }
                                            .flatMapCompletable { walletRepository.updateWithdrawTransactionStatus(event.operationId, WithdrawTransaction.Status.CONFIRM_PENDING)
                                                .andThen(walletRepository.updateWithdrawTransactionConfirmHash(event.operationId, it))
                                            }
                                            .onErrorResumeNext {
                                                FirebaseCrashlytics.getInstance().recordException(it)
                                                walletRepository.updateWithdrawTransactionStatus(event.operationId, WithdrawTransaction.Status.CONFIRM_FAILED)
                                            }
                                    }
                            }
                    } else {
                        Completable.complete()
                    }
                }
        } else {
            Completable.complete()
        }
    }

    private fun handleDepositOperationCompletedEvent(event: DepositOperationCompletedEvent): Completable {
        return didRepository.retrieveKeypair()
            .delay(5, TimeUnit.SECONDS)
            .flatMap { keypair -> didRepository.getAccountId().map { Pair(it, keypair) } }
            .flatMapCompletable { walletRepository.finishDepositTransaction(event.sidechainHash, it.first, it.second) }
    }

    fun release() {
        eventRepository.release()
        disposable?.dispose()
    }
}