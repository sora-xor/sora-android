/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.security.KeyPair

@RunWith(MockitoJUnitRunner::class)
class WalletInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var walletRepository: WalletRepository
    @Mock private lateinit var didRepository: DidRepository

    @Mock private lateinit var keyPair: KeyPair

    private lateinit var interactor: WalletInteractor

    private val accountId = "accountId1@sora"
    private val account = Account("fullName", "lastName", "accountId1@sora")
    private val account2 = Account("fullName", "lastName", "accountId2@sora")
    private val query = "query"
    private val balance = BigDecimal.TEN

    @Before fun setUp() {
        given(didRepository.getAccountId()).willReturn(Single.just(accountId))
        given(didRepository.retrieveKeypair()).willReturn(Single.just(keyPair))
        given(walletRepository.findAccount(query)).willReturn(Single.just(mutableListOf(account)))
        given(walletRepository.getWalletBalance(true, accountId, keyPair)).willReturn(Single.just(balance))

        interactor = WalletInteractorImpl(walletRepository, didRepository)
    }

    @Test fun `get account id called`() {
        interactor.getAccountId()
            .test()
            .assertValue(accountId)
    }

    @Test fun `get balance called`() {
        interactor.getBalance(true)
            .test()
            .assertValue(balance)
    }

    @Test fun `get transaction history called`() {
        val transactionCount = 50
        val transaction = Transaction("transactionid", Transaction.Status.PENDING, "assetId", "details", "peername", 10.0, 10000, "peerid", "reason", Transaction.Type.REWARD, 1.0)
        val transaction2 = Transaction("transactionid2", Transaction.Status.PENDING, "assetId", "details", "peername", 10.0, 10000, "peerid", "reason", Transaction.Type.REWARD, 1.0)

        given(walletRepository.getTransactions(true, 0, transactionCount)).willReturn(Single.just(mutableListOf(transaction)))
        given(walletRepository.getTransactions(true, 1, transactionCount)).willReturn(Single.just(mutableListOf(transaction2)))

        interactor.getTransactionHistory(true, false)
            .test()
            .assertResult(mutableListOf(transaction))

        interactor.getTransactionHistory(true, true)
            .test()
            .assertResult(mutableListOf(transaction2))
    }

    @Test fun `find other user accounts called`() {
        val query = "query"
        val account = Account("fullName", "lastName", "accountId@sora")

        given(walletRepository.findAccount(query)).willReturn(Single.just(mutableListOf(account)))

        interactor.findOtherUsersAccounts(query)
            .test()
            .assertResult(mutableListOf(account))
    }

    @Test fun `find other user accounts called with user account`() {
        interactor.findOtherUsersAccounts(query)
            .test()
            .assertResult(mutableListOf())
    }

    @Test fun `get qr code amount string called`() {
        val amount = "10"
        val qrString = "qrString"

        given(walletRepository.getQrAmountString(accountId, amount)).willReturn(Single.just(qrString))

        val actual = interactor.getQrCodeAmountString(amount).blockingGet()

        assertEquals(qrString, actual)
    }

    @Test fun `transfer amount called`() {
        val amount = "10"
        val description = "10"
        val fee = "10"
        val txHash = "abcdefgh"

        given(walletRepository.transfer(amount, accountId, accountId, description, fee, keyPair)).willReturn(Single.just(txHash))

        interactor.transferAmount(amount, accountId, description, fee)
            .test()
            .assertResult(txHash)
    }

    @Test fun `get contacts called`() {
        given(walletRepository.getContacts(true)).willReturn(Single.just(mutableListOf(account)))

        interactor.getContacts(true)
            .test()
            .assertResult(mutableListOf(account))
    }

    @Test fun `withdraw flow called`() {
        val amount = "10"
        val notaryAddress = "0xnotaryaddr"
        val ethAddress = "0xethaddr"
        val feeAddress = "0xfeeaddr"
        val fee = "10"
        given(walletRepository.withdrawEth(amount, accountId, notaryAddress, ethAddress, feeAddress, fee, keyPair)).willReturn(Completable.complete())

        interactor.withdrawFlow(amount, ethAddress, notaryAddress, feeAddress, fee)
            .test()
            .assertComplete()
            .assertNoErrors()
    }

    @Test fun `get balance and withdrawal meta called`() {
        val withdrawalMeta = WithdrawalMeta("providerAccountId", "feeAccountId", 1.0, FeeType.FIXED)

        given(walletRepository.getWithdrawalMeta()).willReturn(Single.just(withdrawalMeta))

        interactor.getBalanceAndWithdrawalMeta()
            .test()
            .assertResult(Pair(balance, withdrawalMeta))
    }

    @Test fun `get balance and transfer meta called`() {
        val transferMeta = TransferMeta(1.0, FeeType.FIXED)

        given(walletRepository.getTransferMeta(true)).willReturn(Single.just(transferMeta))

        interactor.getBalanceAndTransferMeta(true)
            .test()
            .assertResult(Pair(balance, transferMeta))
    }

    @Test fun `process qr called`() {
        val qrData = QrData("accountId2@sora", "amount", "assetId")
        val content = "content"

        given(walletRepository.getQrDataFromString(content)).willReturn(Single.just(qrData))
        given(walletRepository.findAccount(qrData.accountId)).willReturn(Single.just(mutableListOf(account2)))

        interactor.processQr(content)
            .test()
            .assertResult(Pair(BigDecimal.ZERO, account2))
    }

    @Test fun `process qr called with wrong qr data`() {
        val qrData = QrData("accountId5@sora", "amount", "assetId")
        val content = "content"

        given(walletRepository.getQrDataFromString(content)).willReturn(Single.just(qrData))
        given(walletRepository.findAccount(qrData.accountId)).willReturn(Single.just(mutableListOf()))

        interactor.processQr(content)
            .test()
            .assertErrorMessage(ResponseCode.QR_USER_NOT_FOUND.toString())
    }

    @Test fun `process qr called with users qr data`() {
        val qrData = QrData(accountId, "amount", "assetId")
        val content = "content"

        given(walletRepository.getQrDataFromString(content)).willReturn(Single.just(qrData))
        given(walletRepository.findAccount(accountId)).willReturn(Single.just(mutableListOf(account)))

        interactor.processQr(content)
            .test()
            .assertErrorMessage(ResponseCode.SENDING_TO_MYSELF.toString())
    }
}