package jp.co.soramitsu.feature_wallet_impl.presentation.details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class TransactionDetailsTest {

    @Rule @JvmField val rule = InstantTaskExecutorRule()

    @Mock private lateinit var router: WalletRouter
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter

    private val recipientId = "recipientId"
    private val recipientFullName = "recipientFullName"
    private val transactionId = "transactionId"
    private val date = 0L
    private val amount = 100.0
    private val totalAmount = 100.0
    private val fee = 0.0
    private val transactionDescription = "description"

    @Before fun setup() {
        given(dateTimeFormatter.formatDate(anyNonNull(), anyString())).willReturn("01 Jan")
        given(dateTimeFormatter.formatTime(anyNonNull())).willReturn("03:00")
        given(resourceManager.getString(R.string.wallet_committed)).willReturn("Committed")
        given(resourceManager.getString(R.string.wallet_rejected)).willReturn("Rejected")
        given(resourceManager.getString(R.string.wallet_pending)).willReturn("Pending")
    }

    @Test fun `show PENDING incoming transaction details opened from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.INCOMING
        val status = "pending"

        given(resourceManager.getString(R.string.wallet_transaction_details)).willReturn("Transaction details")
        given(resourceManager.getString(R.string.wallet_send_back)).willReturn("Send back")
        given(resourceManager.getString(R.string.wallet_sender)).willReturn("Sender")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.recipientLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        assertEquals(recipientFullName, transactionDetailsViewModel.recipientLiveData.value)

        transactionDetailsViewModel.btnTitleLiveData.observeForever {
            assertEquals("Send back", it)
        }
        assertEquals("Send back", transactionDetailsViewModel.btnTitleLiveData.value)

        transactionDetailsViewModel.descriptionLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        assertEquals(recipientFullName, transactionDetailsViewModel.descriptionLiveData.value)

        transactionDetailsViewModel.bottomViewVisibility.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.bottomViewVisibility.value!!)

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("Transaction details", it)
        }
        assertEquals("Transaction details", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.transactionLiveData.observeForever {
            assertEquals(transactionId, it)
        }
        assertEquals(transactionId, transactionDetailsViewModel.transactionLiveData.value)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Pending", it)
        }
        assertEquals("Pending", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_pending, it)
        }
        assertEquals(R.drawable.ic_pending, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountIconResLiveData.observeForever {
            assertEquals(R.drawable.ic_plus, it)
        }
        assertEquals(R.drawable.ic_plus, transactionDetailsViewModel.amountIconResLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.feeLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 0", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 0", transactionDetailsViewModel.feeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)

        transactionDetailsViewModel.recipientTitleLiveData.observeForever {
            assertEquals("Sender", it)
        }
        assertEquals("Sender", transactionDetailsViewModel.recipientTitleLiveData.value)
    }

    @Test fun `show PENDING outgoing transaction details opened from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.OUTGOING
        val status = "pending"

        given(resourceManager.getString(R.string.wallet_recipient)).willReturn("Recipient")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.recipientTitleLiveData.observeForever {
            assertEquals("Recipient", it)
        }
        assertEquals("Recipient", transactionDetailsViewModel.recipientTitleLiveData.value)
    }

    @Test fun `show REJECTED outgoing transaction details`() {
        val isFromList = false
        val transactionType = Transaction.Type.OUTGOING
        val status = "rejected"

        given(resourceManager.getString(R.string.wallet_all_done)).willReturn("All done")
        given(resourceManager.getString(R.string.wallet_done)).willReturn("Done")
        given(resourceManager.getString(R.string.wallet_funds_are_being_sent)).willReturn("Funds are being sent")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.recipientLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        assertEquals(recipientFullName, transactionDetailsViewModel.recipientLiveData.value)

        transactionDetailsViewModel.btnTitleLiveData.observeForever {
            assertEquals("Done", it)
        }
        assertEquals("Done", transactionDetailsViewModel.btnTitleLiveData.value)

        transactionDetailsViewModel.descriptionLiveData.observeForever {
            assertEquals("Funds are being sent", it)
        }
        assertEquals("Funds are being sent", transactionDetailsViewModel.descriptionLiveData.value)

        transactionDetailsViewModel.bottomViewVisibility.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.bottomViewVisibility.value!!)

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("All done", it)
        }
        assertEquals("All done", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertFalse(it)
        }
        assertFalse(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.transactionLiveData.observeForever {
            assertEquals(transactionId, it)
        }
        assertEquals(transactionId, transactionDetailsViewModel.transactionLiveData.value)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Rejected", it)
        }
        assertEquals("Rejected", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_failed, it)
        }
        assertEquals(R.drawable.ic_failed, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountIconResLiveData.observeForever {
            assertEquals(R.drawable.ic_minus, it)
        }
        assertEquals(R.drawable.ic_minus, transactionDetailsViewModel.amountIconResLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.feeLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 0", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 0", transactionDetailsViewModel.feeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)
    }

    @Test fun `show COMMITED withdraw transaction details from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.wallet_transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.recipientLiveData.observeForever {
            assertEquals(recipientFullName, it)
        }
        assertEquals(recipientFullName, transactionDetailsViewModel.recipientLiveData.value)

        transactionDetailsViewModel.btnTitleLiveData.observeForever {
            assertEquals("", it)
        }
        assertEquals("", transactionDetailsViewModel.btnTitleLiveData.value)

        transactionDetailsViewModel.descriptionLiveData.observeForever {
            assertEquals("", it)
        }
        assertEquals("", transactionDetailsViewModel.descriptionLiveData.value)

        transactionDetailsViewModel.bottomViewVisibility.observeForever {
            assertFalse(it)
        }
        assertFalse(transactionDetailsViewModel.bottomViewVisibility.value!!)

        transactionDetailsViewModel.titleLiveData.observeForever {
            assertEquals("Transaction details", it)
        }
        assertEquals("Transaction details", transactionDetailsViewModel.titleLiveData.value)

        transactionDetailsViewModel.homeBtnVisibilityLiveData.observeForever {
            assertTrue(it)
        }
        assertTrue(transactionDetailsViewModel.homeBtnVisibilityLiveData.value!!)

        transactionDetailsViewModel.transactionLiveData.observeForever {
            assertEquals(transactionId, it)
        }
        assertEquals(transactionId, transactionDetailsViewModel.transactionLiveData.value)

        transactionDetailsViewModel.statusLiveData.observeForever {
            assertEquals("Committed", it)
        }
        assertEquals("Committed", transactionDetailsViewModel.statusLiveData.value)

        transactionDetailsViewModel.statusImageLiveData.observeForever {
            assertEquals(R.drawable.ic_success, it)
        }
        assertEquals(R.drawable.ic_success, transactionDetailsViewModel.statusImageLiveData.value)

        transactionDetailsViewModel.amountIconResLiveData.observeForever {
            assertEquals(R.drawable.ic_minus, it)
        }
        assertEquals(R.drawable.ic_minus, transactionDetailsViewModel.amountIconResLiveData.value)

        transactionDetailsViewModel.amountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.amountLiveData.value)

        transactionDetailsViewModel.totalAmountLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 100", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 100", transactionDetailsViewModel.totalAmountLiveData.value)

        transactionDetailsViewModel.feeLiveData.observeForever {
            assertEquals("${Const.SORA_SYMBOL} 0", it)
        }
        assertEquals("${Const.SORA_SYMBOL} 0", transactionDetailsViewModel.feeLiveData.value)

        transactionDetailsViewModel.transactionDescriptionLiveData.observeForever {
            assertEquals(transactionDescription, it)
        }
        assertEquals(transactionDescription, transactionDetailsViewModel.transactionDescriptionLiveData.value)
    }

    @Test fun `click next button calls showTransferAmount if opened from list`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.wallet_transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.btnNextClicked()

        verify(router).showTransferAmount(recipientId, recipientFullName, BigDecimal.ZERO)
    }

    @Test fun `click next button calls returnToWalletFragment() if opened not from list`() {
        val isFromList = false
        val transactionType = Transaction.Type.OUTGOING
        val status = "rejected"

        given(resourceManager.getString(R.string.wallet_all_done)).willReturn("All done")
        given(resourceManager.getString(R.string.wallet_done)).willReturn("Done")
        given(resourceManager.getString(R.string.wallet_funds_are_being_sent)).willReturn("Funds are being sent")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.btnNextClicked()

        verify(router).returnToWalletFragment()
    }

    @Test fun `backpress calls returnToWalletFragment() from wallet`() {
        val isFromList = true
        val transactionType = Transaction.Type.WITHDRAW
        val status = "committed"

        given(resourceManager.getString(R.string.wallet_transaction_details)).willReturn("Transaction details")
        given(numbersFormatter.format(amount)).willReturn("100")
        given(numbersFormatter.format(totalAmount)).willReturn("100")
        given(numbersFormatter.format(fee)).willReturn("0")

        val transactionDetailsViewModel = TransactionDetailsViewModel(
            router,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            transactionDescription
        )

        transactionDetailsViewModel.btnBackClicked()

        verify(router).returnToWalletFragment()
    }
}