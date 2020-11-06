package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var transactionMappers: TransactionMappers

    private val transactions = mutableListOf(Transaction(
        "",
        "transactionId",
        Transaction.Status.COMMITTED,
        "assetId",
        "myAddress",
        "details",
        "peerName lastname",
        BigDecimal.ONE,
        1000,
        "peerId",
        "reason",
        Transaction.Type.INCOMING,
        BigDecimal.ZERO,
        BigDecimal(0.5))
    )

    private val transactionsWithHeaders = mutableListOf(
        EventHeader("today"),
        SoraTransaction(
            "transactionId",
            true,
            R.drawable.ic_val_red_30,
            "PL",
            "peerName lastname",
            "details",
            "01 Jan 1970 00:00",
            "10.12 VAL"
        )
    )

    @Before fun setup() {
        transactionMappers = TransactionMappers(resourceManager, numbersFormatter, dateTimeFormatter)
    }

    @Test fun `map transactions to SoraTransactions with headers`() {
        given(resourceManager.getString(R.string.common_today)).willReturn("today")
        given(resourceManager.getString(R.string.common_yesterday)).willReturn("yesterday")
        given(resourceManager.getString(R.string.val_token)).willReturn("VAL")
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(dateTimeFormatter.formatDate(Date(transactions.first().timestampInMillis), DateTimeFormatter.DD_MMM_YYYY_HH_MM_SS)).willReturn("01 Jan 1970 00:00")
        given(dateTimeFormatter.dateToDayWithoutCurrentYear(Date(transactions.first().timestampInMillis), "today", "yesterday")).willReturn("today")

        val result = transactionMappers.mapTransactionToSoraTransactionWithHeaders(transactions, "myAddress", "")

        assertEquals(transactionsWithHeaders, result)
    }
}