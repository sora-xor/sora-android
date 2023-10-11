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

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    suspend fun getSoraAccountsCount(): Int

    suspend fun getCurSoraAccount(): SoraAccount

    suspend fun setCurSoraAccount(soraAccount: SoraAccount)

    fun flowCurSoraAccount(): Flow<SoraAccount>

    fun flowSoraAccountsList(): Flow<List<SoraAccount>>

    suspend fun soraAccountsList(): List<SoraAccount>

    suspend fun insertSoraAccount(soraAccount: SoraAccount, newAccount: Boolean)

    suspend fun updateAccountName(soraAccount: SoraAccount, newName: String)

    suspend fun getRegistrationState(): OnboardingState

    suspend fun savePin(pin: String)

    suspend fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun fullLogout()

    suspend fun defaultGlobalCards()

    suspend fun clearAccountData(address: String)

    suspend fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    suspend fun getAvailableLanguages(): Pair<List<Language>, Int>

    suspend fun changeLanguage(language: String): String

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(biometryAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun getAccountNameForMigration(): String

    suspend fun saveNeedsMigration(it: Boolean, soraAccount: SoraAccount)

    suspend fun needsMigration(soraAccount: SoraAccount): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: SoraAccount)

    suspend fun isMigrationFetched(soraAccount: SoraAccount): Boolean

    suspend fun getSoraAccount(address: String): SoraAccount

    suspend fun savePinTriesUsed(triesUsed: Int)

    suspend fun saveTimerStartedTimestamp(timestamp: Long)

    suspend fun retrievePinTriesUsed(): Int

    suspend fun retrieveTimerStartedTimestamp(): Long

    suspend fun resetTriesUsed()

    suspend fun resetTimerStartedTimestamp()

    suspend fun accountExists(address: String): Boolean
}
