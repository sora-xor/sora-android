package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.eqNonNull
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
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

    @Mock
    private lateinit var interactor: WalletInteractor
    @Mock
    private lateinit var router: WalletRouter
    @Mock
    private lateinit var numbersFormatter: NumbersFormatter
    @Mock
    private lateinit var resourceManager: ResourceManager
    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter
    @Mock
    private lateinit var pushHandler: PushHandler

    private lateinit var walletViewModel: WalletViewModel
    private val balanceAmount = BigDecimal.TEN
    private val transactions = mutableListOf(Transaction(
        "transactionId",
        Transaction.Status.COMMITTED,
        "assetId",
        "details",
        "peerName",
        1.0,
        1000,
        "peerId",
        "reason",
        Transaction.Type.INCOMING,
        0.5)
    )

    @Before
    fun setUp() {
        given(pushHandler.observeNewPushes()).willReturn(Observable.just("push"))
        given(resourceManager.getString(R.string.common_today)).willReturn("today")
        given(resourceManager.getString(R.string.common_yesterday)).willReturn("yesterday")
        given(resourceManager.getString(R.string.wallet_from)).willReturn("From")
        given(numbersFormatter.formatBigDecimal(balanceAmount)).willReturn("10")
        given(numbersFormatter.format(anyDouble())).willReturn("10.12")
        given(interactor.getBalance(anyBoolean())).willReturn(Single.just(balanceAmount))
        given(interactor.getTransactionHistory(anyBoolean(), anyBoolean())).willReturn(Single.just(transactions))
        given(dateTimeFormatter.date2Day(anyNonNull(), eqNonNull("today"), eqNonNull("yesterday"))).willReturn("01 Jan 1970")

        walletViewModel = WalletViewModel(interactor, router, numbersFormatter, dateTimeFormatter, resourceManager, pushHandler)
    }

    @Test
    fun `init`() {
        val transactionsWithHeaders = mutableListOf(
            EventHeader("01 Jan 1970"),
            SoraTransaction(
                "Committed",
                "transact",
                Date(transactions[0].timestamp * 1000L),
                "peerId",
                "From peerName",
                "peerName",
                1.0,
                "${Const.SORA_SYMBOL} 10.12",
                Transaction.Type.INCOMING,
                "details",
                0.5,
                1.5

            )
        )
        assertEquals(transactionsWithHeaders, walletViewModel.transactionsLiveData.value)
        assertEquals(numbersFormatter.formatBigDecimal(balanceAmount), walletViewModel.balanceLiveData.value)
        assertEquals(Unit, walletViewModel.hideSwipeProgressLiveData.value?.peekContent())
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
        val date = Date()
        val soraTransaction = SoraTransaction(
            "Opened",
            "transactionId",
            date,
            "recipientId",
            "recipientWithPrefix",
            "recipient",
            100.0,
            "100.00",
            Transaction.Type.INCOMING,
            "description",
            1.0,
            101.0
        )
        walletViewModel.eventClicked(soraTransaction)

        verify(router).showTransactionDetailsFromList(
            soraTransaction.recipientId,
            soraTransaction.recipient,
            soraTransaction.transactionId,
            soraTransaction.amount,
            soraTransaction.status,
            soraTransaction.dateTime,
            soraTransaction.type,
            soraTransaction.description,
            soraTransaction.fee,
            soraTransaction.totalAmount
        )
    }
}