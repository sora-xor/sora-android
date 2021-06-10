/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import io.mockk.every
import io.mockk.mockkStatic
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEntry
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.blake2b256String
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyLong
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willDoNothing
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey

@RunWith(MockitoJUnitRunner::class)
class WalletInteractorTest {

    @Rule
    @JvmField
    var schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var ethRepository: EthereumRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var cryptoAssistant: CryptoAssistant

    @Mock
    private lateinit var keyPair: KeyPair

    @Mock
    private lateinit var publicKey: PublicKey

    private lateinit var interactor: WalletInteractor

    private val accountId = "accountId1@sora"
    private val account = Account("fullName", "lastName", "accountId1@sora")
    private val account2 = Account("fullName", "lastName", "accountId2@sora")
    private val query = "query"
    private val myAddress = "myAddress"
    private val balance = BigDecimal.TEN

    @Before
    fun setUp() {
        given(credentialsRepository.getAddress()).willReturn(Single.just(myAddress))
        //mockkStatic("jp.co.soramitsu.feature_wallet_impl.data.network.substrate.ExtrinsicsKt")
        mockkStatic(String::blake2b256String)
        every { "0x112323345".blake2b256String() } returns "blake2b"
        every { "0x35456472".blake2b256String() } returns "blake2b"
        interactor = WalletInteractorImpl(
            walletRepository,
            ethRepository,
            userRepository,
            credentialsRepository,
            cryptoAssistant
        )
    }

    @Test
    fun `needs migration`() {
        given(credentialsRepository.getIrohaAddress()).willReturn(Single.just("irohaAddress"))
        given(walletRepository.needsMigration("irohaAddress")).willReturn(Single.just(true))
        given(userRepository.saveNeedsMigration(anyBoolean())).willReturn(Completable.complete())
        interactor.needsMigration().test().assertResult(true)
    }

    @Test
    fun `get assets`() {
        given(credentialsRepository.getAddress()).willReturn(Single.just("address"))
        given(walletRepository.getAssets("address", true, true)).willReturn(Single.just(assetList()))
        interactor.getAssets(true, true).test().assertResult(assetList())
    }

    @Test
    fun `find other users`() {
        given(credentialsRepository.getAddress()).willReturn(Single.just("address"))
        given(credentialsRepository.isAddressOk(anyString())).willReturn(Single.just(true))
        given(walletRepository.getContacts(anyString())).willReturn(
            Single.just(
                setOf(
                    "contact1",
                    "contact2"
                )
            )
        )
        interactor.findOtherUsersAccounts("use").test().assertResult(accountList())
    }

    @Test
    fun `just transfer`() {
        val kp = Keypair(ByteArray(32), ByteArray(32))
        given(credentialsRepository.getAddress()).willReturn(Single.just("address"))
        given(credentialsRepository.retrieveKeyPair()).willReturn(Single.just(kp))
        given(walletRepository.transfer(kp, "address", "to", "assetId", BigDecimal.ONE)).willReturn(
            Single.just("hash")
        )
        interactor.transfer("to", "assetId", BigDecimal.ONE).test().assertResult("hash")
    }

    @Test
    fun `calc transaction fee`() {
        given(credentialsRepository.getAddress()).willReturn(Single.just("address"))
        given(
            walletRepository.calcTransactionFee(
                "address",
                "to",
                "assetId",
                BigDecimal.ONE
            )
        ).willReturn(
            Single.just(
                BigDecimal.TEN
            )
        )
        interactor.calcTransactionFee("to", "assetId", BigDecimal.ONE).test().assertResult(
            BigDecimal.TEN
        )
    }

