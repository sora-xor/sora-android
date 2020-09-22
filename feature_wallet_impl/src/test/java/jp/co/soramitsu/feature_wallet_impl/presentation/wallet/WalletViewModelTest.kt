package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.eqNonNull
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
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class WalletViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var ethInteractor: EthereumInteractor
    @Mock private lateinit var interactor: WalletInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var transactionMappers: TransactionMappers
    @Mock private lateinit var pushHandler: PushHandler
    @Mock private lateinit var clipboardManager: ClipboardManager

    private lateinit var walletViewModel: WalletViewModel

    private val assetXor = Asset(AssetHolder.SORA_XOR.id, AssetHolder.SORA_XOR.assetFirstName, AssetHolder.SORA_XOR.assetLastName,
        true, false, 1, Asset.State.NORMAL, 2, AssetBalance(AssetHolder.SORA_XOR.id, BigDecimal.TEN))

    private val assetXorErc = Asset(AssetHolder.SORA_XOR_ERC_20.id, AssetHolder.SORA_XOR_ERC_20.assetFirstName, AssetHolder.SORA_XOR_ERC_20.assetLastName,
        true, false, 2, Asset.State.NORMAL, 2, AssetBalance(AssetHolder.SORA_XOR_ERC_20.id, BigDecimal.TEN))

    private val assetEth = Asset(AssetHolder.ETHER_ETH.id, AssetHolder.ETHER_ETH.assetFirstName, AssetHolder.ETHER_ETH.assetLastName,
        false, false, 0, Asset.State.NORMAL, 2, AssetBalance(AssetHolder.ETHER_ETH.id, BigDecimal.TEN))

    private val transactions = mutableListOf(Transaction(
        "",
        "transactionId",
        Transaction.Status.COMMITTED,
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
        BigDecimal(0.5))
    )

    val transactionsWithHeaders = mutableListOf(
        EventHeader("today"),
        SoraTransaction(
            "transactionId",
            true,
            0,
            "PL",
            "peername lastname",
            "peerId",
            "01 Jan 1970 00:00",
            "${Const.SORA_SYMBOL} 10.12"
        )
    )

    private val assets = mutableListOf(assetXor, assetXorErc, assetEth)
    private val ethAddress = "ethAddress"

    @Before
    fun setUp() {
        given(pushHandler.observeNewPushes()).willReturn(Observable.just("push"))
        given(interactor.updateTransactions(50)).willReturn(Single.just(49))
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(transactionMappers.mapTransactionToSoraTransactionWithHeaders(anyNonNull(), anyNonNull(), anyNonNull())).willReturn(transactionsWithHeaders)
        given(interactor.getTransactions()).willReturn(Observable.just(transactions))
        given(ethInteractor.getAddress()).willReturn(Single.just("ethAddress"))
        given(interactor.getAccountId()).willReturn(Single.just(""))
        given(interactor.getAssets()).willReturn(Observable.just(assets))
        given(interactor.updateAssets()).willReturn(Completable.complete())
        walletViewModel = WalletViewModel(ethInteractor, interactor, router, numbersFormatter, resourceManager, clipboardManager, transactionMappers, pushHandler)
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

        verify(router).showContacts()
    }

    @Test
    fun `receive button clicked`() {
        walletViewModel.receiveButtonClicked()

        verify(router).showReceive()
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
                "details",
                "peerId",
                "20"
        )
        walletViewModel.eventClicked(soraTransaction)
        val date = Date(tx.timestampInMillis)

        verify(router).showTransactionDetailsFromList(
            "myAddress",
            tx.peerId!!,
            tx.peerName,
            tx.ethTxHash,
            tx.soranetTxHash,
            tx.amount,
            "Committed",
            tx.assetId!!,
            date,
            tx.type,
            soraTransaction.description,
            tx.ethFee,
            tx.soranetFee,
            tx.amount + tx.ethFee + tx.soranetFee
        )
    }
}