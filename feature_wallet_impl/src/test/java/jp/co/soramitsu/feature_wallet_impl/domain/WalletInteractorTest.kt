/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.sora.substrate.models.BlockEntry
import jp.co.soramitsu.sora.substrate.models.BlockResponse
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.doNothing
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class WalletInteractorTest {

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

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

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: WalletInteractor

    private val soraAccount = SoraAccount("address", "name")
    //private val irohaData = IrohaData("irohaAddress", "irohaClaim", "irohaPublic")

    private val irohaData: IrohaData =
        IrohaData(address = "abcdef", claimSignature = "qweasdzc", publicKey = "publickey")

    @Before
    fun setUp() = runTest {
        mockkStatic(String::extrinsicHash)
        every { "0x112323345".extrinsicHash() } returns "blake2b"
        every { "0x35456472".extrinsicHash() } returns "blake2b"
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = WalletInteractorImpl(
            walletRepository,
            transactionHistoryRepository,
            ethRepository,
            userRepository,
            credentialsRepository,
            cryptoAssistant,
            coroutineManager,
        )
    }

    @Test
    fun `needs migration`() = runTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        given(walletRepository.needsMigration("abcdef")).willReturn(true)
        given(userRepository.saveNeedsMigration(true, soraAccount)).willReturn(Unit)
        val result = interactor.needsMigration()
        assertEquals(true, result)
    }

    @Test
    fun `get assets`() = runTest {
        given(
            walletRepository.getAssetsVisible(
                "address",
            )
        ).willReturn(assetList())
        assertEquals(assetList(), interactor.getVisibleAssets())
    }

    @Test
    fun `find other users`() = runTest {
        given(credentialsRepository.isAddressOk(anyString())).willReturn(true)
        given(transactionHistoryRepository.getContacts(anyString())).willReturn(
            setOf(
                "contact1",
                "contact2"
            )
        )
        assertEquals(accountList(), interactor.getContacts("use"))
    }

    @Test
    fun `just transfer`() = runTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(
            walletRepository.transfer(
                kp,
                "address",
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE
            )
        ).willReturn(
            Result.success("")
        )
        assertEquals(
            Result.success(""),
            interactor.transfer("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `calc transaction fee`() = runTest {
        given(
            walletRepository.calcTransactionFee(
                "address",
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE
            )
        ).willReturn(BigDecimal.TEN)
        assertEquals(
            BigDecimal.TEN,
            interactor.calcTransactionFee("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `migrate extrinsic`() = runTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(
            walletRepository.migrate(
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "", "")
        )
        val br = BlockResponse(
            "justification",
            BlockEntry("header", listOf("0x112323345", "0x35456472"))
        )
        assertEquals(true, interactor.migrate())
    }

    @Test
    fun `observe transfer`() = runTest(UnconfinedTestDispatcher()) {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(
            walletRepository.observeTransfer(
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "", "")
        )
        doNothing().`when`(transactionHistoryRepository).saveTransaction(
            any(),
        )
        assertEquals(
            true,
            interactor.observeTransfer("to", TestTokens.xorToken, BigDecimal.ONE, BigDecimal.ONE)
        )
    }

    @Test
    fun `hide assets`() = runTest {
        val assets = listOf("id1", "id2")
        given(walletRepository.hideAssets(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.hideAssets(assets))
    }

    @Test
    fun `display assets`() = runTest {
        val assets = listOf("id1", "id2")
        given(walletRepository.displayAssets(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.displayAssets(assets))
    }

    @Test
    fun `update assets position`() = runTest {
        val assets = mapOf("id1" to 1, "id2" to 2)
        given(walletRepository.updateAssetPositions(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.updateAssetPositions(assets))
    }

    @Test
    fun `get account id called`() = runTest {
        assertEquals(soraAccount.substrateAddress, interactor.getAddress())
    }

    @Test
    fun `process qr called`() = runTest {
        val content = "substrate:notMyAddress:en:part4:part5"
        given(credentialsRepository.isAddressOk("notMyAddress")).willReturn(true)
        given(walletRepository.isWhitelistedToken("part5")).willReturn(true)
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!! == Triple("notMyAddress", "part5", BigDecimal.ZERO))
    }

    @Test
    fun `process qr called with wrong qr data`() = runTest {
        val content = "substrate:notMyAddress:en:tjj:qwe"
        given(credentialsRepository.isAddressOk("notMyAddress")).willReturn(false)
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()!!
        assertTrue(
            exception is QrException && exception.kind == QrException.Kind.USER_NOT_FOUND
        )
    }

    @Test
    fun `process qr called with users qr data`() = runTest {
        val content = "substrate:address:en:tjj:qwe"
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()!!
        assertTrue(
            exception is QrException && exception.kind == QrException.Kind.SENDING_TO_MYSELF
        )
    }

    private fun accountList() = listOf(
        Account("", "", "use"),
        Account("", "", "contact1"),
        Account("", "", "contact2"),
    )

    private fun assetList() = listOf(
        Asset(oneToken(), true, 1, assetBalance()),
    )

    private fun oneToken() = Token("token_id", "token name", "token symbol", 18, true, 0)

    private fun assetBalance() = AssetBalance(
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE
    )
}