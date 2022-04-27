/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDatasource: UserDatasource,
    private val appVersionProvider: AppVersionProvider,
    private val db: AppDatabase,
    private val appLinksProvider: AppLinksProvider,
    private val deviceParamsProvider: DeviceParamsProvider,
) : UserRepository {

    private val currentSoraAccount = MutableStateFlow<SoraAccount?>(null)

    override suspend fun initCurSoraAccount() {
        val curAddress = userDatasource.getCurAccountAddress()
        val soraAccountLocal = db.accountDao().getAccount(curAddress)
        currentSoraAccount.value = SoraAccountMapper.map(soraAccountLocal)
    }

    override suspend fun getCurSoraAccount(): SoraAccount {
        return requireNotNull(currentSoraAccount.value)
    }

    override suspend fun setCurSoraAccount(soraAccount: SoraAccount) {
        userDatasource.setCurAccountAddress(soraAccount.substrateAddress)
        currentSoraAccount.value = soraAccount
    }

    override suspend fun setCurSoraAccount(accountAddress: String) {
        userDatasource.setCurAccountAddress(accountAddress)
        currentSoraAccount.value = SoraAccountMapper.map(db.accountDao().getAccount(accountAddress))
    }

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        currentSoraAccount.asStateFlow().filterNotNull()

    override fun flowSoraAccountsList(): Flow<List<SoraAccount>> =
        db.accountDao().flowAccounts().map { list ->
            list.map {
                SoraAccountMapper.map(it)
            }
        }

    override suspend fun getSoraAccountsCount(): Int {
        return db.accountDao().getAccountsCount()
    }

    override suspend fun insertSoraAccount(soraAccount: SoraAccount) {
        db.accountDao().insertSoraAccount(
            SoraAccountMapper.map(soraAccount)
        )
    }

    override suspend fun updateAccountName(soraAccount: SoraAccount, newName: String) {
        db.accountDao().updateAccountName(newName, soraAccount.substrateAddress)
        setCurSoraAccount(soraAccount.copy(accountName = newName))
    }

    override suspend fun getAppVersion(): String {
        return appVersionProvider.getVersionName()
    }

    override suspend fun savePin(pin: String) {
        userDatasource.savePin(pin)
    }

    override suspend fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override suspend fun getInvitationLink(): String {
        return appLinksProvider.defaultMarketUrl
    }

    override suspend fun saveRegistrationState(onboardingState: OnboardingState) {
        userDatasource.saveRegistrationState(onboardingState)
    }

    override suspend fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override suspend fun clearUserData() {
        userDatasource.clearUserData()
        db.withTransaction { db.clearAllTables() }
    }

    override suspend fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return userDatasource.getParentInviteCode()
    }

    override suspend fun getAvailableLanguages(): Pair<List<Language>, String> {
        return LanguagesHolder.getLanguages() to userDatasource.getCurrentLanguage()
    }

    override suspend fun changeLanguage(language: String): String {
        userDatasource.changeLanguage(language)
        return language
    }

    override suspend fun getSelectedLanguage(): Language {
        return LanguagesHolder.getLanguages().first {
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

    override suspend fun getAccountNameForMigration(): String {
        return userDatasource.getAccountName()
    }

    override suspend fun saveNeedsMigration(it: Boolean, soraAccount: SoraAccount) {
        userDatasource.saveNeedsMigration(it, soraAccount.substrateAddress)
    }

    override suspend fun needsMigration(soraAccount: SoraAccount): Boolean {
        return userDatasource.needsMigration(soraAccount.substrateAddress)
    }

    override suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: SoraAccount) {
        userDatasource.saveIsMigrationFetched(it, soraAccount.substrateAddress)
    }

    override suspend fun isMigrationFetched(soraAccount: SoraAccount): Boolean {
        return userDatasource.isMigrationStatusFetched(soraAccount.substrateAddress) &&
            !userDatasource.needsMigration(soraAccount.substrateAddress)
    }
}