    @Test
    fun `migrate extrinsic`() {
        given(credentialsRepository.getClaimSignature()).willReturn(Single.just("signature"))
        given(credentialsRepository.getIrohaAddress()).willReturn(Single.just("irohaAddress"))
        given(credentialsRepository.retrieveIrohaKeyPair()).willReturn(Single.just(keyPair))
        given(keyPair.public).willReturn(publicKey)
        given(publicKey.encoded).willReturn(ByteArray(32) { 1 })
        val kp = Keypair(ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair()).willReturn(Single.just(kp))
        given(
            walletRepository.migrate(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyString()
            )
        ).willReturn(
            Observable.just(
                "hash" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    "sub",
                    "block"
                )
            )
        )
        val br = BlockResponse(
            "justification",
            BlockEntry("header", listOf("0x112323345", "0x35456472"))
        )
        given(walletRepository.getBlock(anyString())).willReturn(Single.just(br))
        given(walletRepository.isTxSuccessful(anyLong(), anyString(), anyString())).willReturn(
            Single.just(true)
        )
        given(userRepository.saveNeedsMigration(anyBoolean())).willReturn(Completable.complete())
        given(walletRepository.unwatch(anyString())).willReturn(Completable.complete())
        interactor.migrate().test().assertResult(true)
    }

    @Test
    fun `observe transfer`() {
        val kp = Keypair(ByteArray(32), ByteArray(32))
        given(credentialsRepository.getAddress()).willReturn(Single.just("address"))
        given(credentialsRepository.retrieveKeyPair()).willReturn(Single.just(kp))
        given(
            walletRepository.observeTransfer(
                kp,
                "address",
                "to",
                "assetId",
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        ).willReturn(
            Observable.just(
                "hash" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    "sub",
                    "block"
                )
            )
        )
        given(
            walletRepository.saveTransaction(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(1)
        interactor.observeTransfer("to", "assetId", BigDecimal.ONE, BigDecimal.ONE).test()
            .assertComplete()
    }

    @Test
    fun `hide assets`() {
        val assets = listOf("id1", "id2")
        given(walletRepository.hideAssets(assets)).willReturn(Completable.complete())
        interactor.hideAssets(assets).test().assertComplete()
    }

    @Test
    fun `display assets`() {
        val assets = listOf("id1", "id2")
        given(walletRepository.displayAssets(assets)).willReturn(Completable.complete())
        interactor.displayAssets(assets).test().assertComplete()
    }

    @Test
    fun `update assets position`() {
        val assets = mapOf("id1" to 1, "id2" to 2)
        given(walletRepository.updateAssetPositions(assets)).willReturn(Completable.complete())
        interactor.updateAssetPositions(assets).test().assertComplete()
    }

    @Test
    fun `get account id called`() {
        interactor.getAccountId()
            .test()
            .assertValue(myAddress)
    }

    @Test
    fun `get transaction history called`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val peerid = "5EcDoG4T1SLbop4bxBjLL9VJaaytZxGXA7mLaY9y84GYpzsR"
        val transaction = Transaction(
            "",
            "",
            "transactionid",
            Transaction.Status.PENDING,
            Transaction.DetailedStatus.TRANSFER_PENDING,
            "assetId",
            "myAddress",
            "details",
            "peername",
            BigDecimal.TEN,
            10000,
            peerid,
            "reason",
            Transaction.Type.REWARD,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            bytes
        )

        given(walletRepository.getTransactions(myAddress, "")).willReturn(
            Observable.just(
                mutableListOf(transaction)
            )
        )

        interactor.getTransactions()
            .test()
            .assertResult(mutableListOf(transaction))
    }

    @Test
    fun `process qr called`() {
        val content = "substrate:notMyAddress:en:part4:part5"
        given(credentialsRepository.isAddressOk("notMyAddress")).willReturn(Single.just(true))
        interactor.processQr(content)
            .test()
            .assertResult(Triple("notMyAddress", "part5", BigDecimal.ZERO))
    }

    @Test
    fun `process qr called with wrong qr data`() {
        val content = "substrate:notMyAddress:en:tjj:qwe"
        given(credentialsRepository.isAddressOk("notMyAddress")).willReturn(Single.just(false))

        interactor.processQr(content)
            .test()
            .assertError { it is QrException && it.kind == QrException.Kind.USER_NOT_FOUND }
    }

    @Test
    fun `process qr called with users qr data`() {
        val qrData = QrData(accountId, "amount", "assetId")
        val content = "substrate:myAddress:en:tjj:qwe"

        interactor.processQr(content)
            .test()
            .assertError { it is QrException && it.kind == QrException.Kind.SENDING_TO_MYSELF }
    }

    private fun accountList() = listOf(
        Account("", "", "contact1"),
        Account("", "", "contact2"),
        Account("", "", "use"),
    )

    private fun assetList() = listOf(
        Asset("id", "assetName", "symbol", true, true, 1, 4, 18, BigDecimal.TEN, 0, true),
    )
}