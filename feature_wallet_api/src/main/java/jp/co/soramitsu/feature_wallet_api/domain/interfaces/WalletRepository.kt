package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawTransaction
import java.security.KeyPair

interface WalletRepository {

    fun getAssets(): Observable<List<Asset>>

    fun updateAssets(accountSettings: AccountSettings): Completable

    fun getTransactions(myAddress: String, myEthAddress: String): Observable<List<Transaction>>

    fun fetchRemoteTransactions(pageSize: Int, offset: Int, accountId: String): Single<Int>

    fun transfer(amount: String, myAccountId: String, dstUserId: String, description: String, fee: String, keyPair: KeyPair): Single<String>

    fun findAccount(search: String): Single<List<Account>>

    fun getContacts(updateCached: Boolean): Single<List<Account>>

    fun getBalance(assetId: String): Observable<AssetBalance>

    fun getTransferMeta(): Observable<TransferMeta>

    fun getWithdrawMeta(): Observable<TransferMeta>

    fun updateTransferMeta(): Completable

    fun updateWithdrawMeta(): Completable

    fun getQrAmountString(accountId: String, amount: String): Single<String>

    fun getQrDataFromString(content: String): Single<QrData>

    fun getBlockChainExplorerUrl(transactionHash: String): Single<String>

    fun getTransaction(operationId: String): Single<Transaction>

    fun getWithdrawTransaction(operationId: String): WithdrawTransaction?

    fun saveWithdrawTransaction(operationId: String, amount: Double, peerId: String, peerName: String, timestamp: Long, details: String, fee: Double)

    fun updateWithdrawTransactionStatus(operationId: String, status: WithdrawTransaction.Status): Completable

    fun updateWithdrawTransactionConfirmHash(operationId: String, confirmTxHash: String): Completable

    fun finishDepositTransaction(operationId: String, myAccountId: String, keyPair: KeyPair): Completable

    fun hideAssets(assetIds: List<String>): Completable

    fun displayAssets(assetIds: List<String>): Completable

    fun updateAssetPositions(assetPositions: Map<String, Int>): Completable
}