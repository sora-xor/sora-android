/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.domain

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class AssetsInteractorTest {
    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var builder: TransactionBuilder

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: AssetsInteractor

    private val soraAccount = SoraAccount("address", "name")

    private val irohaData: IrohaData =
            IrohaData(address = "abcdef", claimSignature = "qweasdzc", publicKey = "publickey")

    @Before
    fun setUp() = runTest {
        mockkStatic(String::extrinsicHash)
        every { "0x112323345".extrinsicHash() } returns "blake2b"
        every { "0x35456472".extrinsicHash() } returns "blake2b"
        mockkObject(OptionsProvider)
        BDDMockito.given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = AssetsInteractorImpl(
                assetsRepository = assetsRepository,
                credentialsRepository = credentialsRepository,
                coroutineManager = coroutineManager,
                transactionBuilder = builder,
                transactionHistoryRepository = transactionHistoryRepository,
                userRepository = userRepository,
        )
    }

    @Test
    fun `get assets`() = runTest {
        BDDMockito.given(
                assetsRepository.getAssetsFavorite(
                        "address",
                )
        ).willReturn(assetList())
        Assert.assertEquals(assetList(), interactor.getVisibleAssets())
    }

    @Test
    fun `just transfer`() = runTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        BDDMockito.given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        BDDMockito.given(
                assetsRepository.transfer(
                        kp,
                        "address",
                        "to",
                        TestTokens.xorToken,
                        BigDecimal.ONE
                )
        ).willReturn(
                Result.success("")
        )
        Assert.assertEquals(
                Result.success(""),
                interactor.transfer("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `calc transaction fee`() = runTest {
        BDDMockito.given(
                assetsRepository.calcTransactionFee(
                        "address",
                        "to",
                        TestTokens.xorToken,
                        BigDecimal.ONE
                )
        ).willReturn(BigDecimal.TEN)
        Assert.assertEquals(
                BigDecimal.TEN,
                interactor.calcTransactionFee("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `observe transfer`() = runTest(UnconfinedTestDispatcher()) {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        BDDMockito.given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        BDDMockito.given(
                assetsRepository.observeTransfer(
                        any(),
                        BDDMockito.anyString(),
                        BDDMockito.anyString(),
                        any(),
                        any(),
                        any(),
                )
        ).willReturn(
                ExtrinsicSubmitStatus(true, "txhash", "")
        )
        Assert.assertEquals(
                "txhash",
                interactor.observeTransfer("to", TestTokens.xorToken, BigDecimal.ONE, BigDecimal.ONE)
        )
    }

    @Test
    fun `hide assets`() = runTest {
        val assets = listOf("id1", "id2")
        BDDMockito.given(assetsRepository.hideAssets(assets, soraAccount)).willReturn(Unit)
        Assert.assertEquals(Unit, interactor.tokenFavoriteOff(assets))
    }

    @Test
    fun `display assets`() = runTest {
        val assets = listOf("id1", "id2")
        BDDMockito.given(assetsRepository.displayAssets(assets, soraAccount)).willReturn(Unit)
        Assert.assertEquals(Unit, interactor.tokenFavoriteOn(assets))
    }

    @Test
    fun `update assets position`() = runTest {
        val assets = mapOf("id1" to 1, "id2" to 2)
        BDDMockito.given(assetsRepository.updateAssetPositions(assets, soraAccount)).willReturn(Unit)
        Assert.assertEquals(Unit, interactor.updateAssetPositions(assets))
    }

    @Test
    fun `get account id called`() = runTest {
        Assert.assertEquals(soraAccount.substrateAddress, interactor.getCurSoraAccount().substrateAddress)
    }

    private fun accountList() = listOf(
            "use","contact1","contact2",
    )

    private fun assetList() = listOf(
            Asset(oneToken(), true, 1, assetBalance(), true),
    )

    private fun oneToken() = Token(
            "token_id",
            "token name",
            "token symbol",
            18,
            true,
            null,
            null,
            null,
            null,
    )

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