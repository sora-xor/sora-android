/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class WalletViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

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
    fun setUp() = runBlockingTest {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(interactor.getVisibleAssets()).willReturn(assets)
        walletViewModel = WalletViewModel(
            interactor, router, preloader,
            numbersFormatter, clipboardManager, transactionMappers,
            qrCodeDecoder,
        )
    }

    @Test
    fun `swipe asset card`() = runBlockingTest {
        walletViewModel.onAssetCardSwiped(
            assetModel()
        )
        walletViewModel.assetsLiveData.observeForever {
            assertEquals(3, it.size)
        }
    }

    @Test
    fun `swipe asset card partly`() = runBlockingTest {
        walletViewModel.onAssetCardSwipedPartly(assetModel())
        val res = walletViewModel.assetsLiveData.getOrAwaitValue()
        assertEquals(3, res.size)
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
        walletViewModel.eventClicked("id")
        verify(router).showTransactionDetails("id")
    }

    @Test
    fun `decode qr`() = runBlockingTest {
        val content = "qr_content"
        given(interactor.processQr(content)).willReturn(
            Triple(
                "recipient",
                "asset",
                BigDecimal.ZERO
            )
        )
        walletViewModel.qrResultProcess(content)
        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()
        verify(router).showValTransferAmount("recipient", "asset", BigDecimal.ZERO)
    }

    @Test
    fun `decode qr uri`() = runBlockingTest {
        given(qrCodeDecoder.decodeQrFromUri(anyNonNull())).willReturn("content")
        given(interactor.processQr("content")).willReturn(
            Triple(
                "recipient",
                "asset",
                BigDecimal.ZERO
            )
        )
        walletViewModel.decodeTextFromBitmapQr(uriMock)
        verify(router).showValTransferAmount("recipient", "asset", BigDecimal.ZERO)
    }

    private val assetXor = Asset(
        oneToken(),
        true, false, 0, AssetBalance(
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE
        )
    )

    private val assetXorErc = Asset(
        oneToken(),
        true, false, 0, AssetBalance(
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE
        )
    )

    private val assetEth = Asset(
        oneToken(),
        true, false, 0, AssetBalance(
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE
        )
    )

    private val transactions = mutableListOf(
        Transaction.Transfer(
            "",
            "",
            BigDecimal.ZERO,
            TransactionStatus.COMMITTED,
            1000,
            true,
            BigDecimal.ONE,
            "peerId",
            TransactionTransferType.INCOMING,
            oneToken(),
        )
    )

    private fun oneToken() = Token("token_id", "token name", "token symbol", 18, true, 0)

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
            false,
            null
        )
    )

    private val assets = mutableListOf(assetXor, assetXorErc, assetEth)

    private fun eventUi() = EventUiModel.EventTxUiModel.EventTransferUiModel(
        "id",
        true,
        0,
        "",
        "",
        100000,
        "",
        "",
        false,
        true
    )

    private fun assetModel() = AssetModel(
        "id",
        "",
        "",
        0,
        "",
        AssetModel.State.NORMAL,
        4,
        1,
        true,
        true,
        true
    )
}