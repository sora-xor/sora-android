package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransactionDetailsTest {

    @Rule @JvmField val rule = InstantTaskExecutorRule()
    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var walletInteractor: WalletInteractor
    @Mock private lateinit var ethereumInteractor: EthereumInteractor
    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var textFormatter: TextFormatter
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter
    @Mock private lateinit var clipboardManager: ClipboardManager

    private val myAccountId = "myAccountId"
    private val peerId = "recipientId"
    private val peerName = "recipientFullName"
    private val soranetTransactionId = "soraTransactionId"
    private val ethTransactionId = "ethTransactionId"
    private val date = 0L
    private val amount = BigDecimal("100")
    private val totalAmount = BigDecimal("100")
    private val transactionFee = BigDecimal.ZERO
    private val minerFee = BigDecimal.ONE
    private val transactionDescription = "description"

    @Before fun setup() {
        given(dateTimeFormatter.formatDate(anyNonNull(), anyString())).willReturn("01 Jan")
        given(dateTimeFormatter.formatTimeWithSeconds(anyNonNull())).willReturn("03:00:00")
        given(resourceManager.getString(R.string.status_success)).willReturn("Committed")
        given(resourceManager.getString(R.string.status_rejected)).willReturn("Rejected")
        given(resourceManager.getString(R.string.status_pending)).willReturn("Pending")
        given(resourceManager.getString(R.string.transaction_send_again)).willReturn("Send again")
        given(resourceManager.getString(R.string.transaction_send_back)).willReturn("Send back")
        given(resourceManager.getString(R.string.transaction_details)).willReturn("Transaction details")
        given(resourceManager.getString(R.string.val_token)).willReturn("VAL")
        given(textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(anyString())).willReturn("MM")
        given(ethereumInteractor.getAddress()).willReturn(Single.just(ethTransactionId))
    }

    @Test fun `show PENDING incoming transaction details opened from list`() {
        val transactionType = Transaction.Type.INCOMING
        val status = "pending"

        given(resourceManager.getString(R.string.transaction_details)).willReturn("Transaction details")
        given(resourceManager.getString(R.string.transaction_send_back)).willReturn("Send back")
        given(numbersFormatter.formatBigDecimal(amount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(totalAmount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(transactionFee)).willReturn("0")


        val transactionDetailsViewModel = TransactionDetailsViewModel(
            walletInteractor,
            ethereumInteractor,
            router,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            AssetHolder.SORA_VAL.id,
            peerId,
            peerName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            transactionDescription,
            clipboardManager
        )

        transactionDetailsViewModel.fromLiveData.observeForever {
            assertEquals(peerId, it)
        }
        assertEquals(peerId, transactionDetailsViewModel.fromLiveData.value)

        transactionDetailsViewModel.toLiveData.observeForever {
            assertEquals(myAccountId, it)
        }
        assertEquals(myAccountId, transactionDetailsViewModel.toLiveData.value)

        transactionDetailsViewModel.btnTitleLiveData.observeForever {
            assertEquals("Send back", it)
        }
        assertEquals("Send back", transactionDetailsViewModel.btnTitleLiveData.value)

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("Transaction details", it)
        }
        assertEquals("Transaction details", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Pending", it)
        }
        assertEquals("Pending", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_pending_grey_18, it)
        }
        assertEquals(R.drawable.ic_pending_grey_18, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.tranasctionFeeLiveData.observeForever {
            assertEquals("0 VAL", it)
        }
        assertEquals("0 VAL", transactionDetailsViewModel.tranasctionFeeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)
    }

    @Test fun `show REJECTED outgoing transaction details`() {
        val transactionType = Transaction.Type.OUTGOING
        val status = "rejected"

        given(numbersFormatter.formatBigDecimal(totalAmount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(transactionFee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            walletInteractor,
            ethereumInteractor,
            router,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            AssetHolder.SORA_VAL.id,
            peerId,
            peerName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            transactionDescription,
            clipboardManager
        )

        transactionDetailsViewModel.fromLiveData.observeForever {
            assertEquals(myAccountId, it)
        }
        assertEquals(myAccountId, transactionDetailsViewModel.fromLiveData.value)

        transactionDetailsViewModel.toLiveData.observeForever {
            assertEquals(peerId, it)
        }
        assertEquals(peerId, transactionDetailsViewModel.toLiveData.value)

        transactionDetailsViewModel.btnTitleLiveData.observeForever {
            assertEquals("Send again", it)
        }
        assertEquals("Send again", transactionDetailsViewModel.btnTitleLiveData.value)

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("Transaction details", it)
        }
        assertEquals("Transaction details", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Rejected", it)
        }
        assertEquals("Rejected", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_error_red_18, it)
        }
        assertEquals(R.drawable.ic_error_red_18, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.tranasctionFeeLiveData.observeForever {
            assertEquals("0 VAL", it)
        }
        assertEquals("0 VAL", transactionDetailsViewModel.tranasctionFeeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)
    }

    @Test fun `show COMMITED withdraw transaction details from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.formatBigDecimal(amount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(totalAmount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(transactionFee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            walletInteractor,
            ethereumInteractor,
            router,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            AssetHolder.SORA_VAL.id,
            peerId,
            peerName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            transactionDescription,
            clipboardManager
        )

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("Transaction details", it)
        }
        assertEquals("Transaction details", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Committed", it)
        }
        assertEquals("Committed", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_success_green_18, it)
        }
        assertEquals(R.drawable.ic_success_green_18, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("100 VAL", it)
        }
        assertEquals("100 VAL", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.tranasctionFeeLiveData.observeForever {
            assertEquals("0 VAL", it)
        }
        assertEquals("0 VAL", transactionDetailsViewModel.tranasctionFeeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)
    }

    @Test fun `click next button calls showTransferAmount if opened from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.formatBigDecimal(amount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(totalAmount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(transactionFee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            walletInteractor,
            ethereumInteractor,
            router,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            AssetHolder.SORA_VAL.id,
            peerId,
            peerName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            transactionDescription,
            clipboardManager
        )

        transactionDetailsViewModel.btnNextClicked()

        verify(router).showValTransferAmount(peerId, peerName, BigDecimal.ZERO)
    }

    @Test fun `backpress calls returnToWalletFragment() from wallet`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.formatBigDecimal(amount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(totalAmount)).willReturn("100")
        given(numbersFormatter.formatBigDecimal(transactionFee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            walletInteractor,
            ethereumInteractor,
            router,
            resourceManager,
            numbersFormatter,
            textFormatter,
            dateTimeFormatter,
            myAccountId,
            AssetHolder.SORA_XOR.id,
            peerId,
            peerName,
            transactionType,
            soranetTransactionId,
            ethTransactionId,
            status,
            date,
            amount,
            totalAmount,
            transactionFee,
            minerFee,
            transactionDescription,
            clipboardManager
        )

        transactionDetailsViewModel.btnBackClicked()

        verify(router).returnToWalletFragment()
    }
}