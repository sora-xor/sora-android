/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface UserDatasource {

    suspend fun savePin(pin: String)

    suspend fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun retrieveRegistratrionState(): OnboardingState

    suspend fun clearUserData()

    suspend fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    fun getCurrentLanguage(): String

    fun changeLanguage(language: String)

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(isAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun saveAccountName(accountName: String)

    suspend fun getAccountName(): String

    suspend fun saveNeedsMigration(it: Boolean)

    suspend fun needsMigration(): Boolean

    suspend fun isMigrationStatusFetched(): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean)
}
