/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import javax.inject.Inject

class PinCodeInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val walletRepository: WalletRepository
) {

    fun savePin(pin: String): Completable {
        return Completable.fromAction { userRepository.savePin(pin) }
    }

    fun checkPin(code: String): Completable {
        return Completable.create { emitter ->
            if (userRepository.retrievePin() == code) {
                emitter.onComplete()
            } else {
                emitter.onError(SoraException.businessError(ResponseCode.WRONG_PIN_CODE))
            }
        }
    }

    fun isCodeSet(): Single<Boolean> {
        return Single.just(userRepository.retrievePin().isNotEmpty())
    }

    fun resetUser(): Completable {
        return userRepository.clearUserData()
    }

    fun setBiometryAvailable(isBiometryAvailable: Boolean): Completable {
        return userRepository.setBiometryAvailable(isBiometryAvailable)
    }

    fun isBiometryAvailable(): Single<Boolean> {
        return userRepository.isBiometryAvailable()
    }

    fun isBiometryEnabled(): Single<Boolean> {
        return userRepository.isBiometryEnabled()
    }

    fun needsMigration(): Single<Boolean> {
        return userRepository.isMigrationFetched()
            .flatMapCompletable {
                if (it) {
                    Completable.complete()
                } else {
                    credentialsRepository.getIrohaAddress()
                        .flatMap { walletRepository.needsMigration(it) }
                        .flatMapCompletable { userRepository.saveNeedsMigration(it) }
                        .andThen(userRepository.saveIsMigrationFetched(true))
                }
            }
            .andThen(userRepository.needsMigration())
    }

    fun setBiometryEnabled(isEnabled: Boolean): Completable {
        return userRepository.setBiometryEnabled(isEnabled)
    }
}
