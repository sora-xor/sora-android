/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import jp.co.soramitsu.feature_wallet_impl.data.network.WalletNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.network.model.AccountRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.model.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransactionHistoryResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetTransferMetaResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.GetWithdrawalMetaResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.response.UserFindResponse
import jp.co.soramitsu.feature_wallet_impl.data.qr.QrDataRecord
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
    @Mock private lateinit var transactionDao: TransactionDao
    @Mock private lateinit var serializer: Serializer

    private lateinit var walletRepository: WalletRepositoryImpl
    private val emptyJson = "{}"

    @Before fun setUp() {
        walletRepository = WalletRepositoryImpl(api, datasource, db, serializer)
    }

    @Test fun `get transactions called`() {
        val transactionsLocal = mutableListOf(
            TransactionLocal(
                "transactionId",
                TransactionLocal.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                TransactionLocal.Type.REWARD,
                1.0
            )
        )
        val transactions = mutableListOf(
            Transaction(
                "transactionId",
                Transaction.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                Transaction.Type.REWARD,
                1.0
            )
        )
        given(db.transactionDao()).willReturn(transactionDao)
        given(transactionDao.getTransactions()).willReturn(Single.just(transactionsLocal))

        walletRepository.getTransactions(false, 0, 3)
            .test()
            .assertResult(transactions)
    }

    @Test fun `get transactions called if update cached`() {
        val transactionsLocal = mutableListOf(
            TransactionLocal(
                "transactionId",
                TransactionLocal.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                TransactionLocal.Type.REWARD,
                1.0
            )
        )
        val transactionsRemote = mutableListOf(
            TransactionRemote(
                "transactionId",
                TransactionRemote.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                TransactionRemote.Type.REWARD,
                1.0
            )
        )
        val transactions = mutableListOf(
            Transaction(
                "transactionId",
                Transaction.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                Transaction.Type.REWARD,
                1.0
            )
        )
        given(db.transactionDao()).willReturn(transactionDao)
        given(api.getTransactions(0, 3)).willReturn(Single.just(GetTransactionHistoryResponse(StatusDto("Ok", ""), transactionsRemote)))

        walletRepository.getTransactions(true, 0, 3)
            .test()
            .assertResult(transactions)

        verify(transactionDao).insert(transactionsLocal)
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

    @Test fun `get last transaction details called`() {
        val transactionsRemote = mutableListOf(
            TransactionRemote(
                "transactionId",
                TransactionRemote.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                TransactionRemote.Type.REWARD,
                1.0
            )
        )
        val transactions = mutableListOf(
            Transaction(
                "transactionId",
                Transaction.Status.COMMITTED,
                "assetId",
                "details",
                "peerName",
                10.0,
                1000,
                "peerId",
                "reason",
                Transaction.Type.REWARD,
                1.0
            )
        )
        given(api.getTransactions(0, 1)).willReturn(Single.just(GetTransactionHistoryResponse(StatusDto("Ok", ""), transactionsRemote)))

        walletRepository.getLastTransactionDetails()
            .test()
            .assertResult(transactions.first())
    }

    @Test fun `get wallet balance called`() {
        val keyPair = mock(KeyPair::class.java)
        val assets = arrayOf(Asset(BigDecimal.TEN, "xor#sora"))
        given(datasource.retrieveBalance()).willReturn(assets)

        walletRepository.getWalletBalance(false, "", keyPair)
            .test()
            .assertResult(assets[0].balance)
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

    @Test fun `get withdrawal meta called`() {
        val assetId = "xor#sora"
        val eth = "ETH"
        val providerAccountId = "providerAccountId"
        val feeAccountId = "feeAccountId"
        val feeRate = "1.0"
        val feeType = FeeType.FIXED
        val withdrawalMeta = WithdrawalMeta(providerAccountId, feeAccountId, feeRate.toDouble(), feeType)
        given(api.getWithdrawalMeta(assetId, eth)).willReturn(Single.just(GetWithdrawalMetaResponse(StatusDto("Ok", ""), providerAccountId, feeAccountId, feeRate, feeType)))

        walletRepository.getWithdrawalMeta()
            .test()
            .assertResult(withdrawalMeta)
    }

    @Test fun `get transfer meta called`() {
        val feeRate = "1.0"
        val feeType = FeeType.FIXED
        val transferMeta = TransferMeta(feeRate.toDouble(), feeType)
        given(datasource.retrieveTransferMeta()).willReturn(transferMeta)

        walletRepository.getTransferMeta(false)
            .test()
            .assertResult(transferMeta)
    }

    @Test fun `get transfer meta called with update cached`() {
        val assetId = "xor#sora"
        val feeRate = 1.0
        val feeType = FeeType.FIXED
        val transferMeta = TransferMeta(feeRate, feeType)
        given(api.getTransferMeta(assetId)).willReturn(Single.just(GetTransferMetaResponse(StatusDto("Ok", ""), feeRate, feeType)))

        walletRepository.getTransferMeta(true)
            .test()
            .assertResult(transferMeta)

        verify(datasource).saveTransferMeta(transferMeta)
    }
}
