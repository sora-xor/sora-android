/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import kotlinx.coroutines.flow.Flow

interface WalletRepository {

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    suspend fun needsMigration(irohaAddress: String): Boolean

    suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair,
        from: String,
    ): ExtrinsicSubmitStatus

    fun observeStorageAccount(address: String): Flow<String>

    fun subscribeVisibleCardsHubList(address: String): Flow<List<CardHub>>

    fun subscribeVisibleGlobalCardsHubList(): Flow<List<CardHub>>

    fun subscribeSoraCardInfo(): Flow<SoraCardInformation?>

    suspend fun getSoraCardInfo(): SoraCardInformation?

    suspend fun updateSoraCardKycStatus(kycStatus: String)

    suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    )

    suspend fun updateCardVisibilityOnCardHub(cardId: String, visible: Boolean)

    suspend fun updateCardCollapsedState(cardId: String, collapsed: Boolean)
}
