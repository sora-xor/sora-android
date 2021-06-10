/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
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

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter

    @Mock
    private lateinit var assetHolder: AssetHolder

    private lateinit var transactionMappers: TransactionMappers

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
            "peerName lastname",
            BigDecimal.ONE,
            1000000,
            "peerId",
            "reason",
            Transaction.Type.INCOMING,
            BigDecimal.ZERO,
            BigDecimal(0.5)
        )
    )

    private val transactionsWithHeaders = mutableListOf(
        EventHeader("today"),
        SoraTransaction(
            "transactionId",
            true,
            0,
            "peerName lastname",
            "peerId",
            "01 Jan 1970 00:00",
            "10.12 VAL",
            "10.12",
            false,
            null
        )
    )

    @Before
    fun setup() {
        transactionMappers =
            TransactionMappers(resourceManager, numbersFormatter, dateTimeFormatter, assetHolder)
    }

    @Test
    fun `map transactions to SoraTransactions with headers`() {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), anyInt())).willReturn("10.12")
        given(
            dateTimeFormatter.formatDate(
                Date(transactions.first().timestamp),
                DateTimeFormatter.MMMM_YYYY
            )
        ).willReturn("today")
        given(
            dateTimeFormatter.formatDateTime(
                Date(transactions.first().timestamp),
            )
        ).willReturn("01 Jan 1970 00:00")

        val result = transactionMappers.mapTransactionToSoraTransactionWithHeaders(
            transactions,
            listOf(Asset("assetId", "Validator Token", "VAL", true, false, 1, 4, 18, BigDecimal.ZERO)),
            "",
            ""
        )

        assertEquals(transactionsWithHeaders, result)
    }
}