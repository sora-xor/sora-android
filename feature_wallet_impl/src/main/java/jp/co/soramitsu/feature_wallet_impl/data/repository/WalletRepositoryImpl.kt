/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.annotation.WorkerThread
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.AssetInfoDto
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.substrate.Events
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEvent
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetToAssetLocalMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject
import kotlin.math.pow

class WalletRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val serializer: Serializer,
    private val appLinksProvider: AppLinksProvider,
    private val assetHolder: AssetHolder,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val assetToAssetLocalMapper: AssetToAssetLocalMapper
) : WalletRepository {

    private val assetsCache = mutableListOf<Asset>()

    override fun saveMigrationStatus(migrationStatus: MigrationStatus): Completable {
        return Completable.fromCallable { datasource.saveMigrationStatus(migrationStatus) }
    }

    override fun observeMigrationStatus(): Observable<MigrationStatus> {
        return datasource.observeMigrationStatus()
    }

    override fun retrieveClaimBlockAndTxHash(): Single<Pair<String, String>> {
        return Single.fromCallable { datasource.retrieveClaimBlockAndTxHash() }
    }

    override fun needsMigration(irohaAddress: String): Single<Boolean> {
        return wsConnection.needsMigration(irohaAddress)
    }

    override fun unwatch(subscription: String): Completable =
        wsConnection.unwatchExtrinsic(subscription)

    override fun checkEvents(blockHash: String): Single<List<BlockEvent>> {
        return wsConnection.checkEvents(RuntimeHolder.getRuntime(), blockHash).map { list ->
            list.map {
                BlockEvent(
                    it.event.moduleIndex,
                    it.event.eventIndex,
                    (it.phase as? PhaseRecord.ApplyExtrinsic)?.extrinsicId?.toLong()
                )
            }
        }
    }

    override fun getBlock(blockHash: String): Single<BlockResponse> {
        return wsConnection.getBlock(blockHash)
    }

    override fun isTxSuccessful(
        extrinsicId: Long,
        blockHash: String,
        txHash: String
    ): Single<Boolean> {
        return checkEvents(blockHash)
            .map { events ->

                if (events.isEmpty()) {
                    FirebaseCrashlytics.getInstance()
                        .recordException(Throwable("No success or failed evennt in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
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
                        FirebaseCrashlytics.getInstance()
                            .recordException(Throwable("No success or failed evennt in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
                        false
                    }
                }
            }
    }

    override fun transfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): Single<String> {
        return getAssets(from).flatMap { assets ->
            val precision = requireNotNull(assets.find { it.id == assetId }).precision
            wsConnection.transfer(
                keypair,
                from,
                to,
                assetId,
                mapBalance(amount, precision),
                RuntimeHolder.getRuntime()
            )
        }
    }

    override fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Keypair,
        address: String
    ): Observable<Pair<String, ExtrinsicStatusResponse>> {
        return wsConnection.migrate(
            irohaAddress,
            irohaPublicKey,
            signature,
            keypair,
            RuntimeHolder.getRuntime(),
            address
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

    override fun observeTransfer(
        keypair: Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Observable<Pair<String, ExtrinsicStatusResponse>> {
        return getAssets(from).flatMapObservable { assets ->
            val precision = requireNotNull(assets.find { it.id == assetId }).precision
            wsConnection.observeTransfer(
                keypair,
                from,
                to,
                assetId,
                mapBalance(amount, precision),
                RuntimeHolder.getRuntime()
            )
        }
    }

    override fun calcTransactionFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): Single<BigDecimal> {
        return getAssets(from).flatMap { assets ->
            val precision = requireNotNull(assets.find { it.id == assetId }).precision
            wsConnection.calcFee(
                from,
                to,
                assetId,
                mapBalance(amount, precision),
                RuntimeHolder.getRuntime()
            )
                .map { mapBalance(it, precision) }
        }
    }

    override fun getAssets(
        address: String,
        forceUpdateBalances: Boolean,
        forceUpdateAssets: Boolean
    ): Single<List<Asset>> {
        return if (!forceUpdateAssets && !forceUpdateBalances && assetsCache.isNotEmpty())
            Single.just(assetsCache)
        else db.assetDao().getAll().toSingle(emptyList())
            .map {
                it.map { assetLocal ->
                    assetLocalToAssetMapper.map(assetLocal, assetHolder)
                }
            }
            .map { dbAssets ->
                if (!forceUpdateAssets && !forceUpdateBalances && dbAssets.isNotEmpty()) {
                    assetsCache.clear()
                    assetsCache.addAll(dbAssets)
                    dbAssets
                } else {
                    val curAssets = if (forceUpdateAssets || dbAssets.isEmpty()) {
                        fetchAssetList()
                            .filter { dto -> assetHolder.isKnownAsset(dto.id) }
                            .map { dto ->
                                val dbAsset = dbAssets.find { a -> dto.id == a.id }
                                Asset(
                                    id = dto.id,
                                    assetName = dto.name,
                                    symbol = dto.symbol,
                                    display = dbAsset?.display ?: assetHolder.isDisplay(dto.id),
                                    hidingAllowed = assetHolder.isHiding(dto.id),
                                    position = dbAsset?.position ?: assetHolder.position(dto.id),
                                    roundingPrecision = assetHolder.rounding(dto.id),
                                    precision = dto.precision,
                                    balance = dbAsset?.balance ?: BigDecimal.ZERO,
                                    iconShadow = assetHolder.iconShadow(dto.id),
                                    isMintable = dto.isMintable,
                                )
                            }
                    } else {
                        dbAssets
                    }
                    val curBalances = if (forceUpdateAssets || forceUpdateBalances) {
                        fetchBalances(address, curAssets.map { it.id })
                            .mapIndexed { index, bigInteger ->
                                curAssets[index].copy(balance = mapBalance(bigInteger, curAssets[index].precision, curAssets[index].balance))
                            }
                    } else {
                        curAssets
                    }
                    curBalances
                }
            }
            .onErrorReturn { listOf() }
            .doOnSuccess {
                assetsCache.clear()
                assetsCache.addAll(it)
                db.assetDao().insert(it.map { a -> assetToAssetLocalMapper.map(a) })
            }
    }

    @WorkerThread
    private fun fetchAssetList(): List<AssetInfoDto> {
        return wsConnection.fetchAssetList(RuntimeHolder.getRuntime()).blockingGet()
    }

    @WorkerThread
    private fun fetchBalances(
        address: String,
        assetIdList: List<String>
    ): List<BigInteger> {
        return assetIdList.map {
            wsConnection.fetchBalance(address, it).blockingGet()
        }
    }

    private fun mapBalance(
        bigInteger: BigInteger?,
        precision: Int,
        default: BigDecimal? = null
    ): BigDecimal =
        bigInteger?.toBigDecimal()?.divide(BigDecimal(10.0.pow(precision)))
            ?: default ?: BigDecimal.ZERO

    private fun mapBalance(balance: BigDecimal, precision: Int): BigInteger =
        balance.multiply(BigDecimal(10.0.pow(precision))).toBigInteger()

    override fun updateTransactionSuccess(hash: String, success: Boolean) {
        db.transactionDao().updateSuccess(hash, success)
    }

    override fun saveTransaction(
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal,
        status: ExtrinsicStatusResponse,
        hash: String,
        fee: BigDecimal,
        eventSuccess: Boolean?
    ): Long {
        return db.transactionDao().insert(
            TransferTransactionLocal(
                hash,
                status.let {
                    when (it) {
                        is ExtrinsicStatusResponse.ExtrinsicStatusPending -> TransferTransactionLocal.Status.PENDING
                        is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> TransferTransactionLocal.Status.COMMITTED
                    }
                },
                assetId,
                from, amount, Date().time, to,
                TransferTransactionLocal.Type.OUTGOING, fee,
                (status as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                eventSuccess,
            )
        )
    }

    override fun getTransactions(
        myAddress: String,
        myEthAddress: String
    ): Observable<List<Transaction>> {
        return db.transactionDao().getTransactions()
            .map { list ->
                list.map { mapTransactionLocalToTransaction(it) }
            }
    }

    override fun getContacts(query: String): Single<Set<String>> =
        db.transactionDao().getContacts(query).map { it.toSet() }

    override fun hideAssets(assetIds: List<String>): Completable {
        return Completable.fromAction {
            db.assetDao().hideAssets(assetIds)
            val newList = assetsCache.map {
                if (it.id in assetIds) it.copy(display = false) else it
            }
            assetsCache.clear()
            assetsCache.addAll(newList)
        }
    }

    override fun displayAssets(assetIds: List<String>): Completable {
        return Completable.fromAction {
            db.assetDao().displayAssets(assetIds)
            val newList = assetsCache.map {
                if (it.id in assetIds) it.copy(display = true) else it
            }
            assetsCache.clear()
            assetsCache.addAll(newList)
        }
    }

    override fun updateAssetPositions(assetPositions: Map<String, Int>): Completable {
        return Completable.fromAction {
            db.runInTransaction {
                assetPositions.entries.forEach {
                    db.assetDao().updateAssetPosition(it.key, it.value)
                }
            }
        }
    }
}
