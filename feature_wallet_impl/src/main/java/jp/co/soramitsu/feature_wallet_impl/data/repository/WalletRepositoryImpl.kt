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

package jp.co.soramitsu.feature_wallet_impl.data.repository

import javax.inject.Inject
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.CardsHubMapper
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionCoordinator
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.migrate
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class WalletRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val pendingSubmissionCoordinator: Sora2PendingSubmissionCoordinator,
) : WalletRepository {

    override fun observeStorageAccount(address: String): Flow<String> = flow {
        val runtime = runtimeManager.getRuntimeSnapshot()
        val key = runtime.metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName).storageKey(runtime, address.toAccountId())
        val resultFlow = substrateCalls.observeStorage(key)
        emitAll(resultFlow)
    }

    override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        return datasource.saveMigrationStatus(migrationStatus)
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return datasource.observeMigrationStatus()
    }

    override suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String> {
        return datasource.retrieveClaimBlockAndTxHash()
    }

    override suspend fun needsMigration(irohaAddress: String): Boolean {
        return substrateCalls.needsMigration(irohaAddress)
    }

    override suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair,
        from: String,
    ): ExtrinsicSubmitStatus {
        return extrinsicManager.submitLegacyMigrationAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            useBatchAll = false,
            activity = Sora2SubmissionActivity.legacyClaim(),
        ) {
            migrate(
                irohaAddress = irohaAddress,
                irohaPublicKey = irohaPublicKey,
                signature = signature,
            )
        }
    }

    override suspend fun checkSubmittedClaim(
        walletId: String,
        txHash: String?,
    ): TransactionOutcome? {
        val hash = txHash
            ?: pendingSubmissionCoordinator.latestSubmissionHashOrNull(
                walletId = walletId,
                kind = Sora2SubmissionActivity.Kind.LEGACY_CLAIM,
            )
            ?: return null
        try {
            // Status only: this pass has no signer and cannot submit.
            pendingSubmissionCoordinator.recoverStatusOnlyFor(walletId, hash)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Offline or a slow node: the journal row stays as it is and is reported as it is.
        }
        return pendingSubmissionCoordinator.currentOutcomeOrNull(walletId, hash)
    }

    override fun subscribeCardsHubList(address: String): Flow<List<CardHub>> {
        return db.cardsHubDao().getCardsHub(address).map {
            it.mapNotNull { card ->
                CardsHubMapper.map(card)
            }
        }
    }

    override fun subscribeVisibleGlobalCardsHubList(): Flow<List<CardHub>> {
        return db.globalCardsHubDao().getGlobalCardsHubVisible().map {
            it.mapNotNull { cardLocal -> CardsHubMapper.map(cardLocal) }
        }
    }

    override suspend fun updateCardVisibilityOnGlobalCardsHub(cardId: String, visible: Boolean) {
        db.globalCardsHubDao().updateCardVisibility(cardId, visible)
    }

    override suspend fun updateCardVisibilityOnCardsHub(cardId: String, visible: Boolean) {
        db.cardsHubDao().updateCardVisibility(cardId, visible)
    }

    override suspend fun updateCardCollapsedState(cardId: String, collapsed: Boolean) {
        db.cardsHubDao().updateCardCollapsed(cardId, collapsed)
    }
}
