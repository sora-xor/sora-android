/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.DepositTransactionDao
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.dao.WithdrawTransactionDao
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetToAssetLocalMapper
import jp.co.soramitsu.feature_wallet_impl.data.network.TransactionFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.network.model.AccountRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.model.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransactionHistoryResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransferMetaResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.UserFindResponse
import jp.co.soramitsu.feature_wallet_impl.data.qr.QrDataRecord
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.mock
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.security.KeyPair

@RunWith(MockitoJUnitRunner::class)
class WalletRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var api: WalletNetworkApi
    @Mock private lateinit var datasource: PrefsWalletDatasource
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionDao: TransferTransactionDao
    @Mock private lateinit var withdrawTransactionDao: WithdrawTransactionDao
    @Mock private lateinit var depositTransactionDao: DepositTransactionDao
    @Mock private lateinit var serializer: Serializer
    @Mock private lateinit var appLinksProvider: AppLinksProvider
    @Mock private lateinit var transactionFactory: TransactionFactory
    @Mock private lateinit var assetHolder: AssetHolder
    @Mock private lateinit var assetLocalToAssetMapper: AssetLocalToAssetMapper
    @Mock private lateinit var assetToAssetLocalMapper: AssetToAssetLocalMapper

    private lateinit var walletRepository: WalletRepositoryImpl
    private val emptyJson = "{}"
    private val assetId = "xor#sora"
    private val myAddress = "myaddress"

    @Before fun setUp() {
        walletRepository = WalletRepositoryImpl(api, datasource, db, serializer, appLinksProvider, transactionFactory, assetHolder,
            assetLocalToAssetMapper, assetToAssetLocalMapper)
    }

    @Test fun `get transactions called`() {
        val transactionsLocal = mutableListOf(
            TransferTransactionLocal(
                "transactionId",
                TransferTransactionLocal.Status.COMMITTED,
                AssetHolder.SORA_XOR.id,
                "details",
                "myAddress",
                "peerName",
                BigDecimal.TEN,
                1000,
                "peerId",
                "reason",
                TransferTransactionLocal.Type.OUTGOING,
                BigDecimal.TEN
            ),
            TransferTransactionLocal(
                "transactionId",
                TransferTransactionLocal.Status.COMMITTED,
                AssetHolder.SORA_XOR_ERC_20.id,
                "details",
                "myAddress",
                "peerName",
                BigDecimal.TEN,
                1000,
                "peerId",
                "reason",
                TransferTransactionLocal.Type.OUTGOING,
                BigDecimal.TEN
            )
        )
        val transactions = mutableListOf(
            Transaction(
                "",
                "transactionId",
                Transaction.Status.COMMITTED,
                AssetHolder.SORA_XOR.id,
                "myAddress",
                "details",
                "peerName",
                BigDecimal.TEN,
                1000,
                "peerId",
                "reason",
                Transaction.Type.OUTGOING,
                BigDecimal.ZERO,
                BigDecimal.TEN
            ),
            Transaction(
                "transactionId",
                "",
                Transaction.Status.COMMITTED,
                AssetHolder.SORA_XOR_ERC_20.id,
                "myAddress",
                "details",
                "peerName",
                BigDecimal.TEN,
                1000,
                "peerId",
                "reason",
                Transaction.Type.OUTGOING,
                BigDecimal.TEN,
                BigDecimal.ZERO
            )
        )
        given(db.transactionDao()).willReturn(transactionDao)
        given(db.withdrawTransactionDao()).willReturn(withdrawTransactionDao)
        given(db.depositTransactionDao()).willReturn(depositTransactionDao)
        given(transactionDao.getTransactions()).willReturn(Observable.just(transactionsLocal))

        walletRepository.getTransactions(myAddress, myAddress)
            .test()
            .assertResult(transactions)
    }

    @Test fun `fetch remote transactions called`() {
//        val pageSize = 8
//        val offset = 0
//
//        val transactionsRemote = mutableListOf(
//            TransactionRemote(
//                "transactionId",
//                TransactionRemote.Status.COMMITTED,
//                "assetId",
//                "details",
//                "peerName",
//                BigDecimal.TEN,
//                1000,
//                "peerId",
//                "reason",
//                TransactionRemote.Type.REWARD,
//                BigDecimal.ONE
//            )
//        )
//
//        val transactions = mutableListOf(
//            Transaction(
//                "",
//                "transactionId",
//                Transaction.Status.COMMITTED,
//                "assetId",
//                "myAddress",
//                "details",
//                BigDecimal.TEN,
//                1000,
//                "peerId",
//                "reason",
//                Transaction.Type.REWARD,
//                BigDecimal.ONE
//            )
//        )
//        given(api.getTransactions(offset, pageSize)).willReturn(Single.just(GetTransactionHistoryResponse(StatusDto("Ok", ""), transactionsRemote)))
//
//        walletRepository.fetchRemoteTransactions(pageSize, offset)
//            .test()
//            .assertResult(transactions.size)
//
//        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `transfer called`() {
        val amount = "1"
        val myAccountId = "accountId"
        val dstUserId = "dstUserId"
        val description = "description"
        val fee = "1"
        val keyPair = mock(KeyPair::class.java)
        val result = mock(Pair::class.java)
        val irohaRequest = mock(IrohaRequest::class.java)
        val txHash = "txHash"
        given(transactionFactory.buildTransferWithFeeTransaction(amount, myAccountId, dstUserId, description, fee, keyPair)).willReturn(Single.just(result as Pair<IrohaRequest, String>))
        given(result.first).willReturn(irohaRequest)
        given(result.second).willReturn(txHash)
        given(api.transferXor(irohaRequest)).willReturn(Single.just(BaseResponse(StatusDto("200", "Ok"))))

        walletRepository.transfer(amount, myAccountId, dstUserId, description, fee, keyPair)
            .test()
            .assertResult(txHash)
    }

    @Test fun `find account called`() {
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        val accountsRemote = mutableListOf(AccountRemote("firstName", "lastName", "accountId"))
        given(api.findUser(accounts[0].accountId)).willReturn(Single.just(UserFindResponse(StatusDto("Ok", ""), accountsRemote)))

        walletRepository.findAccount(accounts[0].accountId)
            .test()
            .assertResult(accounts)
    }

    @Test fun `get contacts called`() {
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        given(datasource.retrieveContacts()).willReturn(accounts)

        walletRepository.getContacts(false)
            .test()
            .assertResult(accounts)
    }

    @Test fun `get contacts called if updatecached`() {
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        val accountsRemote = mutableListOf(AccountRemote("firstName", "lastName", "accountId"))
        given(api.getContacts()).willReturn(Single.just(UserFindResponse(StatusDto("Ok", ""), accountsRemote)))

        walletRepository.getContacts(true)
            .test()
            .assertResult(accounts)

        verify(datasource).saveContacts(accounts)
    }

    @Test fun `get qr amount string called`() {
        val accountId = "accountId"
        val amount = "100"
        given(serializer.serialize(QrDataRecord(accountId, amount))).willReturn(emptyJson)

        walletRepository.getQrAmountString(accountId, amount)
            .test()
            .assertResult(emptyJson)
    }

    @Test fun `get qr amount string called with amount null`() {
        val accountId = "accountId"
        val amount = ""
        given(serializer.serialize(QrDataRecord(accountId, null))).willReturn(emptyJson)

        walletRepository.getQrAmountString(accountId, amount)
            .test()
            .assertResult(emptyJson)
    }

    @Test fun `get qr data from string called`() {
        val qrDataRecord = QrDataRecord("accountId", "100")
        val qrData = QrData(qrDataRecord.accountId, qrDataRecord.amount, qrDataRecord.assetId)
        given(serializer.deserialize(emptyJson, QrDataRecord::class.java)).willReturn(qrDataRecord)

        walletRepository.getQrDataFromString(emptyJson)
            .test()
            .assertResult(qrData)
    }

    @Test fun `get transfer meta called`() {
        val feeRate = "1.0"
        val feeType = FeeType.FIXED
        val transferMeta = TransferMeta(feeRate.toDouble(), feeType)
        given(datasource.observeTransferMeta()).willReturn(Observable.just(transferMeta))

        walletRepository.getTransferMeta()
            .test()
            .assertResult(transferMeta)
    }

    @Test fun `get transfer meta called with update cached`() {
        val feeRate = 1.0
        val feeType = FeeType.FIXED
        val transferMeta = TransferMeta(feeRate, feeType)
        given(api.getTransferMeta(assetId)).willReturn(Single.just(GetTransferMetaResponse(StatusDto("Ok", ""), feeRate, feeType)))

        walletRepository.updateTransferMeta()
            .test()
            .assertComplete()

        verify(datasource).saveTransferMeta(transferMeta)
    }

    @Test fun `get block chain explorer url called`() {
        val txHash = "txHash"
        val blockExplorerUrl = "url"
        given(appLinksProvider.blockChainExplorerUrl).willReturn(blockExplorerUrl)

        walletRepository.getBlockChainExplorerUrl(txHash)
            .test()
            .assertResult(blockExplorerUrl + txHash)

        verify(appLinksProvider).blockChainExplorerUrl
    }
}
