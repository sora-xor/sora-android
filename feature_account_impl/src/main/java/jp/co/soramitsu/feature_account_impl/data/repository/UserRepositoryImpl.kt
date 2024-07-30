/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.CardHubLocal
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UserRepositoryImpl(
    private val userDatasource: UserDatasource,
    private val credentialsDatasource: CredentialsDatasource,
    private val db: AppDatabase,
    private val coroutineManager: CoroutineManager,
    private val languagesHolder: LanguagesHolder,
) : UserRepository {

    private val currentSoraAccount = MutableStateFlow<SoraAccount?>(null)

    private val mutex = Mutex()

    init {
        coroutineManager.applicationScope.launch {
            mutex.withLock {
                initCurSoraAccount()
            }
        }
    }

    private suspend fun initCurSoraAccount() {
        val curAddress = userDatasource.getCurAccountAddress()
        if (curAddress.isEmpty()) {
            val accounts = db.accountDao().getAccounts()
            if (accounts.isNotEmpty()) {
                val newCurAccount = accounts.first()
                userDatasource.setCurAccountAddress(newCurAccount.substrateAddress)
                currentSoraAccount.value = SoraAccountMapper.map(newCurAccount)
            }
        } else {
            db.accountDao().getAccount(curAddress)?.let { soraAccountLocal ->
                currentSoraAccount.value = SoraAccountMapper.map(soraAccountLocal)
            }
        }
    }

    override suspend fun getCurSoraAccount(): SoraAccount = mutex.withLock {
        if (currentSoraAccount.value == null) {
            initCurSoraAccount()
        }
        requireNotNull(currentSoraAccount.value)
    }

    override suspend fun setCurSoraAccount(soraAccount: SoraAccount) {
        mutex.withLock {
            userDatasource.setCurAccountAddress(soraAccount.substrateAddress)
            currentSoraAccount.value = soraAccount
        }
    }

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        currentSoraAccount.asStateFlow()
            .filterNotNull()
            .distinctUntilChangedBy { it.substrateAddress }
            .onEach {
                db.referralsDao().clearTable()
            }

    override fun flowSoraAccountsList(): Flow<List<SoraAccount>> =
        db.accountDao().flowAccounts().map { list ->
            list.map {
                SoraAccountMapper.map(it)
            }
        }

    override suspend fun soraAccountsList(): List<SoraAccount> =
        db.accountDao().getAccounts().map {
            SoraAccountMapper.map(it)
        }

    override suspend fun getSoraAccountsCount(): Int {
        return db.accountDao().getAccountsCount()
    }

    override suspend fun insertSoraAccount(soraAccount: SoraAccount, newAccount: Boolean) {
        db.withTransaction {
            db.accountDao().insertSoraAccount(
                SoraAccountMapper.map(soraAccount)
            )
            db.cardsHubDao().insert(
                CardHubType.entries
                    .filter { it.boundToAccount }
                    .mapIndexed { _, type ->
                        CardHubLocal(
                            cardId = type.hubName,
                            accountAddress = soraAccount.substrateAddress,
                            visibility = if (type == CardHubType.BACKUP) newAccount else true,
                            sortOrder = type.order,
                            collapsed = false,
                        )
                    }
            )
        }
        defaultGlobalCards()
    }

    override suspend fun updateAccountName(soraAccount: SoraAccount, newName: String) {
        db.accountDao().updateAccountName(newName, soraAccount.substrateAddress)
        setCurSoraAccount(soraAccount.copy(accountName = newName))
    }

    override suspend fun savePin(pin: String) {
        userDatasource.savePin(pin)
    }

    override suspend fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override suspend fun saveRegistrationState(onboardingState: OnboardingState) {
        userDatasource.saveRegistrationState(onboardingState)
    }

    override suspend fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override suspend fun fullLogout() {
        userDatasource.clearAllData()
        db.withTransaction {
            db.accountDao().clearAll()
            db.referralsDao().clearTable()
            db.nodeDao().clearTable()
            db.globalCardsHubDao().clearTable()
            defaultGlobalCards()
        }
    }

    override suspend fun defaultGlobalCards() {
        val count = db.globalCardsHubDao().count()
        if (count == 0) {
            db.globalCardsHubDao().insert(
                CardHubType.values()
                    .filter { !it.boundToAccount }
                    .map { cardType ->
                        GlobalCardHubLocal(
                            cardId = cardType.hubName,
                            visibility = true,
                            sortOrder = cardType.order,
                            collapsed = false
                        )
                    }
            )
        }
    }

    override suspend fun clearAccountData(address: String) {
        credentialsDatasource.clearAllDataForAddress(address)
        db.withTransaction {
            db.accountDao().clearAccount(address)
            db.referralsDao().clearTable()
        }
    }

    override suspend fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return userDatasource.getParentInviteCode()
    }

    override suspend fun getAvailableLanguages(): Pair<List<Language>, Int> {
        return languagesHolder.getLanguages()
    }

    override suspend fun changeLanguage(language: String): String {
        languagesHolder.setCurrentLanguage(language)
        return language
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

    override suspend fun getSoraAccount(address: String): SoraAccount {
        val soraAccountLocal = requireNotNull(db.accountDao().getAccount(address))
        return SoraAccountMapper.map(soraAccountLocal)
    }

    override suspend fun savePinTriesUsed(triesUsed: Int) {
        userDatasource.savePinTriesUsed(triesUsed)
    }

    override suspend fun saveTimerStartedTimestamp(timestamp: Long) {
        userDatasource.saveTimerStartedTimestamp(timestamp)
    }

    override suspend fun retrievePinTriesUsed(): Int = userDatasource.retrievePinTriesUsed()

    override suspend fun retrieveTimerStartedTimestamp(): Long =
        userDatasource.retrieveTimerStartedTimestamp()

    override suspend fun resetTimerStartedTimestamp() {
        userDatasource.resetTimerStartedTimestamp()
    }

    override suspend fun accountExists(address: String): Boolean {
        return db.accountDao().getAccount(address) != null
    }

    override suspend fun resetTriesUsed() {
        userDatasource.resetPinTriesUsed()
    }
}
