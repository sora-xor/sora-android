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
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEntry
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.blake2b256String
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyLong
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class WalletInteractorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

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

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: WalletInteractor

    private val soraAccount = SoraAccount("address", "name")
    private val irohaData = IrohaData("irohaAddress", "irohaClaim", "irohaPublic")

    @Before
    fun setUp() = runBlockingTest {
        given(coroutineManager.applicationScope).willReturn(mainCoroutineRule)
        mockkStatic(String::blake2b256String)
        every { "0x112323345".blake2b256String() } returns "blake2b"
        every { "0x35456472".blake2b256String() } returns "blake2b"
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = WalletInteractorImpl(
            walletRepository,
            ethRepository,
            userRepository,
            credentialsRepository,
            cryptoAssistant,
            coroutineManager,
        )
    }

    @Test
    fun `needs migration`() = runBlockingTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        given(walletRepository.needsMigration("irohaAddress")).willReturn(true)
        given(userRepository.saveNeedsMigration(anyBoolean(), anyNonNull())).willReturn(Unit)
        val result = interactor.needsMigration()
        assertEquals(true, result)
    }

    @Test
    fun `get assets`() = runBlockingTest {
        given(
            walletRepository.getAssetsVisible(
                "address",
            )
        ).willReturn(assetList())
        assertEquals(assetList(), interactor.getVisibleAssets())
    }

    @Test
    fun `find other users`() = runBlockingTest {
        given(credentialsRepository.isAddressOk(anyString())).willReturn(true)
        given(walletRepository.getContacts(anyString())).willReturn(
            setOf(
                "contact1",
                "contact2"
            )
        )
        assertEquals(accountList(), interactor.findOtherUsersAccounts("use"))
    }

    @Test
    fun `just transfer`() = runBlockingTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(walletRepository.transfer(kp, "address", "to", "assetId", BigDecimal.ONE)).willReturn(
            "hash"
        )
        assertEquals("hash", interactor.transfer("to", "assetId", BigDecimal.ONE))
    }

    @Test
    fun `calc transaction fee`() = runBlockingTest {
        given(
            walletRepository.calcTransactionFee(
                "address",
                "to",
                "assetId",
                BigDecimal.ONE
            )
        ).willReturn(BigDecimal.TEN)
        assertEquals(BigDecimal.TEN, interactor.calcTransactionFee("to", "assetId", BigDecimal.ONE))
    }

    @Test
    fun `migrate extrinsic`() = runBlockingTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(
            walletRepository.migrate(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
            )
        ).willReturn(
            flow {
                emit(
                    "hash" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                        "sub",
                        "block"
                    )
                )
            }
        )
        val br = BlockResponse(
            "justification",
            BlockEntry("header", listOf("0x112323345", "0x35456472"))
        )
        given(walletRepository.getBlock(anyString())).willReturn(br)
        given(walletRepository.isTxSuccessful(anyLong(), anyString(), anyString())).willReturn(true)
        given(userRepository.saveNeedsMigration(anyBoolean(), anyNonNull())).willReturn(Unit)
        assertEquals(true, interactor.migrate())
    }

    @Test
    fun `observe transfer`() = runBlockingTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
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
            flow {
                emit(
                    "hash" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                        "sub",
                        "block"
                    )
                )
            }
        )
        given(
            walletRepository.saveTransfer(
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).willReturn(Unit)
        assertEquals(
            true,
            interactor.observeTransfer("to", "assetId", BigDecimal.ONE, BigDecimal.ONE)
        )
    }

    @Test
    fun `hide assets`() = runBlockingTest {
        val assets = listOf("id1", "id2")
        given(walletRepository.hideAssets(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.hideAssets(assets))
    }

    @Test
    fun `display assets`() = runBlockingTest {
        val assets = listOf("id1", "id2")
        given(walletRepository.displayAssets(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.displayAssets(assets))
    }

    @Test
    fun `update assets position`() = runBlockingTest {
        val assets = mapOf("id1" to 1, "id2" to 2)
        given(walletRepository.updateAssetPositions(assets, soraAccount)).willReturn(Unit)
        assertEquals(Unit, interactor.updateAssetPositions(assets))
    }

    @Test
    fun `get account id called`() = runBlockingTest {
        assertEquals(soraAccount.substrateAddress, interactor.getAddress())
    }

    @Test
    fun `process qr called`() = runBlockingTest {
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
    fun `process qr called with wrong qr data`() = runBlockingTest {
        val content = "substrate:notMyAddress:en:tjj:qwe"
        given(credentialsRepository.isAddressOk("notMyAddress")).willReturn(false)
        given(walletRepository.isWhitelistedToken("qwe")).willReturn(true)
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
    fun `process qr called with users qr data`() = runBlockingTest {
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