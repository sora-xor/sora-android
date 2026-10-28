/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface UserDatasource {

    suspend fun savePin(pin: String)

    suspend fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun retrieveRegistratrionState(): OnboardingState

    suspend fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(isAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun getAccountName(): String

    suspend fun saveNeedsMigration(it: Boolean, suffixAddress: String)

    suspend fun needsMigration(suffixAddress: String): Boolean

    suspend fun isMigrationStatusFetched(suffixAddress: String): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean, suffixAddress: String)

    suspend fun getCurAccountAddress(): String

    suspend fun setCurAccountAddress(accountAddress: String)

    /** Atomically publishes the verified owner of retained, unsuffixed legacy credentials. */
    suspend fun completeLegacyAccountUpgrade(accountAddress: String)

    suspend fun savePinTriesUsed(triesUsed: Int)

    suspend fun saveTimerStartedTimestamp(timer: Long)

    suspend fun retrievePinTriesUsed(): Int

    suspend fun retrieveTimerStartedTimestamp(): Long

    suspend fun resetPinTriesUsed()

    suspend fun resetTimerStartedTimestamp()
}
