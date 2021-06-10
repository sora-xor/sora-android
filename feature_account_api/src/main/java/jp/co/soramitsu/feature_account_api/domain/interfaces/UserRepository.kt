/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface UserRepository {

    fun getRegistrationState(): OnboardingState

    fun savePin(pin: String)

    fun retrievePin(): String

    fun saveRegistrationState(onboardingState: OnboardingState)

    fun clearUserData(): Completable

    fun getAppVersion(): Single<String>

    fun saveParentInviteCode(inviteCode: String)

    fun getParentInviteCode(): Single<String>

    fun getAvailableLanguages(): Single<Pair<List<Language>, String>>

    fun changeLanguage(language: String): Single<String>

    fun getInvitationLink(): Single<String>

    fun getSelectedLanguage(): Single<Language>

    fun setBiometryEnabled(isEnabled: Boolean): Completable

    fun isBiometryEnabled(): Single<Boolean>

    fun setBiometryAvailable(biometryAvailable: Boolean): Completable

    fun isBiometryAvailable(): Single<Boolean>

    fun saveAccountName(accountName: String): Completable

    fun getAccountName(): Single<String>

    fun saveNeedsMigration(it: Boolean): Completable

    fun needsMigration(): Single<Boolean>

    fun saveIsMigrationFetched(it: Boolean): Completable

    fun isMigrationFetched(): Single<Boolean>
}
