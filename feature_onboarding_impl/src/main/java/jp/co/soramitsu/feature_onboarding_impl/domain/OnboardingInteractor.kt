/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_account_api.domain.model.UserCreatingCase
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import javax.inject.Inject

class OnboardingInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val didRepository: DidRepository
) {

    fun getMnemonic(): Single<String> {
        return didRepository.retrieveMnemonic()
            .flatMap {
                if (it.isNotEmpty()) {
                    Single.just(it)
                } else {
                    throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
                }
            }
    }

    fun runRegisterFlow(): Single<AppVersion> {
        return userRepository.checkAppVersion()
            .flatMap { version ->
                didRepository.registerUserDdo()
                    .andThen(userRepository.checkInviteCodeAvailable())
                    .andThen(Single.just(version))
            }
    }

    fun checkVersionIsSupported(): Single<AppVersion> {
        return userRepository.checkAppVersion()
    }

    fun runRecoverFlow(mnemonic: String): Completable {
        return didRepository.recoverAccount(mnemonic)
            .andThen(userRepository.getUser(true))
            .ignoreElement()
            .doOnComplete { userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED) }
    }

    fun requestNewCode(): Single<Int> {
        return userRepository.requestSMSCode()
            .subscribeOn(Schedulers.io())
    }

    fun verifySmsCode(code: String): Completable {
        return userRepository.verifySMSCode(code)
    }

    fun changePersonalData(): Completable {
        return Completable.fromAction { userRepository.saveRegistrationState(OnboardingState.INITIAL) }
    }

    fun getCountries(): Single<List<Country>> {
        return userRepository.getAllCountries()
            .subscribeOn(Schedulers.io())
    }

    fun createUser(phoneNumber: String): Single<UserCreatingCase> {
        return userRepository.createUser(phoneNumber)
    }

    fun register(firstName: String, lastName: String, countryIso: String, inviteCode: String = ""): Single<Boolean> {
        return userRepository.register(firstName, lastName, countryIso, inviteCode)
            .flatMap { inviteCorrect ->
                if (inviteCorrect) {
                    userRepository.getUser(true)
                        .map { inviteCorrect }
                } else {
                    Single.just(inviteCorrect)
                }
            }
    }

    fun getParentInviteCode(): Single<String> {
        return userRepository.getParentInviteCode()
            .subscribeOn(Schedulers.io())
    }
}