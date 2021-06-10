/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import javax.inject.Inject

class OnboardingInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val ethereumRepository: EthereumRepository,
) {

    fun saveRegistrationStateFinished(): Completable {
        return Completable.fromCallable {
            userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        }
    }

    fun getMnemonic(): Single<String> {
        return credentialsRepository.retrieveMnemonic()
            .flatMap {
                if (it.isEmpty()) {
                    throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
                } else {
                    Single.just(it)
                }
            }
    }

    fun isMnemonicValid(mnemonic: String): Single<Boolean> {
        return credentialsRepository.isMnemonicValid(mnemonic)
    }

    fun runRecoverFlow(mnemonic: String, accountName: String): Completable {
        return credentialsRepository.restoreUserCredentials(mnemonic)
            .andThen(getMnemonic())
            .flatMapCompletable { userRepository.saveAccountName(accountName) }
            .doOnComplete {
                userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
            }
    }

    fun createUser(accountName: String): Completable {
        return credentialsRepository.generateUserCredentials()
            .andThen(credentialsRepository.retrieveMnemonic())
            .flatMapCompletable { userRepository.saveAccountName(accountName) }
            .doFinally {
                userRepository.saveRegistrationState(OnboardingState.INITIAL)
                userRepository.saveNeedsMigration(false)
                userRepository.saveIsMigrationFetched(true)
            }
    }
}
