/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.data.network.substrate.Events
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicTransferTypes
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEvent
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapBalance
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject

@ExperimentalPagingApi
class WalletRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val soraScanApi: SoraScanApi,
    private val fileManager: FileManager,
    private val gson: Gson,
    private val assetHolder: AssetHolder,
    private val resourceManager: ResourceManager,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
) : WalletRepository {

    private val tokensDeferred = CompletableDeferred<List<Token>>()

    private lateinit var pager: Pager<Int, ExtrinsicLocal>

    private fun getOrInitPager(
        address: String,
    ): Pager<Int, ExtrinsicLocal> {
        return synchronized(this) {
            if (::pager.isInitialized) {
                pager
            } else {
                pager = Pager(
                    config = PagingConfig(
                        pageSize = 40,
                    ),
                    remoteMediator = TransactionsRemoteMediator(
                        soraScanApi,
                        db,
                        address,
                        tokensDeferred,
                        gson
                    )
                ) {
                    db.transactionDao().getExtrinsicPaging()
                }
                pager
            }
        }
    }

    override fun getTransactionsFlow(
        address: String,
        assetId: String
    ): Flow<PagingData<Transaction>> =
        getOrInitPager(address).flow.map { pagingData ->
            pagingData
                .map {
                    val tokens = tokensDeferred.await()
                    val params = db.transactionDao().getParamsOfExtrinsic(it.txHash)
                    mapTransactionLocalToTransaction(it, tokens, params)
                }
                .filter { tx ->
                    when {
                        assetId.isEmpty() -> {
                            true
                        }
                        tx is Transaction.Transfer -> {
                            tx.token.id == assetId
                        }
                        tx is Transaction.Swap -> {
                            tx.tokenFrom.id == assetId || tx.tokenTo.id == assetId
                        }
                        else -> {
                            false
                        }
                    }
                }
        }

    override fun observeStorageAccount(account: Any): Flow<String> {
        val key = RuntimeHolder.getRuntime().metadata.module(Pallete.SYSTEM.palleteName)
            .storage(Storage.ACCOUNT.storageName).storageKey(RuntimeHolder.getRuntime(), account)
        return wsConnection.observeStorage(key)
    }

    override suspend fun getTransaction(txHash: String) = mapTransactionLocalToTransaction(
        db.transactionDao().getExtrinsic(txHash),
        tokensDeferred.await(),
        db.transactionDao().getParamsOfExtrinsic(txHash)
    )

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
        return wsConnection.needsMigration(irohaAddress)
    }

    override suspend fun checkEvents(blockHash: String): List<BlockEvent> {
        return wsConnection.checkEvents(RuntimeHolder.getRuntime(), blockHash).let { list ->
            list.map {
                BlockEvent(
                    it.event.moduleIndex,
                    it.event.eventIndex,
                    (it.phase as? PhaseRecord.ApplyExtrinsic)?.extrinsicId?.toLong()
                )
            }
        }
    }

    override suspend fun getBlock(blockHash: String): BlockResponse {
        return wsConnection.getBlock(blockHash)
    }

    override suspend fun isTxSuccessful(
        extrinsicId: Long,
        blockHash: String,
        txHash: String
    ): Boolean {
        return checkEvents(blockHash)
            .let { events ->

                if (events.isEmpty()) {
                    FirebaseWrapper.recordException(Throwable("No success or failed event in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
                }

                val (moduleIndexSuccess, eventIndexSuccess) = RuntimeHolder.getRuntime().metadata.module(
                    Pallete.SYSTEM.palleteName
                ).event(Events.EXTRINSIC_SUCCESS.eventName).index
                val (moduleIndexFailed, eventIndexFailed) = RuntimeHolder.getRuntime().metadata.module(
                    Pallete.SYSTEM.palleteName
                ).event(Events.EXTRINSIC_FAILED.eventName).index
                val successEvent = events.find { event ->
                    event.module == moduleIndexSuccess && event.event == eventIndexSuccess && event.number == extrinsicId
                }
                val failedEvent = events.find { event ->
                    event.module == moduleIndexFailed && event.event == eventIndexFailed && event.number == extrinsicId
                }

                when {
                    successEvent != null -> {
                        true
                    }
                    failedEvent != null -> {
                        false
                    }
                    else -> {
                        FirebaseWrapper.recordException(Throwable("No success or failed event in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
                        false
                    }
                }
            }
    }

    override suspend fun transfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): String {
        val precision = db.assetDao().getPrecisionOfToken(assetId)
            ?: throw IllegalArgumentException("Token ($assetId) not found in db")
        return wsConnection.transfer(
            keypair,
            from,
            to,
            assetId,
            mapBalance(amount, precision),
            RuntimeHolder.getRuntime()
        )
    }

    override suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Keypair
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        return wsConnection.migrate(
            irohaAddress,
            irohaPublicKey,
            signature,
            keypair,
            RuntimeHolder.getRuntime()
        )
            .map {
                if (it.second is ExtrinsicStatusResponse.ExtrinsicStatusFinalized) {
                    datasource.saveClaimBlockAndTxHash(
                        (it.second as ExtrinsicStatusResponse.ExtrinsicStatusFinalized).inBlock,
                        it.first
                    )
                }
                it
            }
    }

    override suspend fun observeTransfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        return db.assetDao().getPrecisionOfToken(assetId)?.let { precision ->
            wsConnection.observeTransfer(
                keypair,
                from,
                to,
                assetId,
                mapBalance(amount, precision),
                RuntimeHolder.getRuntime()
            )
        } ?: throw IllegalArgumentException("Token ($assetId) not found in db")
    }

    override suspend fun calcTransactionFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): BigDecimal {
        return db.assetDao().getPrecisionOfToken(assetId)?.let { precision ->
            val fee = wsConnection.calcFee(
                from,
                to,
                assetId,
                mapBalance(amount, precision),
                RuntimeHolder.getRuntime()
            )
            mapBalance(fee, precision)
        } ?: throw IllegalArgumentException("Token ($assetId) not found in db")
    }

    private suspend fun updateTokens(address: String) {
        val tokens = fetchTokensList()
        val whiteList = runCatching {
            val whileListRaw = fileManager.readAssetFile("whitelist.json")
            val ids: List<String> =
                gson.fromJson(whileListRaw, object : TypeToken<List<String>>() {}.type)
            ids
        }.getOrDefault(emptyList())
        val assets = db.assetDao().getAssets(address)
        val tokensExist = db.assetDao().getTokensByList(assets.map { it.tokenLocal.id })
        val partition = tokens.partition {
            tokensExist.find { t -> t.id == it.id } != null
        }
        db.assetDao().insertTokenList(
            partition.second.map {
                TokenLocal(
                    it.id,
                    it.name,
                    it.symbol,
                    it.precision,
                    it.isMintable,
                    if (it.id in whiteList) AssetHolder.DEFAULT_WHITE_LIST_NAME else "",
                    assetHolder.isHiding(it.id),
                )
            }
        )
        db.assetDao().updateTokenList(
            partition.first.map {
                TokenLocal(
                    it.id,
                    it.name,
                    it.symbol,
                    it.precision,
                    it.isMintable,
                    if (it.id in whiteList) AssetHolder.DEFAULT_WHITE_LIST_NAME else "",
                    assetHolder.isHiding(it.id),
                )
            }
        )
        val allTokens = db.assetDao().getTokensAll()
        tokensDeferred.complete(
            allTokens.map { tokenLocal ->
                assetLocalToAssetMapper.map(tokenLocal, resourceManager)
            }
        )
    }

    override suspend fun updateBalancesVisibleAssets(address: String) {
        val assets = db.assetDao().getAssetsVisible(address)
        val balances = fetchBalances(address, assets.map { it.tokenLocal.id })
        val updated = assets.mapIndexed { index, assetTokenLocal ->
            assetTokenLocal.assetLocal.copy(
                free = mapBalance(
                    balances[index],
                    assetTokenLocal.tokenLocal.precision
                )
            )
        }
        db.assetDao().insertAssets(updated)
    }

    override suspend fun updateWhitelistBalances(address: String) {
        updateTokens(address)
        val assetsLocal =
            db.assetDao().getAssetsWhitelist(AssetHolder.DEFAULT_WHITE_LIST_NAME, address)
        var amount = assetsLocal.count { it.assetLocal != null }
        val assetsLocalSorted = assetsLocal.sortedBy { it.tokenLocal.symbol }
        val balances = fetchBalances(address, assetsLocalSorted.map { it.tokenLocal.id })
        val updated = assetsLocalSorted.mapIndexed { index, assetTokenLocal ->
            assetTokenLocal.assetLocal?.copy(
                free = mapBalance(
                    balances[index],
                    assetTokenLocal.tokenLocal.precision
                )
            ) ?: AssetLocal(
                tokenId = assetTokenLocal.tokenLocal.id,
                accountAddress = address,
                displayAsset = false,
                position = ++amount,
                free = mapBalance(
                    balances[index],
                    assetTokenLocal.tokenLocal.precision
                ),
                reserved = BigDecimal.ZERO,
                miscFrozen = BigDecimal.ZERO,
                feeFrozen = BigDecimal.ZERO,
                bonded = BigDecimal.ZERO,
                redeemable = BigDecimal.ZERO,
                unbonding = BigDecimal.ZERO,
            )
        }
        db.assetDao().insertAssets(updated)
    }

    override suspend fun getAssetsWhitelist(address: String): List<Asset> {
        val assetsLocal =
            db.assetDao().getAssetsWhitelist(AssetHolder.DEFAULT_WHITE_LIST_NAME, address)
        var amount = assetsLocal.count { it.assetLocal != null }
        val updated = assetsLocal.mapIndexed { _, assetTokenLocal ->
            val assetLocal = assetTokenLocal.assetLocal
            assetLocal?.copy(
                free = assetLocal.free
            )
                ?: AssetLocal(
                    tokenId = assetTokenLocal.tokenLocal.id,
                    accountAddress = address,
                    displayAsset = false,
                    position = ++amount,
                    free = BigDecimal.ZERO,
                    reserved = BigDecimal.ZERO,
                    miscFrozen = BigDecimal.ZERO,
                    feeFrozen = BigDecimal.ZERO,
                    bonded = BigDecimal.ZERO,
                    redeemable = BigDecimal.ZERO,
                    unbonding = BigDecimal.ZERO,
                )
        }
        db.assetDao().insertAssets(updated)
        return assetsLocal.mapIndexed { index, assetTokenLocal ->
            assetLocalToAssetMapper.map(
                AssetTokenLocal(
                    tokenLocal = assetTokenLocal.tokenLocal,
                    assetLocal = updated[index]
                ),
                resourceManager
            )
        }
    }

    override suspend fun getAssetsVisible(address: String): List<Asset> {
        return db.assetDao().getAssetsVisible(address).ifEmpty { checkDefaultAssetData(address) }
            .map {
                assetLocalToAssetMapper.map(it, resourceManager)
            }
    }

    override fun subscribeVisibleAssets(address: String): Flow<List<Asset>> {
        return db.assetDao().flowAssetsVisible(address).map {
            it.map { l -> assetLocalToAssetMapper.map(l, resourceManager) }
        }
    }

    private suspend fun fetchTokensList(): List<TokenInfoDto> =
        wsConnection.fetchAssetsList(RuntimeHolder.getRuntime())

    private suspend fun fetchBalances(
        address: String,
        assetIdList: List<String>
    ): List<BigInteger> {
        return assetIdList.map {
            fetchAssetBalance(address, it)
        }
    }

    override suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance {
        return wsConnection.fetchXORBalances(RuntimeHolder.getRuntime(), address).let {
            val transferable = mapBalance(it.free - it.miscFrozen.max(it.feeFrozen), precision)
            val frozen = mapBalance(it.reserved + it.miscFrozen.max(it.feeFrozen), precision)
            val total = mapBalance(it.free + it.reserved, precision)
            val locked = mapBalance(it.miscFrozen.max(it.feeFrozen), precision)
            val bonded = mapBalance(it.bonded, precision)
            val reserved = mapBalance(it.reserved, precision)
            val redeemable = mapBalance(it.redeemable, precision)
            val unbonding = mapBalance(it.unbonding, precision)

            XorAssetBalance(
                transferable,
                frozen,
                total,
                locked,
                bonded,
                reserved,
                redeemable,
                unbonding
            )
        }
    }

    private suspend fun fetchAssetBalance(address: String, assetId: String): BigInteger {
        return wsConnection.fetchBalance(address, assetId)
    }

    override suspend fun updateTransactionSuccess(hash: String, success: Boolean) {
        db.transactionDao().updateSuccess(hash, success)
    }

    override suspend fun saveTransfer(
        to: String,
        assetId: String,
        amount: BigDecimal,
        status: ExtrinsicStatusResponse,
        hash: String,
        fee: BigDecimal,
        eventSuccess: Boolean?
    ) {
        db.withTransaction {
            db.transactionDao().insert(
                ExtrinsicLocal(
                    hash,
                    (status as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    fee,
                    when (status) {
                        is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> ExtrinsicStatus.COMMITTED
                        is ExtrinsicStatusResponse.ExtrinsicStatusPending -> ExtrinsicStatus.PENDING
                    },
                    Date().time,
                    ExtrinsicType.TRANSFER,
                    eventSuccess,
                    true,
                )
            )
            db.transactionDao().insertParams(
                listOf(
                    ExtrinsicParamLocal(
                        hash,
                        ExtrinsicParam.PEER.paramName,
                        to
                    ),
                    ExtrinsicParamLocal(
                        hash,
                        ExtrinsicParam.TOKEN.paramName,
                        assetId
                    ),
                    ExtrinsicParamLocal(
                        hash,
                        ExtrinsicParam.AMOUNT.paramName,
                        amount.toString()
                    ),
                    ExtrinsicParamLocal(
                        hash,
                        ExtrinsicParam.TRANSFER_TYPE.paramName,
                        ExtrinsicTransferTypes.OUT.name
                    )
                )
            )
        }
    }

    override suspend fun getContacts(query: String): Set<String> =
        db.transactionDao().getContacts(query).toSet()

    override suspend fun hideAssets(assetIds: List<String>) {
        db.assetDao().hideAssets(assetIds)
    }

    override suspend fun displayAssets(assetIds: List<String>) {
        db.assetDao().displayAssets(assetIds)
    }

    override suspend fun updateAssetPositions(assetPositions: Map<String, Int>) {
        db.withTransaction {
            assetPositions.entries.forEach {
                db.assetDao().updateAssetPosition(it.key, it.value)
            }
        }
    }

    override suspend fun getAsset(assetId: String, address: String): Asset? {
        return db.assetDao().getAssetWithToken(address, assetId)?.let {
            assetLocalToAssetMapper.map(it, resourceManager)
        }
    }

    override suspend fun getToken(tokenId: String): Token? {
        return db.assetDao().getTokensByList(listOf(tokenId)).let {
            if (it.isEmpty()) null else assetLocalToAssetMapper.map(it.first(), resourceManager)
        }
    }

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return db.assetDao().getWhitelistOfToken(tokenId).isNullOrEmpty().not()
    }

    private suspend fun checkDefaultAssetData(address: String): List<AssetTokenLocal> {
        val ids = assetHolder.getIds()
        val tokenList = ids.map {
            TokenLocal(
                it,
                assetHolder.getName(it),
                assetHolder.getSymbol(it),
                18,
                false,
                AssetHolder.DEFAULT_WHITE_LIST_NAME,
                assetHolder.isHiding(it)
            )
        }
        val assetList = ids.map {
            AssetLocal(
                it,
                address,
                assetHolder.isDisplay(it),
                assetHolder.position(it),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
        db.assetDao().insertTokenListIgnore(tokenList)
        db.assetDao().insertAssetListReplace(assetList)
        return List(tokenList.size) {
            AssetTokenLocal(assetList[it], tokenList[it])
        }
    }
}
