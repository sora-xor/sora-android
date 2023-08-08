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

package jp.co.soramitsu.feature_wallet_impl.data.repository

import javax.inject.Inject
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.CardsHubMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SoraCardInfoMapper
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.migrate
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
    private val soraConfigManager: SoraConfigManager,
) : WalletRepository {
    private companion object {
        const val SORA_CARD_ID = "soraCardId"
    }

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
        return extrinsicManager.submitAndWaitExtrinsic(
            from = from,
            keypair = keypair,
            useBatchAll = false,
        ) {
            migrate(
                irohaAddress = irohaAddress,
                irohaPublicKey = irohaPublicKey,
                signature = signature,
            )
        }
    }

    override fun subscribeVisibleCardsHubList(address: String): Flow<List<CardHub>> {
        return db.cardsHubDao().getCardsHubVisible(address).map {
            it.mapNotNull { card ->
                CardsHubMapper.map(card)
            }
        }
    }

    override fun subscribeVisibleGlobalCardsHubList(): Flow<List<CardHub>> {
        return db.globalCardsHubDao().getGlobalCardsHubVisible().map {
            val soraCard = soraConfigManager.getSoraCard()
            it.mapNotNull { cardLocal ->
                val card = CardsHubMapper.map(cardLocal)
                when (card?.cardType) {
                    CardHubType.GET_SORA_CARD -> if (soraCard) card else null
                    CardHubType.BUY_XOR_TOKEN -> if (soraCard) card else null
                    else -> card
                }
            }
        }
    }

    override fun subscribeSoraCardInfo(): Flow<SoraCardInformation?> {
        return db.soraCardDao().observeSoraCardInfo(SORA_CARD_ID).map {
            it?.let {
                SoraCardInfoMapper.map(it)
            }
        }
    }

    override suspend fun getSoraCardInfo(): SoraCardInformation? {
        return db.soraCardDao().getSoraCardInfo(SORA_CARD_ID)?.let {
            SoraCardInfoMapper.map(it)
        }
    }

    override suspend fun updateSoraCardKycStatus(kycStatus: String) {
        db.soraCardDao().updateKycStatus(SORA_CARD_ID, kycStatus)
    }

    override suspend fun updateSoraCardInfo(
        accessToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        db.soraCardDao().insert(
            SoraCardInfoLocal(
                id = SORA_CARD_ID,
                accessToken = accessToken,
                refreshToken = "",
                accessTokenExpirationTime = accessTokenExpirationTime,
                kycStatus = kycStatus
            )
        )
    }

    override suspend fun deleteSoraCardInfo() {
        db.soraCardDao().clearTable()
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
