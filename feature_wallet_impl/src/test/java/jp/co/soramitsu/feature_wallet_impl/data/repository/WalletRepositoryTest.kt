package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.ExperimentalPagingApi
import androidx.room.withTransaction
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.common.data.network.dto.InnerEventRecord
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetToAssetLocalMapper
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.data.substrate.TestRuntimeProvider
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyList
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willDoNothing
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.math.BigInteger

@ExperimentalCoroutinesApi
@ExperimentalPagingApi
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
    private lateinit var transactionDao: TransferTransactionDao

    @Mock
    private lateinit var assetDao: AssetDao

    @Mock
    private lateinit var serializer: Serializer

    @Mock
    private lateinit var appLinksProvider: AppLinksProvider

    @Mock
    private lateinit var assetHolder: AssetHolder

    @Mock
    private lateinit var assetLocalToAssetMapper: AssetLocalToAssetMapper

    @Mock
    private lateinit var assetToAssetLocalMapper: AssetToAssetLocalMapper

    @Mock
    private lateinit var subapi: SubstrateApi

    @Mock
    private lateinit var soraScanApi: SoraScanApi

    @Mock
    private lateinit var fileManager: FileManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var gson: Gson

    private lateinit var runtime: RuntimeSnapshot

    private lateinit var walletRepository: WalletRepositoryImpl
    private val emptyJson = "{}"
    private val myAddress = "myaddress"

    @Before
    fun setUp() = runBlockingTest {
        runtime = TestRuntimeProvider.buildRuntime("sora2")
        given(db.assetDao()).willReturn(assetDao)
        given(assetDao.getPrecisionOfToken(anyString())).willReturn(1)
        given(db.transactionDao()).willReturn(transactionDao)
        mockkObject(RuntimeHolder)
        every { RuntimeHolder.getRuntime() } returns runtime
        walletRepository = WalletRepositoryImpl(
            datasource,
            db,
            subapi,
            soraScanApi,
            fileManager,
            gson,
            AssetHolder(),
            resourceManager,
            AssetLocalToAssetMapper(),
        )
    }

    @Test
    fun `save migration status`() = runBlockingTest {
        val m = MigrationStatus.SUCCESS
        willDoNothing().given(datasource).saveMigrationStatus(anyNonNull())
        walletRepository.saveMigrationStatus(m)
        verify(datasource).saveMigrationStatus(m)
    }

    @Test
    fun `retrieve claim block hash`() = runBlockingTest {
        given(datasource.retrieveClaimBlockAndTxHash()).willReturn("block" to "hash")
        assertEquals("block" to "hash", walletRepository.retrieveClaimBlockAndTxHash())
    }

    @Test
    fun `get assets`() = runBlockingTest {
        given(assetDao.getAssetsVisible(anyString())).willReturn(emptyList())
        assertEquals(assetList(), walletRepository.getAssetsVisible("address"))
    }

    @Test
    fun `calc fee`() = runBlockingTest {
        given(
            subapi.calcFee(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
            )
        ).willReturn(BigInteger("1000000000000000000"))
        val fee = walletRepository.calcTransactionFee(
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE
        )
        assertEquals(BigDecimal("100000000000000000"), fee)
    }

    @Test
    fun `observe migrate`() = runBlockingTest {
        given(
            subapi.migrate(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(
            flow {
                emit(
                    "id" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                        "sub",
                        "block"
                    )
                )
            }
        )
        val test = walletRepository.migrate(
            "iroha address",
            "iroha public",
            "signature",
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
        ).toList()

        assertEquals(1, test.size)
    }

    @Test
    fun `tx success`() = runBlockingTest {
        given(subapi.checkEvents(anyNonNull(), anyNonNull())).willReturn(eventRecord())
        assertEquals(true, walletRepository.isTxSuccessful(1, "blockhash", "txhash"))
    }

    @Test
    fun `transfer simple`() = runBlockingTest {
        given(
            subapi.transfer(
                anyNonNull(),
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn("hash")
        val test = walletRepository.transfer(
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE,
        )
        assertEquals("hash", test)
    }

    @Test
    fun `observe transfer`() = runBlockingTest {
        given(
            subapi.observeTransfer(
                anyNonNull(),
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(
            flow {
                emit(
                    "id" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                        "sub",
                        "block"
                    )
                )
            }
        )
        val result = walletRepository.observeTransfer(
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE,
            BigDecimal.ZERO
        ).toList()
        assertEquals(1, result.size)
    }

    @Test
    fun `save transaction`() = runBlockingTest {
        mockkStatic("androidx.room.RoomDatabaseKt")
        val lambda = slot<suspend () -> Unit>()
        coEvery { db.withTransaction(capture(lambda)) } coAnswers {
            lambda.captured.invoke()
        }
        given(transactionDao.insert(transaction = anyNonNull())).willReturn(Unit)
        given(transactionDao.insertParams(transactions = anyList())).willReturn(Unit)
        walletRepository.saveTransfer(
            "from",
            "assetId",
            BigDecimal.ZERO,
            ExtrinsicStatusResponse.ExtrinsicStatusFinalized("s", "b"),
            "hash",
            BigDecimal.ZERO,
            true,
        )
        verify(transactionDao).insert(transaction = anyNonNull())
        verify(transactionDao).insertParams(transactions = anyList())
    }

    @Test
    fun `display assets`() = runBlockingTest {
        walletRepository.displayAssets(listOf())
    }

    @Test
    fun `hide assets`() = runBlockingTest {
        walletRepository.hideAssets(listOf())
    }

    @Test
    fun `get contacts`() = runBlockingTest {
        given(transactionDao.getContacts("")).willReturn(listOf("contact"))
        assertEquals(setOf("contact"), walletRepository.getContacts(""))
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
            true,
            1,
            BigDecimal.ONE,
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
            false,
            3,
            BigDecimal.ONE,
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
            true,
            1,
            assetBalance(),
        ),
        Asset(
            oneToken2(),
            true,
            true,
            2,
            assetBalance(),
        ),
        Asset(
            oneToken3(),
            true,
            true,
            3,
            assetBalance(),
        )
    )

    private fun oneToken() = Token(
        "0x0200000000000000000000000000000000000000000000000000000000000000",
        "SORA",
        "XOR",
        18,
        false,
        0
    )

    private fun oneTokenLocal() =
        TokenLocal("token_id", "token name", "token symbol", 18, true, "whitelist", true)

    private fun oneToken2() = Token(
        "0x0200040000000000000000000000000000000000000000000000000000000000",
        "SORA Validator Token",
        "VAL",
        18,
        true,
        0
    )

    private fun oneTokenLocal2() =
        TokenLocal("token2_id", "token2 name", "token2 symbol", 18, true, "whitelist", true)

    private fun oneToken3() = Token(
        "0x0200050000000000000000000000000000000000000000000000000000000000",
        "Polkaswap",
        "PSWAP",
        18,
        true,
        0
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

    private fun assetInfoDtoList() = listOf(
        TokenInfoDto(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "sora",
            "xor",
            18,
            true,
        ),
        TokenInfoDto(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "soranet",
            "val",
            18,
            true,
        ),
    )

    private fun eventRecord() = listOf(
        EventRecord(PhaseRecord.ApplyExtrinsic(BigInteger.ONE), InnerEventRecord(0, 0, null))
    )
}
