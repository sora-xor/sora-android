/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import javax.inject.Inject

class MainInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
) {

    fun getMnemonic(): Single<String> {
        return credentialsRepository.retrieveMnemonic()
            .flatMap {
                if (it.isNotEmpty()) {
                    Single.just(it)
                } else {
                    throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
                }
            }
    }

    fun voteForReferendum(referendumId: String, votes: Long): Completable {
        return Completable.complete()
    }

    fun voteAgainstReferendum(referendumId: String, votes: Long): Completable {
        return Completable.complete()
    }

    fun syncVotes(): Completable {
        return Completable.complete()
    }

    fun getAppVersion(): Single<String> {
        return userRepository.getAppVersion()
    }

    fun getInviteCode(): Single<String> {
        return userRepository.getParentInviteCode()
            .subscribeOn(Schedulers.io())
    }

    fun getAvailableLanguagesWithSelected(): Single<Pair<List<Language>, String>> {
        return userRepository.getAvailableLanguages()
            .subscribeOn(Schedulers.io())
    }

    fun changeLanguage(language: String): Single<String> {
        return userRepository.changeLanguage(language)
            .subscribeOn(Schedulers.io())
    }

    fun getSelectedLanguage(): Single<Language> {
        return userRepository.getSelectedLanguage()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeVotables(vararg sources: Observable<out List<Votable>>): Observable<List<Votable>> {
        return Observable.combineLatest(sources) { combined ->
            combined.map { it as List<Votable> }
                .flatten()
        }
    }

    fun setBiometryEnabled(isEnabled: Boolean): Completable {
        return userRepository.setBiometryEnabled(isEnabled)
    }

    fun isBiometryEnabled(): Single<Boolean> {
        return userRepository.isBiometryEnabled()
    }

    fun isBiometryAvailable(): Single<Boolean> {
        return userRepository.isBiometryAvailable()
    }

    fun saveAccountName(accountName: String): Completable {
        return userRepository.saveAccountName(accountName)
    }

    fun getAccountName(): Single<String> {
        return userRepository.getAccountName()
    }
}
