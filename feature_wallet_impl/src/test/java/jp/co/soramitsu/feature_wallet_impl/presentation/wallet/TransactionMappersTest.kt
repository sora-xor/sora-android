package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.mapTransactionToSoraTransactionWithHeaders
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.eqNonNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class TransactionMappersTest {

    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter

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

    private val transactionsWithHeaders = mutableListOf(
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

    @Test fun `map transactions to SoraTransactions with headers`() {
        given(resourceManager.getString(R.string.common_today)).willReturn("today")
        given(resourceManager.getString(R.string.common_yesterday)).willReturn("yesterday")
        given(resourceManager.getString(R.string.wallet_from)).willReturn("From")
        given(numbersFormatter.format(anyDouble())).willReturn("10.12")
        given(dateTimeFormatter.date2Day(anyNonNull(), eqNonNull("today"), eqNonNull("yesterday"))).willReturn("01 Jan 1970")

        assertEquals(transactionsWithHeaders, mapTransactionToSoraTransactionWithHeaders(transactions, resourceManager, numbersFormatter, dateTimeFormatter))
    }
}