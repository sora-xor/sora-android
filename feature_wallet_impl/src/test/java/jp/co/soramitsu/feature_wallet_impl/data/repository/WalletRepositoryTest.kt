/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.TestRuntimeProvider
import jp.co.soramitsu.xnetworking.subquery.SubQueryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.math.BigInteger

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class WalletRepositoryTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var datasource: PrefsWalletDatasource

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var assetDao: AssetDao

    @Mock
    private lateinit var assetHolder: AssetHolder

    @Mock
    private lateinit var assetLocalToAssetMapper: AssetLocalToAssetMapper

    @Mock
    private lateinit var extrinsicManager: ExtrinsicManager

    @Mock
    private lateinit var substrateCalls: SubstrateCalls

    @Mock
    private lateinit var historyReader: SubQueryClient<*, *>

    @Mock
    private lateinit var fileManager: FileManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var gson: Gson

    private lateinit var runtime: RuntimeSnapshot

    private lateinit var walletRepository: WalletRepositoryImpl
    private val emptyJson = "{}"
    private val myAddress = "myaddress"
    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        runtime = TestRuntimeProvider.buildRuntime("sora2")
        given(db.assetDao()).willReturn(assetDao)
        walletRepository = WalletRepositoryImpl(
            datasource,
            db,
            fileManager,
            gson,
            resourceManager,
            AssetLocalToAssetMapper(),
            extrinsicManager,
            substrateCalls,
            runtimeManager
        )
    }

    @Test
    fun `save migration status`() = runTest {
        val m = MigrationStatus.SUCCESS
        given(datasource.saveMigrationStatus(m)).willReturn(Unit)
        walletRepository.saveMigrationStatus(m)
        verify(datasource).saveMigrationStatus(m)
    }

    @Test
    fun `retrieve claim block hash`() = runTest {
        given(datasource.retrieveClaimBlockAndTxHash()).willReturn("block" to "hash")
        assertEquals("block" to "hash", walletRepository.retrieveClaimBlockAndTxHash())
    }

    @Test
    fun `get assets`() = runTest {
        given(assetDao.getAssetsVisible(anyString(), anyString())).willReturn(
            assetTokenList()
        )
        val assets = walletRepository.getAssetsVisible("address")
        val expected = assetList().subList(0, 2)
        assertEquals(expected.size, assets.size)
        repeat(2) {
            assertEquals(expected[it], assets[it])
        }
    }

    @Test
    fun `calc fee`() = runTest {
        given(
            extrinsicManager.calcFee(anyString(), anyBoolean(), any())
        ).willReturn(BigInteger("1000000000000000000"))
        val fee = walletRepository.calcTransactionFee(
            "from",
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE
        )
        assertEquals(BigDecimal("1"), fee)
    }

    @Test
    fun `observe migrate`() = runTest {
        val key = Sr25519Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5, 6))
        given(
            extrinsicManager.submitAndWaitExtrinsic(
                anyString(),
                any(),
                anyBoolean(),
                anyString(),
                any(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "txhash", "blockhash")
        )
        val test = walletRepository.migrate(
            "iroha address",
            "iroha public",
            "signature",
            key,
            "from",
        )

        assertEquals(true, test.success)
    }

    @Test
    fun `observe transfer`() = runTest {
        val key = Sr25519Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5, 6))
        given(
            extrinsicManager.submitAndWatchExtrinsic(
                anyString(),
                any(),
                anyBoolean(),
                any()
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "txhash", "blockhash")
        )
        val result = walletRepository.observeTransfer(
            key,
            "from",
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE,
            BigDecimal.ZERO
        )
        assertEquals(true, result.success)
    }

    @Test
    fun `display assets`() = runTest {
        walletRepository.displayAssets(listOf(), soraAccount)
    }

    @Test
    fun `hide assets`() = runTest {
        walletRepository.hideAssets(listOf(), soraAccount)
    }

    private fun assetTokenList() = listOf(
        AssetTokenLocal(
            assetLocalList()[0], oneTokenLocal(),
        ),
        AssetTokenLocal(
            assetLocalList()[1], oneTokenLocal2(),
        )
    )

    private fun assetLocalList() = listOf(
        AssetLocal(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "someaddress",
            true,
            1,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        ),
        AssetLocal(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "someaddress",
            true,
            2,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
        ),
    )

    private fun assetList() = listOf(
        Asset(
            oneToken(),
            true,
            1,
            assetBalance(),
        ),
        Asset(
            oneToken2(),
            true,
            2,
            assetBalance(),
        ),
        Asset(
            oneToken3(),
            true,
            3,
            assetBalance(),
        ),
        Asset(
            oneToken4(),
            true,
            4,
            assetBalance(),
        )
    )

    private fun oneToken() = Token(
        "0x0200000000000000000000000000000000000000000000000000000000000000",
        "SORA",
        "XOR",
        18,
        false,
        OptionsProvider.DEFAULT_ICON,
    )

    private fun oneTokenLocal() =
        TokenLocal(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "SORA",
            "XOR",
            18,
            true,
            "whitelist",
            false
        )

    private fun oneToken2() = Token(
        "0x0200040000000000000000000000000000000000000000000000000000000000",
        "SORA Validator Token",
        "VAL",
        18,
        true,
        OptionsProvider.DEFAULT_ICON,
    )

    private fun oneTokenLocal2() =
        TokenLocal(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "SORA Validator Token",
            "VAL",
            18,
            true,
            "whitelist",
            true
        )

    private fun oneToken3() = Token(
        "0x0200050000000000000000000000000000000000000000000000000000000000",
        "Polkaswap",
        "PSWAP",
        18,
        true,
        OptionsProvider.DEFAULT_ICON,
    )

    private fun oneToken4() = Token(
        "0x0200080000000000000000000000000000000000000000000000000000000000",
        "SORA Synthetic USD",
        "XSTUSD",
        18,
        true,
        OptionsProvider.DEFAULT_ICON
    )

    private fun assetBalance() = AssetBalance(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    )
}
