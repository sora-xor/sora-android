/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
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

    override suspend fun getAppVersion(): String {
        return appVersionProvider.getVersionName()
    }

    override suspend fun savePin(pin: String) {
        userDatasource.savePin(pin)
    }

    override fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override suspend fun getInvitationLink(): String {
        return appLinksProvider.defaultMarketUrl
    }

    override suspend fun saveRegistrationState(onboardingState: OnboardingState) {
        userDatasource.saveRegistrationState(onboardingState)
    }

    override fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override suspend fun clearUserData() {
        userDatasource.clearUserData()
        db.withTransaction { db.clearAllTables() }
    }

    override fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return userDatasource.getParentInviteCode()
    }

    override suspend fun getAvailableLanguages(): Pair<List<Language>, String> {
        return languagesHolder.getLanguages() to userDatasource.getCurrentLanguage()
    }

    override suspend fun changeLanguage(language: String): String {
        userDatasource.changeLanguage(language)
        return language
    }

    override suspend fun getSelectedLanguage(): Language {
        return languagesHolder.getLanguages().first {
            it.iso == userDatasource.getCurrentLanguage()
        }
    }

    override suspend fun setBiometryEnabled(isEnabled: Boolean) {
        userDatasource.setBiometryEnabled(isEnabled)
    }

    override suspend fun isBiometryEnabled(): Boolean {
        return userDatasource.isBiometryEnabled()
    }

    override suspend fun setBiometryAvailable(biometryAvailable: Boolean) {
        userDatasource.setBiometryAvailable(biometryAvailable)
    }

    override suspend fun isBiometryAvailable(): Boolean {
        return userDatasource.isBiometryAvailable()
    }

    override suspend fun saveAccountName(accountName: String) {
        userDatasource.saveAccountName(accountName)
    }

    override suspend fun getAccountName(): String {
        return userDatasource.getAccountName()
    }

    override suspend fun saveNeedsMigration(it: Boolean) {
        userDatasource.saveNeedsMigration(it)
    }

    override suspend fun needsMigration(): Boolean {
        return userDatasource.needsMigration()
    }

    override suspend fun saveIsMigrationFetched(it: Boolean) {
        userDatasource.saveIsMigrationFetched(it)
    }

    override suspend fun isMigrationFetched(): Boolean {
        return userDatasource.isMigrationStatusFetched() && !userDatasource.needsMigration()
    }
}
