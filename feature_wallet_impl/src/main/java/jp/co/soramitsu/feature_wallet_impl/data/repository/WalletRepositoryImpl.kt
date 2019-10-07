/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.ext.toHash
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAccountRemoteToAccount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetRemoteToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapQrDataRecordToQrData
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionRemoteToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.network.request.GetBalanceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.request.TransferXorRequest
import jp.co.soramitsu.feature_wallet_impl.data.qr.QrDataRecord
import jp.co.soramitsu.iroha.java.Query
import org.spongycastle.util.encoders.Base64
import java.math.BigDecimal
import java.security.KeyPair
import java.util.Date
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor(
    private val api: WalletNetworkApi,
    private val datasource: WalletDatasource,
    private val db: AppDatabase
) : WalletRepository {

    companion object {
        private const val ASSET_ID = "xor#sora"
        private const val ETH = "ETH"
        private const val WITHDRAWAL_FEE_DESCRIPTION = "withdrawal fee"
    }

    override fun getTransactions(updateCached: Boolean, offset: Int, count: Int): Single<List<Transaction>> {
        return if (updateCached) {
            api.getTransactions(offset, count)
                .map { it.transactions.map { mapTransactionRemoteToTransaction(it) } }
                .doOnSuccess { db.transactionDao().insert(it.map { mapTransactionToTransactionLocal(it) }) }
        } else {
            db.transactionDao().getTransactions()
                .map { it.map { mapTransactionLocalToTransaction(it) } }
        }
    }

    override fun transfer(
        amount: String,
        myAccountId: String,
        dstUserId: String,
        description: String,
        fee: String,
        keyPair: KeyPair
    ): Single<String> {
        return buildTransferWithFeeXorRequest(amount, myAccountId, dstUserId, description, fee, keyPair)
            .flatMap { pair ->
                api.transferXor(pair.first).map { pair }
            }.flatMap {
                Single.just(it.second)
            }
    }

    private fun buildTransferWithFeeXorRequest(
        amount: String,
        myAccountId: String,
        dstUserId: String,
        description: String,
        fee: String,
        keyPair: KeyPair
    ): Single<Pair<TransferXorRequest, String>> {
        return Single.fromCallable {
            val tx = jp.co.soramitsu.iroha.java.Transaction.builder(myAccountId)
                .transferAsset(myAccountId, dstUserId, ASSET_ID, description, amount)
                .subtractAssetQuantity(ASSET_ID, fee)
                .setQuorum(2)
                .sign(keyPair)
                .build()

            Pair(TransferXorRequest(Base64.toBase64String(tx.toByteArray())), tx.toHash().substring(0, 8))
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

    override fun getLastTransactionDetails(): Single<Transaction> {
        return api.getTransactions(0, 1)
            .map { it.transactions }
            .map { it.map { mapTransactionRemoteToTransaction(it) } }
            .map { it.first() }
    }

    override fun getWalletBalance(updateCached: Boolean, accountId: String, keyPair: KeyPair): Single<BigDecimal> {
        return if (updateCached) {
            getWalletBalanceRemote(accountId, keyPair)
        } else {
            val localAssets = datasource.retrieveBalance()
            if (localAssets == null || localAssets.isEmpty()) {
                getWalletBalanceRemote(accountId, keyPair)
            } else {
                Single.just(localAssets[0].balance!!)
            }
        }
    }

    private fun getWalletBalanceRemote(accountId: String, keyPair: KeyPair): Single<BigDecimal> {
        return buildGetBalanceRequest(accountId, keyPair)
            .flatMap { api.getBalance(it) }
            .map { it.assets.map { mapAssetRemoteToAsset(it) } }
            .doOnSuccess { datasource.saveBalance(it.toTypedArray()) }
            .map { it[0].balance }
    }

    private fun buildGetBalanceRequest(accountId: String, keyPair: KeyPair): Single<GetBalanceRequest> {
        return Single.fromCallable {
            val q = Query.builder(accountId, Date(), 1)
                .getAccountAssets(accountId)
                .buildSigned(keyPair)
            GetBalanceRequest(arrayOf(ASSET_ID), Base64.toBase64String(q.toByteArray()))
        }
    }

    override fun transferD3(): Completable {
        return api.transferXorCsp()
            .ignoreElement()
    }

    override fun transferD3(amount: String, accountId: String, d3AccountId: String, keyPair: KeyPair): Completable {
        return buildTransferXorRequest(amount, accountId, d3AccountId, "", keyPair)
            .flatMap { api.transferXorCsp(it) }
            .ignoreElement()
    }

    private fun buildTransferXorRequest(
        amount: String,
        myAccountId: String,
        dstUserId: String,
        description: String,
        keyPair: KeyPair
    ): Single<TransferXorRequest> {
        return Single.fromCallable {
            val tx = jp.co.soramitsu.iroha.java.Transaction.builder(myAccountId)
                .transferAsset(myAccountId, dstUserId, ASSET_ID, description, amount)
                .setQuorum(2)
                .sign(keyPair)
                .build()
            TransferXorRequest(Base64.toBase64String(tx.toByteArray()))
        }
    }

    override fun withdrawEth(
        amount: String,
        srcAccountId: String,
        notaryAddress: String,
        ethAddress: String,
        feeAddress: String,
        feeAmount: String,
        keyPair: KeyPair
    ): Completable {
        return buildWithdrawTransfer(amount, srcAccountId, notaryAddress, ethAddress, feeAddress, feeAmount, keyPair)
            .flatMap { api.withdrawEth(it) }
            .ignoreElement()
    }

    private fun buildWithdrawTransfer(
        amount: String,
        srcAccountId: String,
        notaryAddress: String,
        ethAddress: String,
        feeAddress: String,
        feeAmount: String,
        keyPair: KeyPair
    ): Single<TransferXorRequest> {
        return Single.fromCallable {
            val txBuilder = jp.co.soramitsu.iroha.java.Transaction.builder(srcAccountId)
                .transferAsset(srcAccountId, notaryAddress, ASSET_ID, ethAddress, amount)

            if (feeAmount.toDouble() != 0.0) {
                txBuilder.transferAsset(srcAccountId, feeAddress, ASSET_ID, WITHDRAWAL_FEE_DESCRIPTION, feeAmount)
            }

            val tx = txBuilder.setQuorum(2)
                .sign(keyPair)
                .build()

            TransferXorRequest(Base64.toBase64String(tx.toByteArray()))
        }
    }

    override fun getQrAmountString(accountId: String, amount: String): Single<String> {
        return Single.just(Gson().toJson(QrDataRecord(accountId, if (amount.isEmpty()) null else amount)))
    }

    override fun getQrDataFromString(content: String): Single<QrData> {
        return Single.create { emitter ->
            try {
                val qrDataRecord = Gson().fromJson(content, QrDataRecord::class.java)

                if (qrDataRecord != null) {
                    emitter.onSuccess(mapQrDataRecordToQrData(qrDataRecord))
                } else {
                    emitter.onError(SoraException.businessError(ResponseCode.QR_ERROR))
                }
            } catch (e: JsonSyntaxException) {
                emitter.onError(SoraException.businessError(ResponseCode.QR_ERROR))
            }
        }
    }

    override fun getWithdrawalMeta(): Single<WithdrawalMeta> {
        return api.getWithdrawalMeta(ASSET_ID, ETH)
            .map {
                WithdrawalMeta(it.providerAccountId, it.feeAccountId
                    ?: it.providerAccountId, it.feeRate.toDouble(), it.feeType)
            }
    }

    override fun getTransferMeta(updateCached: Boolean): Single<TransferMeta> {
        return if (updateCached) {
            getTransferMetaRemote()
        } else {
            val transferMeta = datasource.retrieveTransferMeta()

            if (transferMeta != null) {
                Single.just(transferMeta)
            } else {
                getTransferMetaRemote()
            }
        }
    }

    private fun getTransferMetaRemote(): Single<TransferMeta> {
        return api.getTransferMeta(ASSET_ID)
            .map { TransferMeta(it.feeRate, it.feeType) }
            .doOnSuccess { datasource.saveTransferMeta(it) }
    }
}