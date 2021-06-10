/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDatasource: UserDatasource,
    private val appVersionProvider: AppVersionProvider,
    private val db: AppDatabase,
    private val appLinksProvider: AppLinksProvider,
    private val deviceParamsProvider: DeviceParamsProvider,
    private val languagesHolder: LanguagesHolder
) : UserRepository {

    override fun getAppVersion(): Single<String> {
        return Single.just(appVersionProvider.getVersionName())
    }

    override fun savePin(pin: String) {
        userDatasource.savePin(pin)
    }

    override fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override fun getInvitationLink(): Single<String> {
        return Single.fromCallable { appLinksProvider.defaultMarketUrl }
    }

    override fun saveRegistrationState(onboardingState: OnboardingState) {
        userDatasource.saveRegistrationState(onboardingState)
    }

    override fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override fun clearUserData(): Completable {
        return Completable.fromCallable {
            userDatasource.clearUserData()
            db.clearAllTables()
        }
    }

    override fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override fun getParentInviteCode(): Single<String> {
        return Single.just(userDatasource.getParentInviteCode())
    }

    override fun getAvailableLanguages(): Single<Pair<List<Language>, String>> {
        return Single.just(languagesHolder.getLanguages())
            .map { languages ->
                val currentLanguage = userDatasource.getCurrentLanguage()
                Pair(languages, currentLanguage)
            }
    }

    override fun changeLanguage(language: String): Single<String> {
        return Single.fromCallable { userDatasource.changeLanguage(language) }
            .map { language }
    }

    override fun getSelectedLanguage(): Single<Language> {
        return Single.just(languagesHolder.getLanguages())
            .map { languages ->
                val currentLanguage = userDatasource.getCurrentLanguage()
                languages.first { it.iso == currentLanguage }
            }
    }

    override fun setBiometryEnabled(isEnabled: Boolean): Completable {
        return Completable.fromCallable { userDatasource.setBiometryEnabled(isEnabled) }
    }

    override fun isBiometryEnabled(): Single<Boolean> {
        return Single.fromCallable { userDatasource.isBiometryEnabled() }
    }

    override fun setBiometryAvailable(biometryAvailable: Boolean): Completable {
        return Completable.fromCallable { userDatasource.setBiometryAvailable(biometryAvailable) }
    }

    override fun isBiometryAvailable(): Single<Boolean> {
        return Single.fromCallable { userDatasource.isBiometryAvailable() }
    }

    override fun saveAccountName(accountName: String): Completable {
        return Completable.fromCallable { userDatasource.saveAccountName(accountName) }
    }

    override fun getAccountName(): Single<String> {
        return Single.fromCallable { userDatasource.getAccountName() }
    }

    override fun saveNeedsMigration(it: Boolean): Completable {
        return Completable.fromCallable { userDatasource.saveNeedsMigration(it) }
    }

    override fun needsMigration(): Single<Boolean> {
        return Single.fromCallable { userDatasource.needsMigration() }
    }

    override fun saveIsMigrationFetched(it: Boolean): Completable {
        return Completable.fromCallable { userDatasource.saveIsMigrationFetched(it) }
    }

    override fun isMigrationFetched(): Single<Boolean> {
        return Single.fromCallable { userDatasource.isMigrationStatusFetched() && !userDatasource.needsMigration() }
    }
}
