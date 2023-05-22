/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow

interface WalletInteractor {

    suspend fun getFeeToken(): Token

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun needsMigration(): Boolean

    suspend fun migrate(): Boolean

    suspend fun getContacts(query: String): List<String>?

    suspend fun processQr(contents: String): Triple<String, String, BigDecimal>

    suspend fun getSoraAccounts(): List<SoraAccount>

    suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    )

    fun subscribeSoraCardInfo(): Flow<SoraCardInformation?>

    suspend fun getSoraCardInfo(): SoraCardInformation?

    suspend fun updateSoraCardKycStatus(kycStatus: String)

    fun pollSoraCardStatusIfPending(): Flow<String?>
}
