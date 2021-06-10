/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.anyObject
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.nullable
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class WalletViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var preloader: WithPreloader

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var transactionMappers: TransactionMappers

    @Mock
    private lateinit var clipboardManager: ClipboardManager

    @Mock
    private lateinit var qrCodeDecoder: QrCodeDecoder

    @Mock
    private lateinit var uriMock: Uri

    private lateinit var walletViewModel: WalletViewModel

    @Before
    fun setUp() {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(
            transactionMappers.mapTransactionToSoraTransactionWithHeaders(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(transactionsWithHeaders)
        given(interactor.getTransactions()).willReturn(Observable.just(transactions))
        given(interactor.getAccountId()).willReturn(Single.just(""))
        given(interactor.getAssets(anyBoolean(), anyBoolean())).willReturn(Single.just(assets))
        walletViewModel = WalletViewModel(
            interactor, router, preloader,
            numbersFormatter, clipboardManager, transactionMappers,
            qrCodeDecoder,
        )
    }

    @Test
    fun `swipe asset card`() {
        given(interactor.hideAssets(listOf("soravalId"))).willReturn(Completable.complete())
        walletViewModel.onAssetCardSwiped(1)
        walletViewModel.assetsLiveData.observeForever {
            assertEquals(3, it.size)
        }
    }

    @Test
    fun `swipe asset card partly`() {
        given(interactor.hideAssets(listOf("soravalId"))).willReturn(Completable.complete())
        walletViewModel.onAssetCardSwipedPartly(1)
        walletViewModel.assetsLiveData.observeForever {
            assertEquals(3, it.size)
        }
    }

    @Test
    fun `init`() {
        assertEquals(transactionsWithHeaders, walletViewModel.transactionsModelLiveData.value)
    }

    @Test
    fun `btn help clicked`() {
        walletViewModel.btnHelpClicked()
        verify(router).showFaq()
    }

    @Test
    fun `send button clicked`() {
        walletViewModel.sendButtonClicked()
        verify(router).showAssetList(AssetListMode.SEND)
    }

    @Test
    fun `receive button clicked`() {
        walletViewModel.receiveButtonClicked()
        verify(router).showAssetList(AssetListMode.RECEIVE)
    }

    @Test
    fun `event clicked`() {
        val tx = transactions.first()
        val soraTransaction = SoraTransaction(
            "transactionId",
            true,
            0,
            "PL",
            "peername lastname",
            "peerId",
            "20",
            "",
            false,
            null
        )
        walletViewModel.eventClicked(soraTransaction)
        val date = Date(tx.timestamp)

        verify(router).showTransactionDetailsFromList(
            "myAddress",
            tx.peerId!!,
            tx.soranetTxHash,
            tx.blockHash!!,
            tx.amount,
            Transaction.Status.COMMITTED,
            tx.successStatus,
            tx.assetId,
            date.time,
            tx.type,
            tx.soranetFee,
            tx.amount + tx.soranetFee
        )
    }

    @Test
    fun `decode qr`() {
        val content = "qr_content"
        given(interactor.processQr(content)).willReturn(Single.just(Triple("recipient", "asset", BigDecimal.ZERO)))
        walletViewModel.qrResultProcess(content)
        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()
        verify(router).showValTransferAmount("recipient", "asset", BigDecimal.ZERO)
    }

    @Test
    fun `decode qr uri`() {
        given(qrCodeDecoder.decodeQrFromUri(anyNonNull())).willReturn(Single.just("content"))
        given(interactor.processQr("content")).willReturn(Single.just(Triple("recipient", "asset", BigDecimal.ZERO)))
        walletViewModel.decodeTextFromBitmapQr(uriMock)
        verify(router).showValTransferAmount("recipient", "asset", BigDecimal.ZERO)
    }

    private val assetXor = Asset(
        "soravalId", "Validator Token", "AssetHolder.SORA_VAL.symbol",
        true, false, 1, 2, 18, BigDecimal.TEN
    )

    private val assetXorErc = Asset(
        "xorId", "ERC Validator", "AssetHolder.SORA_VAL_ERC_20.symbol",
        true, false, 2, 2, 18, BigDecimal.TEN
    )

    private val assetEth = Asset(
        "AssetHolder.ETHER_ETH.id", "Ethereum", "AssetHolder.ETHER_ETH.symbol",
        false, false, 0, 2, 18, BigDecimal.TEN
    )

    private val transactions = mutableListOf(
        Transaction(
            "",
            "",
            "transactionId",
            Transaction.Status.COMMITTED,
            Transaction.DetailedStatus.TRANSFER_COMPLETED,
            "assetId",
            "myAddress",
            "details",
            "peername lastname",
            BigDecimal.ONE,
            1000,
            "peerId",
            "reason",
            Transaction.Type.INCOMING,
            BigDecimal.ZERO,
            BigDecimal(0.5),
            byteArrayOf(1, 2),
            "0x723842985",
            true
        )
    )

    private val transactionsWithHeaders = mutableListOf(
        EventHeader("today"),
        SoraTransaction(
            "transactionId",
            true,
            0,
            "PL",
            "peername lastname",
            "01 Jan 1970 00:00",
            "10.12 VAL",
            "",
            false,
            null
        )
    )

    private val assets = mutableListOf(assetXor, assetXorErc, assetEth)
}