/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.dto.AssetInfoDto
import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.common.data.network.dto.InnerEventRecord
import jp.co.soramitsu.common.data.network.dto.PhaseRecord
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransferTransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetToAssetLocalMapper
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_wallet_impl.data.substrate.TestRuntimeProvider
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.anyList
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willDoNothing
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class WalletRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

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

    private lateinit var runtime: RuntimeSnapshot

    private lateinit var walletRepository: WalletRepositoryImpl
    private val emptyJson = "{}"
    private val myAddress = "myaddress"

    @Before
    fun setUp() {
        runtime = TestRuntimeProvider.buildRuntime("sora2")
        given(db.assetDao()).willReturn(assetDao)
        given(db.transactionDao()).willReturn(transactionDao)
        mockkObject(RuntimeHolder)
        every { RuntimeHolder.getRuntime() } returns runtime
        walletRepository = WalletRepositoryImpl(
            datasource, db, subapi, serializer, appLinksProvider, AssetHolder(),
            AssetLocalToAssetMapper(), AssetToAssetLocalMapper()
        )
    }

    @Test
    fun `save migration status`() {
        val m = MigrationStatus.SUCCESS
        willDoNothing().given(datasource).saveMigrationStatus(anyNonNull())
        walletRepository.saveMigrationStatus(m).test().assertComplete()
        verify(datasource).saveMigrationStatus(m)
    }

    @Test
    fun `retrieve claim block hash`() {
        given(datasource.retrieveClaimBlockAndTxHash()).willReturn("block" to "hash")
        walletRepository.retrieveClaimBlockAndTxHash().test().assertResult("block" to "hash")
    }

    @Test
    fun `get assets`() {
        given(assetDao.getAll()).willReturn(Maybe.just(emptyList()))
        given(subapi.fetchAssetList(runtime)).willReturn(Single.just(assetInfoDtoList()))
        given(subapi.fetchBalance(anyString(), anyString())).willReturn(Single.just(BigInteger("1000000000000000000")))
        willDoNothing().given(assetDao).insert(anyNonNull())
        walletRepository.getAssets("address", true, true)
            .test()
            .assertResult(assetList())
    }

    @Test
    fun `calc fee`() {
        given(assetDao.getAll()).willReturn(Maybe.just(assetLocalList()))
        willDoNothing().given(assetDao).insert(anyNonNull())
        given(
            subapi.calcFee(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
            )
        ).willReturn(
            Single.just(
                BigInteger("10000000000000000000")
            )
        )
        walletRepository.calcTransactionFee(
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE
        ).test()
            .assertResult(
                BigDecimal.TEN
            )
    }

    @Test
    fun `observe migrate`() {
        given(
            subapi.migrate(
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull(),
                anyString()
            )
        ).willReturn(
            Observable.just(
                "id" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    "sub",
                    "block"
                )
            )
        )
        val test = walletRepository.migrate(
            "iroha address",
            "iroha public",
            "signature",
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "address"
        ).test()
        test.assertComplete()
    }

    @Test
    fun `tx success`() {
        given(subapi.checkEvents(anyNonNull(), anyNonNull())).willReturn(Single.just(eventRecord()))
        walletRepository.isTxSuccessful(1, "blockhash", "txhash").test().assertResult(true)
    }

    @Test
    fun `transfer simple`() {
        given(assetDao.getAll()).willReturn(Maybe.just(assetLocalList()))
        given(
            subapi.transfer(
                anyNonNull(),
                anyString(),
                anyString(),
                anyString(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(
            Single.just("hash")
        )
        val test = walletRepository.transfer(
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE,
        ).test()
        test.assertResult("hash")
    }

    @Test
    fun `observe transfer`() {
        given(assetDao.getAll()).willReturn(Maybe.just(assetLocalList()))
        willDoNothing().given(assetDao).insert(anyNonNull())
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
            Observable.just(
                "id" to ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                    "sub",
                    "block"
                )
            )
        )
        val test = walletRepository.observeTransfer(
            Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            "from",
            "to",
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            BigDecimal.ONE,
            BigDecimal.ZERO
        ).test()
        test.assertComplete()
    }

    @Test
    fun `save transaction`() {
        given(transactionDao.insert(transaction = anyNonNull())).willReturn(123)
        val id = walletRepository.saveTransaction(
            "from",
            "to",
            "assetId",
            BigDecimal.ZERO,
            ExtrinsicStatusResponse.ExtrinsicStatusFinalized("s", "b"),
            "hash",
            BigDecimal.ZERO,
            true,
        )
        assertEquals(123, id)
    }

    @Test
    fun `update asset position`() {
        //willDoNothing().given(assetDao).updateAssetPosition(anyString(), anyInt())
        walletRepository.updateAssetPositions(mapOf("title" to 11)).test().assertComplete()
    }

    @Test
    fun `display assets`() {
        willDoNothing().given(assetDao).displayAssets(anyList())
        walletRepository.displayAssets(listOf()).test().assertComplete()
    }

    @Test
    fun `hide assets`() {
        willDoNothing().given(assetDao).hideAssets(anyList())
        walletRepository.hideAssets(listOf()).test().assertComplete()
    }

    @Test
    fun `get contacts`() {
        given(transactionDao.getContacts("")).willReturn(Single.just(listOf("contact")))
        walletRepository.getContacts("").test().assertResult(setOf("contact"))
    }

    @Test
    fun `get transactions called`() {
        val transactionsLocal = mutableListOf(
            TransferTransactionLocal(
                "transactionId",
                TransferTransactionLocal.Status.COMMITTED,
                "AssetHolder.SORA_XOR.id",
                "myAddress",
                BigDecimal.TEN,
                1000,
                "peerId",
                TransferTransactionLocal.Type.OUTGOING,
                BigDecimal.TEN,
                null,
                true,
            ),
            TransferTransactionLocal(
                "transactionId",
                TransferTransactionLocal.Status.COMMITTED,
                "AssetHolder.SORA_XOR_ERC_20.id",
                "myAddress",
                BigDecimal.TEN,
                1000,
                "peerId",
                TransferTransactionLocal.Type.OUTGOING,
                BigDecimal.TEN,
                null,
                true
            )
        )
        val transactions = mutableListOf(
            Transaction(
                "",
                "",
                "transactionId",
                Transaction.Status.COMMITTED,
                Transaction.DetailedStatus.TRANSFER_COMPLETED,
                "AssetHolder.SORA_XOR.id",
                "myAddress",
                "",
                "",
                BigDecimal.TEN,
                1000,
                "peerId",
                "",
                Transaction.Type.OUTGOING,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                null,
                true
            ),
            Transaction(
                "",
                "",
                "transactionId",
                Transaction.Status.COMMITTED,
                Transaction.DetailedStatus.TRANSFER_COMPLETED,
                "AssetHolder.SORA_XOR_ERC_20.id",
                "myAddress",
                "",
                "",
                BigDecimal.TEN,
                1000,
                "peerId",
                "",
                Transaction.Type.OUTGOING,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                null,
                true
            )
        )
        given(transactionDao.getTransactions()).willReturn(Observable.just(transactionsLocal))

        walletRepository.getTransactions(myAddress, myAddress)
            .test()
            .assertResult(transactions)
    }

    private fun assetLocalList() = listOf(
        AssetLocal(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "Sora",
            "xor",
            true,
            1,
            18,
            true,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        ),
        AssetLocal(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "Sora2",
            "val",
            true,
            2,
            18,
            true,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        ),
    )

    private fun assetList() = listOf(
        Asset(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "sora",
            "xor",
            true,
            false,
            1,
            4,
            18,
            BigDecimal.ONE,
            R.drawable.ic_xor_red_shadow,
            true
        ),
        Asset(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "soranet",
            "val",
            true,
            true,
            2,
            4,
            18,
            BigDecimal.ONE,
            R.drawable.ic_val_gold_shadow,
            true
        ),
    )

    private fun assetInfoDtoList() = listOf(
        AssetInfoDto(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "sora",
            "xor",
            18,
            true,
        ),
        AssetInfoDto(
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
