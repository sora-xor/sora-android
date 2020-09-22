package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.security.KeyPair

@RunWith(MockitoJUnitRunner::class)
class WalletInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var walletRepository: WalletRepository
    @Mock private lateinit var ethRepository: EthereumRepository
    @Mock private lateinit var accountSettings: AccountSettings

    @Mock private lateinit var keyPair: KeyPair

    private lateinit var interactor: WalletInteractor

    private val accountId = "accountId1@sora"
    private val account = Account("fullName", "lastName", "accountId1@sora")
    private val account2 = Account("fullName", "lastName", "accountId2@sora")
    private val query = "query"
    private val myAddress = "myAddress"
    private val balance = BigDecimal.TEN

    @Before fun setUp() {
        given(accountSettings.getAccountId()).willReturn(Single.just(accountId))
        given(accountSettings.getKeyPair()).willReturn(Single.just(keyPair))
        given(walletRepository.findAccount(query)).willReturn(Single.just(mutableListOf(account)))

        interactor = WalletInteractorImpl(walletRepository, ethRepository, accountSettings)
    }

    @Test fun `get account id called`() {
        interactor.getAccountId()
            .test()
            .assertValue(accountId)
    }

    @Test fun `get transaction history called`() {
        val transaction = Transaction("", "transactionid", Transaction.Status.PENDING, "assetId", "myAddress", "details", "peername", BigDecimal.TEN, 10000, "peerid", "reason", Transaction.Type.REWARD, BigDecimal.ZERO, BigDecimal.ONE)
        val mnemonic = "mnemonic"
        val ethereumCredentials = mock(EthereumCredentials::class.java)

        given(walletRepository.getTransactions(myAddress, myAddress)).willReturn(Observable.just(mutableListOf(transaction)))
        given(accountSettings.getAccountId()).willReturn(Single.just(myAddress))
        given(accountSettings.mnemonic()).willReturn(Single.just(mnemonic))
        given(ethRepository.getEthCredentials(mnemonic)).willReturn(Single.just(ethereumCredentials))
        given(ethRepository.getEthWalletAddress(ethereumCredentials)).willReturn(Single.just(myAddress))

        interactor.getTransactions()
            .test()
            .assertResult(mutableListOf(transaction))
    }

    @Test fun `update transactions called`() {
        val pageSize = 8

        given(accountSettings.getAccountId()).willReturn(Single.just(accountId))
        given(walletRepository.fetchRemoteTransactions(pageSize, 0, accountId)).willReturn(Single.just(pageSize))

        interactor.updateTransactions(pageSize)
            .test()
            .assertResult(pageSize)
    }

    @Test fun `load more transactions called`() {
        val pageSize = 8
        val offset = 3

        given(accountSettings.getAccountId()).willReturn(Single.just(accountId))
        given(walletRepository.fetchRemoteTransactions(pageSize, offset, accountId)).willReturn(Single.just(pageSize))

        interactor.loadMoreTransactions(pageSize, offset)
            .test()
            .assertResult(pageSize)
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
        val expected = Pair(txHash, accountId)

        given(walletRepository.transfer(amount, accountId, accountId, description, fee, keyPair)).willReturn(Single.just(txHash))

        interactor.transferAmount(amount, accountId, description, fee)
            .test()
            .assertResult(expected)
    }

    @Test fun `get contacts called`() {
        given(walletRepository.getContacts(true)).willReturn(Single.just(mutableListOf(account)))

        interactor.getContacts(true)
            .test()
            .assertResult(mutableListOf(account))
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
            .assertError { it is QrException && it.kind == QrException.Kind.USER_NOT_FOUND }
    }

    @Test fun `process qr called with users qr data`() {
        val qrData = QrData(accountId, "amount", "assetId")
        val content = "content"

        given(walletRepository.getQrDataFromString(content)).willReturn(Single.just(qrData))
        given(walletRepository.findAccount(accountId)).willReturn(Single.just(mutableListOf(account)))

        interactor.processQr(content)
            .test()
            .assertError { it is QrException && it.kind == QrException.Kind.SENDING_TO_MYSELF }
    }
}