/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.CardsHubMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SoraCardInfoMapper
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
import javax.inject.Inject

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
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        db.soraCardDao().insert(
            SoraCardInfoLocal(
                id = SORA_CARD_ID,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpirationTime = accessTokenExpirationTime,
                kycStatus = kycStatus
            )
        )
    }

    override suspend fun updateCardVisibilityOnCardHub(cardId: String, visible: Boolean) {
        db.globalCardsHubDao().updateCardVisibility(cardId, visible)
    }

    override suspend fun updateCardCollapsedState(cardId: String, collapsed: Boolean) {
        db.cardsHubDao().updateCardCollapsed(cardId, collapsed)
    }
}
