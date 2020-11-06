package jp.co.soramitsu.feature_wallet_impl.data.repository

import com.google.gson.JsonSyntaxException
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.DepositTransactionLocal
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetToAssetLocalMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAccountRemoteToAccount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapDepositTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapQrDataRecordToQrData
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionRemoteToDepositTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionRemoteToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionRemoteToWithdrawTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapWithdrawTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapWithdrawTransactionLocalToWithdrawTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapWithdrawTransactionStatusToWithdrawTransactionStatusLocal
import jp.co.soramitsu.feature_wallet_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.network.model.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.request.GetBalanceRequest
import jp.co.soramitsu.feature_wallet_impl.data.qr.QrDataRecord
import org.spongycastle.util.encoders.Base64
import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor(
    private val api: WalletNetworkApi,
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val serializer: Serializer,
    private val appLinksProvider: AppLinksProvider,
    private val transactionFactory: TransactionFactory,
    private val assetHolder: AssetHolder,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val assetToAssetLocalMapper: AssetToAssetLocalMapper
) : WalletRepository {

    override fun getAssets(): Observable<List<Asset>> {
        return db.assetDao().getAll()
            .doOnNext {
                if (it.isEmpty()) {
                    db.assetDao().insert(assetHolder.getAssets().map { assetToAssetLocalMapper.map(it) })
                } else {
                    Observable.just(it)
                }
            }
            .filter { it.isNotEmpty() }
            .map { it.map { assetLocalToAssetMapper.map(it) } }
    }

    override fun updateAssets(accountSettings: AccountSettings): Completable {
        return getWalletBalanceRemote(accountSettings)
            .ignoreElement()
    }

    override fun getTransactions(myAddress: String, myEthAddress: String): Observable<List<Transaction>> {
        return db.transactionDao().getTransactions()
            .map {
                val withdrawTxHashes = it.map { it.txHash }
                val withdrawTxs = db.withdrawTransactionDao().getTransactionsByIntentHashes(withdrawTxHashes)

                val depositTxHashes = it.map { "0x${it.details}" }
                val depositTxs = db.depositTransactionDao().getTransactionsByDepositHashes(depositTxHashes)
                Triple(it, withdrawTxs, depositTxs)
            }
            .map { pair ->
                pair.first.map { transferTx ->
                    when (transferTx.type) {
                        TransferTransactionLocal.Type.WITHDRAW -> {
                            val tx = pair.second.first { it.intentTxHash == transferTx.txHash }
                            mapWithdrawTransactionLocalToTransaction(tx, myAddress, myEthAddress)
                        }

                        TransferTransactionLocal.Type.DEPOSIT -> {
                            val tx = pair.third.first { it.depositTxHash == "0x${transferTx.details}" }
                            mapDepositTransactionLocalToTransaction(tx, myAddress, myEthAddress)
                        }

                        else -> mapTransactionLocalToTransaction(transferTx)
                    }
                }
            }
            .map { it.sortedByDescending { it.timestamp } }
    }

    override fun fetchRemoteTransactions(pageSize: Int, offset: Int, accountId: String): Single<Int> {
        return api.getTransactions(offset, pageSize)
            .map {
                val withdrawTxs = mutableListOf<WithdrawTransactionLocal>()
                val depositTxs = mutableListOf<DepositTransactionLocal>()
                val transferTxs = mutableListOf<TransferTransactionLocal>()

                it.transactions.forEach {
                    transferTxs.add(mapTransactionRemoteToTransactionLocal(it, accountId))
                    if (it.type == TransactionRemote.Type.WITHDRAW) {
                        withdrawTxs.add(mapTransactionRemoteToWithdrawTransactionLocal(it))
                    }

                    if (it.type == TransactionRemote.Type.DEPOSIT) {
                        db.transactionDao().updateTxHash("0x${it.details}", it.transactionId)
                        depositTxs.add(mapTransactionRemoteToDepositTransactionLocal(it))
                    }
                }

                Triple(withdrawTxs, depositTxs, transferTxs)
            }
            .map {
                db.runInTransaction {
                    db.withdrawTransactionDao().insertWithoutReplacing(it.first)
                    db.depositTransactionDao().insertWithoutReplacing(it.second)
                    db.transactionDao().insert(it.third)
                }
                it.first.size
            }
    }

    override fun transfer(amount: String, myAccountId: String, dstUserId: String, description: String, fee: String, keyPair: KeyPair): Single<String> {
        return transactionFactory.buildTransferWithFeeTransaction(amount, myAccountId, dstUserId, description, fee, keyPair)
            .flatMap { pair ->
                api.transferVal(pair.first).map { pair }
            }.flatMap {
                Single.just(it.second)
            }
    }

    override fun findAccount(search: String): Single<List<Account>> {
        return api.findUser(search)
            .map { it.results.map { mapAccountRemoteToAccount(it) } }
    }

    override fun getContacts(updateCached: Boolean): Single<List<Account>> {
        return if (updateCached) {
            getRemoteContacts()
        } else {
            val localContacts = datasource.retrieveContacts()
            return if (localContacts == null) getRemoteContacts() else Single.just(localContacts)
        }
    }

    private fun getRemoteContacts(): Single<List<Account>> {
        return api.getContacts()
            .map { it.results.map { mapAccountRemoteToAccount(it) } }
            .doOnSuccess { datasource.saveContacts(it) }
    }

    override fun getBalance(assetId: String): Observable<AssetBalance> {
        return db.assetDao().getAsset(assetId)
            .filter { it.balance != null }
            .map { AssetBalance(it.id, it.balance!!) }
    }

    private fun getWalletBalanceRemote(accountSettings: AccountSettings): Maybe<List<AssetBalance>> {
        return accountSettings.getKeyPair()
            .flatMap { keyPair ->
                accountSettings.getAccountId()
                    .flatMap { accountId ->
                        buildGetBalanceRequest(accountId, keyPair)
                            .flatMap { api.getBalance(it) }
                            .map {
                                val asset = AssetHolder.SORA_VAL
                                it.assets.map { AssetBalance(asset.id, it.balance) }
                            }
                            .doOnSuccess {
                                db.runInTransaction {
                                    it.forEach { db.assetDao().updateBalance(it.assetId, it.balance) }
                                }
                            }
                    }
            }
            .toMaybe()
    }

    private fun buildGetBalanceRequest(accountId: String, keyPair: KeyPair): Single<GetBalanceRequest> {
        return transactionFactory.buildGetAccountAssetsQuery(accountId, keyPair)
            .map { GetBalanceRequest(arrayOf(AssetHolder.SORA_VAL.id), Base64.toBase64String(it.toByteArray())) }
    }

    override fun getQrAmountString(accountId: String, amount: String): Single<String> {
        return Single.just(serializer.serialize(QrDataRecord(accountId, if (amount.isEmpty()) null else amount)))
    }

    override fun getQrDataFromString(content: String): Single<QrData> {
        return Single.create { emitter ->
            try {
                val qrDataRecord = serializer.deserialize(content, QrDataRecord::class.java)
                emitter.onSuccess(mapQrDataRecordToQrData(qrDataRecord))
            } catch (e: JsonSyntaxException) {
                emitter.onError(QrException.decodeError())
            }
        }
    }

    override fun getTransferMeta(): Observable<TransferMeta> {
        return datasource.observeTransferMeta()
    }

    override fun updateTransferMeta(): Completable {
        return api.getTransferMeta(AssetHolder.SORA_VAL.id)
            .map { TransferMeta(it.feeRate, it.feeType) }
            .doOnSuccess { datasource.saveTransferMeta(it) }
            .ignoreElement()
    }

    override fun getWithdrawMeta(): Observable<TransferMeta> {
        return datasource.observeWithdrawMeta()
    }

    override fun updateWithdrawMeta(): Completable {
        return api.getWithdrawalMeta(AssetHolder.SORA_VAL.id, "ETH")
            .map { TransferMeta(it.feeRate.toDouble(), it.feeType) }
            .doOnSuccess { datasource.saveWithdrawMeta(it) }
            .ignoreElement()
    }

    override fun getBlockChainExplorerUrl(transactionHash: String): Single<String> {
        return Single.fromCallable {
            appLinksProvider.blockChainExplorerUrl + transactionHash
        }
    }

    override fun getTransaction(operationId: String): Single<Transaction> {
        return Single.fromCallable {
            mapTransactionLocalToTransaction(db.transactionDao().getTransactionByHash(operationId))
        }
    }

    override fun getWithdrawTransaction(operationId: String): WithdrawTransaction? {
        return db.withdrawTransactionDao().getTransactionByIntentHash(operationId)?.let {
            mapWithdrawTransactionLocalToWithdrawTransaction(it)
        }
    }

    override fun saveWithdrawTransaction(operationId: String, amount: Double, peerId: String, peerName: String, timestamp: Long, details: String, fee: Double) {
        val tx = WithdrawTransactionLocal(operationId, "", "", WithdrawTransactionLocal.Status.INTENT_PENDING,
            "", details, amount.toBigDecimal(), BigDecimal.ZERO, timestamp, details, "", "", fee.toBigDecimal(), BigDecimal.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        db.withdrawTransactionDao().insert(tx)
    }

    override fun updateWithdrawTransactionStatus(operationId: String, status: WithdrawTransaction.Status): Completable {
        return Completable.fromAction {
            db.withdrawTransactionDao().updateStatus(operationId, mapWithdrawTransactionStatusToWithdrawTransactionStatusLocal(status))

            if (status == WithdrawTransaction.Status.CONFIRM_COMPLETED) {
                db.transactionDao().updateStatus(operationId, TransferTransactionLocal.Status.COMMITTED)
            } else if (status == WithdrawTransaction.Status.CONFIRM_FAILED || status == WithdrawTransaction.Status.INTENT_FAILED || status == WithdrawTransaction.Status.TRANSFER_FAILED) {
                db.transactionDao().updateStatus(operationId, TransferTransactionLocal.Status.REJECTED)
            }
        }
    }

    override fun updateWithdrawTransactionConfirmHash(operationId: String, confirmTxHash: String): Completable {
        return Completable.fromAction {
            db.withdrawTransactionDao().updateConfirmTxHash(operationId, confirmTxHash)
        }
    }

    override fun finishDepositTransaction(operationId: String, myAccountId: String, keyPair: KeyPair): Completable {
        return db.depositTransactionDao().getTransaction(operationId)
            .flatMap { transactionFactory.buildTransferWithFeeTransaction(it.amount.toString(), myAccountId, it.peerId!!, it.details, it.transferFee.toString(), keyPair) }
            .flatMap { pair ->
                api.transferVal(pair.first).map { pair }
            }
            .map { db.depositTransactionDao().updateStatus(operationId, DepositTransactionLocal.Status.TRANSFER_COMPLETED) }
            .ignoreElement()
    }

    override fun hideAssets(assetIds: List<String>): Completable {
        return Completable.fromAction {
            db.assetDao().hideAssets(assetIds)
        }
    }

    override fun displayAssets(assetIds: List<String>): Completable {
        return Completable.fromAction {
            db.assetDao().displayAssets(assetIds)
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